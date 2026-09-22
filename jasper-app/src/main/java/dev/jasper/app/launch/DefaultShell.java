package dev.jasper.app.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** The command for the user's login shell on this OS. Plan 4 lets the config override it. */
final class DefaultShell {
    private DefaultShell() {
    }

    // Called on the launch worker for every start, before configuration overlays and integration.
    static Map<String, String> loginEnvironment(String osName, Map<String, String> inherited,
                                                Supplier<Optional<String>> accountShell) {
        if (!osName.toLowerCase(Locale.ROOT).startsWith("mac")) return inherited;
        var environment = new HashMap<>(inherited);
        accountShell.get().ifPresent(shell -> environment.put("SHELL", shell));
        return environment;
    }

    static Optional<String> macAccountShell() {
        return readAccountShell(new ProcessBuilder("/usr/bin/dscl", "/Search", "-read",
            "/Users/" + System.getProperty("user.name"), "UserShell"));
    }

    static Optional<String> readAccountShell(ProcessBuilder query) {
        Process process = null;
        try {
            process = query.redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) return Optional.empty();
            String output = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8).strip();
            if (!output.startsWith("UserShell:")) return Optional.empty();
            String shell = output.substring("UserShell:".length()).strip();
            return shell.startsWith("/") && shell.chars().noneMatch(Character::isISOControl)
                ? Optional.of(shell) : Optional.empty();
        } catch (IOException unavailable) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (process != null) {
                process.destroyForcibly();
                try { process.getInputStream().close(); } catch (IOException ignored) { }
            }
        }
    }

    static List<String> command(String osName, Map<String, String> environment) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return List.of("powershell.exe", "-NoLogo");
        }
        String shell = environment.get("SHELL");
        if (shell == null || shell.isBlank()) {
            shell = os.startsWith("mac") ? "/bin/zsh" : "/bin/bash";
        }
        return List.of(shell, "-l");
    }
}
