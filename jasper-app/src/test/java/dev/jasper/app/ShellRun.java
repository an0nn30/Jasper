package dev.jasper.app;

import java.io.IOException;
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
        try (var stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        return new String(output, StandardCharsets.UTF_8);
    }

    static String hostname() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("hostname").redirectErrorStream(true).start();
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    }
}
