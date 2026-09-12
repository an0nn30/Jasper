package dev.moray.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Controlled Java child: no shell, input interpretation, user configuration or unbounded output. */
public final class BenchmarkFixture {
    static final String START = "moray-benchmark-stream-start";
    static final String END = "moray-benchmark-stream-end";
    private BenchmarkFixture() {}

    static long generate(Path file, long targetBytes) throws IOException {
        if (targetBytes < 1 || targetBytes > 100L * 1024 * 1024) throw new IllegalArgumentException("Invalid payload size");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Random random = new Random(42);
        long written = 0;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
            for (long line = 0; written < targetBytes; line++) {
                byte[] bytes = ("\033[" + (31 + random.nextInt(6)) + "m" + line
                    + " moray seeded terminal workload " + random.nextInt(1000000) + " \u2588\033[0m\n")
                    .getBytes(StandardCharsets.UTF_8);
                out.write(bytes); written += bytes.length;
            }
        }
        return Files.size(file);
    }

    static List<String> command(Path data, Path control, int timeoutSeconds) {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        return List.of(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString(),
            "-cp", String.join(File.pathSeparator, Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().toString()).toList()), BenchmarkFixture.class.getName(),
            "--data", data.toString(), "--control", control.toString(), "--timeout-seconds", Integer.toString(timeoutSeconds));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6 || !args[0].equals("--data") || !args[2].equals("--control") || !args[4].equals("--timeout-seconds"))
            throw new IllegalArgumentException("Expected --data PATH --control PATH --timeout-seconds N");
        Path data = Path.of(args[1]), control = Path.of(args[3]);
        int timeout = Integer.parseInt(args[5]);
        if (timeout < 1 || timeout > 3600 || Files.size(data) > 100L * 1024 * 1024 + 512)
            throw new IllegalArgumentException("Fixture bounds exceeded");
        Files.writeString(control.resolve("pid"), Long.toString(ProcessHandle.current().pid()));
        boolean emitted = false;
        long deadline = System.nanoTime() + timeout * 1_000_000_000L;
        while (!Files.exists(control.resolve("stop"))) {
            if (System.nanoTime() >= deadline) throw new java.util.concurrent.TimeoutException("Fixture deadline");
            if (!emitted && Files.exists(control.resolve("go"))) {
                System.out.print("\033]2;" + START + "\007"); System.out.flush();
                Files.copy(data, System.out); System.out.flush();
                System.out.print("\033]2;" + END + "\007"); System.out.flush();
                emitted = true;
            }
            Thread.sleep(10);
        }
    }
}
