package dev.jasper.app.commandline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs a shell command as an administrator after macOS asks the user for their password, as VS Code does. */
final class MacAdministrator {
    private MacAdministrator() { }

    /** Blocks until the user answers the system's prompt. */
    static void run(String shellCommand) throws IOException {
        Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script(shellCommand)).redirectErrorStream(true).start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("The administrator prompt did not finish");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while waiting for the administrator prompt", interrupted);
        }
        if (process.exitValue() == 0) return;
        // AppleScript's "User canceled." is error -128.
        if (output.contains("-128")) throw new CommandInstall.Declined();
        throw new IOException(output.isBlank() ? "osascript failed" : output.strip());
    }

    /** {@code do shell script} with administrator privileges, the command as one AppleScript string. */
    static String script(String shellCommand) {
        return "do shell script \"" + shellCommand.replace("\\", "\\\\").replace("\"", "\\\"") + "\" with administrator privileges";
    }
}
