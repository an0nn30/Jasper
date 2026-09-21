package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The per-platform autostart entry. Resolution is pure, like {@link AppDirs} and
 * {@link LaunchSettings}, so both platforms' output is asserted on any host; {@link #apply} is the
 * only part that touches the system.
 */
public final class LoginItem {
    private static final System.Logger LOG = System.getLogger(LoginItem.class.getName());
    private static final String LABEL = "dev.jasper.background";
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE = "Jasper";

    private LoginItem() { }

    /**
     * A described change: a file to write ({@code contents} present), a file to delete
     * ({@code contents} null), and commands to run. {@link #NONE} changes nothing.
     */
    record Plan(Path file, String contents, List<List<String>> commands) {
        static final Plan NONE = new Plan(null, null, List.of());

        Plan {
            commands = List.copyOf(commands);
        }
    }

    /**
     * {@code appPath} is jpackage's {@code jpackage.app-path}: both the proof that this is an
     * installed Jasper and the executable the entry points at. A development run has none, so it
     * registers nothing.
     */
    public static Plan plan(String osName, boolean enabled, String appPath, Path home) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(home, "home");
        if (appPath == null || appPath.isBlank()) return Plan.NONE;
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            Path plist = home.resolve("Library/LaunchAgents").resolve(LABEL + ".plist");
            // No launchctl: launchd loads an agent in this directory at the next login, which is
            // exactly and only what residency needs.
            return new Plan(plist, enabled ? plist(appPath) : null, List.of());
        }
        if (os.startsWith("windows")) {
            // The registry value must be the quoted path followed by an unquoted argument --
            // "C:\Program Files\Jasper\Jasper.exe" --background -- so Windows can tell where the
            // path ends despite its spaces. That string itself contains a space (in "Program
            // Files"), so ProcessBuilder will wrap this whole /d argument in a fresh pair of
            // quotes before handing it to reg.exe; escaping the two interior quotes here is what
            // makes that wrapping produce the value above instead of a corrupted one. Checked by
            // hand against Windows's CRT argv-parsing rules -- there is no Windows machine here to
            // run it on, so treat this as reviewed, not proven.
            return new Plan(null, null, List.of(enabled
                ? List.of("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ",
                    "/d", "\\\"" + appPath + "\\\" --background", "/f")
                : List.of("reg", "delete", RUN_KEY, "/v", VALUE, "/f")));
        }
        return Plan.NONE;
    }

    /** Applies a plan, reporting rather than throwing: autostart is never worth failing a launch over. */
    public static void apply(Plan plan) {
        if (plan.file() != null) {
            try {
                if (plan.contents() == null) Files.deleteIfExists(plan.file());
                else {
                    Files.createDirectories(plan.file().getParent());
                    Files.writeString(plan.file(), plan.contents(), StandardCharsets.UTF_8);
                }
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not update the login item " + plan.file(), failure);
            }
        }
        for (List<String> command : plan.commands()) run(command);
    }

    private static void run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.log(System.Logger.Level.WARNING, "Timed out running " + String.join(" ", command));
                return;
            }
            int status = process.exitValue();
            if (status != 0) {
                // "reg delete" exits nonzero when the value is already absent, which is the state
                // we want; every other command exiting nonzero failed to do what it was asked, and
                // that must be loud rather than logged as if it were the same harmless case.
                if (command.contains("delete")) {
                    LOG.log(System.Logger.Level.INFO, String.join(" ", command)
                        + " exited " + status + "; the login item may already be as requested");
                } else {
                    LOG.log(System.Logger.Level.WARNING, String.join(" ", command)
                        + " exited " + status + "; the login item was not updated");
                }
            }
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not run " + String.join(" ", command), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String plist(String appPath) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>%s</string>
                <key>ProgramArguments</key>
                <array>
                    <string>%s</string>
                    <string>--background</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
                <key>KeepAlive</key>
                <false/>
                <key>LimitLoadToSessionType</key>
                <string>Aqua</string>
            </dict>
            </plist>
            """.formatted(LABEL, escape(appPath));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
