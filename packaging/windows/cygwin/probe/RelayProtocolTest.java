import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Portable checks for the experimental relay; arguments: native relay, POSIX shell. */
public final class RelayProtocolTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("RelayProtocolTest native-relay POSIX-shell");
        try (var child = start(args, "stty -echo; printf ready; read -r text; printf 'received:%s' \"$text\"; stty size; exit 7")) {
            expect(child, "ready");
            frame(child, 2, new byte[]{0,37,0,101});
            frame(child, 1, "\u00e9\u6f22\uD83D\uDE00\n".getBytes(StandardCharsets.UTF_8));
            String output = expect(child, "37 101");
            require(output.contains("received:\u00e9\u6f22\uD83D\uDE00"), "PTY UTF-8 corrupted");
            require(exit(child) == 7, "Exit code not preserved");
        }
        try (var child = start(args, "printf ready; sleep 30")) {
            expect(child, "ready"); child.process.getOutputStream().close();
            require(exit(child) != 0, "Input EOF did not stop the child");
        }
        try (var child = start(args, "trap 'exit 0' HUP; printf ready; while :; do :; done")) {
            expect(child, "ready"); frame(child, 9, new byte[0]);
            require(exit(child) != 0, "Invalid frame did not stop the child");
        }
        try (var child = start(args, "printf ready; sleep 1; printf more; sleep 30")) {
            expect(child, "ready"); child.process.getInputStream().close();
            require(exit(child) != 0, "Output failure did not stop the child");
        }
        try (var child = start(args, "printf ready; sleep 1; exit 0")) {
            expect(child, "ready"); child.process.getOutputStream().write(1); child.process.getOutputStream().flush();
            require(exit(child) != 0, "Partial frame must fail when child exits");
        }
        System.out.println("Relay native protocol checks passed");
    }
    private static Child start(String[] args, String script) throws Exception {
        var builder = new ProcessBuilder(List.of(Path.of(args[0]).toAbsolutePath().toString(), "24", "80", args[1], "-c", script));
        builder.environment().remove("BASH_ENV"); builder.environment().remove("ENV");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return new Child(builder.start());
    }
    private record Child(Process process) implements AutoCloseable {
        @Override public void close() { process.destroyForcibly(); }
    }
    private static int exit(Child child) throws Exception {
        require(child.process.waitFor(4, TimeUnit.SECONDS), "Relay did not exit within cleanup deadline");
        return child.process.exitValue();
    }
    private static void frame(Child child, int type, byte[] data) throws Exception {
        var out = new DataOutputStream(child.process.getOutputStream());
        out.writeByte(type); out.writeInt(data.length); out.write(data); out.flush();
    }
    private static String expect(Child child, String expected) throws Exception {
        var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            var out = new ByteArrayOutputStream();
            try {
                while (out.size() < 65536) {
                    int next = child.process.getInputStream().read();
                    if (next < 0) break;
                    out.write(next);
                    String text = out.toString(StandardCharsets.UTF_8);
                    if (text.contains(expected)) return text;
                }
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            throw new AssertionError("Missing " + expected + ": " + out.toString(StandardCharsets.UTF_8));
        });
        return future.get(4, TimeUnit.SECONDS);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
