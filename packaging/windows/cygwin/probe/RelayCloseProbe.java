import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;

/** Opt-in scratch probe: close while a nonreading child applies input backpressure. */
public final class RelayCloseProbe {
    public static void main(String[] args) throws Exception {
        ProbeSupport.windows("relay close");
        if (args.length != 3) throw new IllegalArgumentException("RelayCloseProbe helper.exe owned-cygwin-bin frontend.exe");
        Path bin = Path.of(args[1]);
        Path scratch = java.nio.file.Files.createTempDirectory("jasper-relay-close-");
        Path helper = java.nio.file.Files.copy(Path.of(args[0]), scratch.resolve("relay.exe"));
        var env = new HashMap<>(System.getenv());
        env.keySet().removeIf(k -> k.equalsIgnoreCase("PATH") || k.equalsIgnoreCase("BASH_ENV") || k.equalsIgnoreCase("ENV"));
        env.put("PATH", bin + ";" + System.getenv("PATH"));
        var command = List.of(args[2], helper.toString(), bin.resolve("bash.exe").toString(), "--noprofile", "--norc", "-c",
            "stty raw -echo; printf '\033]2;backpressure-ready\007'; sleep 60");
        try {
            try (var session = TerminalSession.start(SessionLaunchOptions.builder().command(command).environment(env)
                    .workingDirectory(helper.getParent()).build())) {
                ProbeAssertions.await(() -> session.title().equals("backpressure-ready"), Duration.ofSeconds(15), "Child not ready");
                ProbeAssertions.require(!helpers(helper).isEmpty(),
                    "Cannot observe the detached helper; cleanup check would be inconclusive");
                var started = new java.util.concurrent.CountDownLatch(1);
                Thread.ofVirtual().start(() -> { started.countDown(); session.write("x".repeat(1024 * 1024)); });
                started.await();
                Thread.sleep(250); // Deliberately allow the nonreading child to fill bounded input buffers.
            }
            ProbeAssertions.await(() -> helpers(helper).isEmpty(),
                Duration.ofSeconds(5), "Detached relay survived terminal close under backpressure");
            System.out.println("Relay backpressure close passed");
        } finally {
            for (var process : helpers(helper)) {
                new ProcessBuilder("taskkill.exe", "/PID", Long.toString(process.pid()), "/T", "/F")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            for (int attempt = 0; ; attempt++) {
                try { java.nio.file.Files.deleteIfExists(helper); break; }
                catch (java.nio.file.AccessDeniedException busy) {
                    if (attempt == 49) throw busy;
                    Thread.sleep(100); // Windows can retain the image mapping briefly after process exit.
                }
            }
            java.nio.file.Files.deleteIfExists(scratch);
        }
    }
    private static List<ProcessHandle> helpers(Path helper) {
        return ProcessHandle.allProcesses().filter(p -> p.info().command().map(c -> Path.of(c).toAbsolutePath()
            .equals(helper.toAbsolutePath())).orElse(false)).toList();
    }
}
