import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ProbeSupport {
    static void windows(String probe) {
        if (!System.getProperty("os.name").startsWith("Windows"))
            throw new IllegalArgumentException("Cygwin " + probe + " probe requires Windows");
    }
    static int run(List<String> command, Path log, Duration timeout) throws Exception {
        Files.writeString(log.resolveSibling(log.getFileName() + ".command.json"), json(Map.of("argv", command.toString())));
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IOException("Timed out; inspect " + log + "; Windows Installer may require recovery");
        }
        return process.exitValue();
    }
    static void success(List<String> command, Path log) throws Exception {
        int exit = run(command, log, Duration.ofMinutes(10));
        ProbeAssertions.require(exit == 0, "Exit " + exit + "; inspect " + log);
    }
    static String text(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 2 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xfe)
            return new String(bytes, StandardCharsets.UTF_16LE);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    static Map<String, String> environment() {
        var map = new LinkedHashMap<String, String>();
        map.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        map.put("architecture", System.getProperty("os.arch"));
        map.put("java", System.getProperty("java.runtime.version"));
        map.put("vendor", System.getProperty("java.vendor"));
        return map;
    }
    static String json(Map<String, String> values) {
        return values.entrySet().stream().map(e -> "  " + quote(e.getKey()) + ": " + quote(e.getValue()))
            .collect(java.util.stream.Collectors.joining(",\n", "{\n", "\n}\n"));
    }
    static String quote(String value) {
        var out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
