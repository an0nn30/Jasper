package dev.jasper.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs a real shell on pipes with -i so its integration script loads; stderr (prompts) is merged in order. */
final class ShellRun {
    static final String A = "\033]133;A\007";
    static final String B = "\033]133;B\007";
    static final String C = "\033]133;C\007";
    static String D(int status) { return "\033]133;D;" + status + "\007"; }
    static String CMD(String command) {
        return "\033]1341;jasper;cmd;" + Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8)) + "\007";
    }
    static String CWD(String host, String encodedPath) { return "\033]7;file://" + host + encodedPath + "\007"; }

    private ShellRun() {}

    static String run(List<String> command, Map<String, String> environment, Path directory, String input)
            throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        // Drained on its own thread so the child never blocks on a full pipe and so the timeout
        // below is real: a shell that never exits is destroyed and whatever it printed is kept.
        var output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream out = process.getInputStream()) {
                out.transferTo(output);
            } catch (IOException closedByDestroy) {
                // Keep what was read before the process was destroyed.
            }
        }, "shell-output");
        reader.setDaemon(true);
        reader.start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        reader.join(TimeUnit.SECONDS.toMillis(5));
        return output.toString(StandardCharsets.UTF_8);
    }

    /** What the shell printed, with its own diagnostics kept apart so a test can assert the shell stayed quiet. */
    record Result(String output, String errors) {}

    /** As {@link #run}, but without merging stderr, so warnings from the shell itself are visible on their own. */
    static Result runSeparate(List<String> command, Map<String, String> environment, Path directory, String input)
            throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        Thread out = drain(process.getInputStream(), output, "shell-output");
        Thread err = drain(process.getErrorStream(), errors, "shell-errors");
        try (var stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        out.join(TimeUnit.SECONDS.toMillis(5));
        err.join(TimeUnit.SECONDS.toMillis(5));
        return new Result(output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private static Thread drain(InputStream source, ByteArrayOutputStream sink, String name) {
        Thread reader = new Thread(() -> {
            try (InputStream in = source) {
                in.transferTo(sink);
            } catch (IOException closedByDestroy) {
                // Keep what was read before the process was destroyed.
            }
        }, name);
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    static String hostname() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("hostname").redirectErrorStream(true).start();
        String name = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor();
        return name;
    }
}
