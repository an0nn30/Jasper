package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/** The desktop's icon theme name: XSETTINGS, then GTK settings files, then gsettings. */
final class IconThemeName {
    private IconThemeName() {}

    /** The live desktop's choice, or null when none is configured. */
    static String find() {
        Map<String, String> env = System.getenv();
        String configHome = env.get("XDG_CONFIG_HOME");
        Path config = configHome == null || configHome.isBlank()
            ? Path.of(System.getProperty("user.home"), ".config") : Path.of(configHome);
        return find(IconThemeName::desktopProperty, config, () -> command(
            List.of("gsettings", "get", "org.gnome.desktop.interface", "icon-theme"), Duration.ofSeconds(2)));
    }

    static String find(Function<String, Object> desktop, Path configHome, Supplier<String> gsettings) {
        if (desktop.apply("gnome.Net/IconThemeName") instanceof String name && valid(name.strip())) return name.strip();
        for (String version : List.of("gtk-3.0", "gtk-4.0")) {
            String name = settingsIni(configHome.resolve(version).resolve("settings.ini"));
            if (valid(name)) return name;
        }
        String name = unquote(gsettings.get());
        return valid(name) ? name : null;
    }

    /** A plain directory name: not blank, no separators, no leading dot, no NUL. */
    static boolean valid(String name) {
        return name != null && !name.isBlank() && !name.startsWith(".")
            && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    /** Standard output of a short command, or null when it is absent, fails or outlives the timeout. */
    static String command(List<String> command, Duration timeout) {
        Process process;
        try { process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
        catch (IOException absent) { return null; }
        // Close stdin so the child never blocks waiting for input it will not receive, and always close
        // stdout too (including on the timeout path below) so a forcibly destroyed process leaks no descriptors.
        try (var stdout = process.getInputStream()) {
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) { process.destroyForcibly(); return null; }
            if (process.exitValue() != 0) return null;
            return new String(stdout.readNBytes(4096), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); process.destroyForcibly(); return null;
        }
    }

    private static String settingsIni(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int equals = line.indexOf('=');
                if (equals > 0 && line.substring(0, equals).strip().equals("gtk-icon-theme-name"))
                    return unquote(line.substring(equals + 1));
            }
        } catch (IOException | RuntimeException unreadable) { return null; }
        return null;
    }

    private static String unquote(String value) {
        if (value == null) return null;
        String text = value.strip();
        if (text.length() >= 2 && (text.charAt(0) == '\'' || text.charAt(0) == '"') && text.charAt(text.length() - 1) == text.charAt(0))
            text = text.substring(1, text.length() - 1);
        return text.strip();
    }

    private static Object desktopProperty(String key) {
        try { return java.awt.Toolkit.getDefaultToolkit().getDesktopProperty(key); }
        catch (RuntimeException | java.awt.AWTError unavailable) { return null; }
    }
}
