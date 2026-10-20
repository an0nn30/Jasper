package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What a double-click on the macOS title band does, as the system setting "Double-click a window's title bar to"
 * says. IntelliJ Classic's frame handles the double-click itself in the same way: it reads
 * {@code AppleActionOnDoubleClick} from the global defaults domain and zooms unless the setting says otherwise.
 * Jasper reads it once per process with {@code /usr/bin/defaults}, because jasper-app has no JNA for the
 * native preference call.
 */
public final class MacTitleDoubleClick {
    /** What the double-click does. */
    public enum Action {
        /** Zooms the window, or restores it when it is zoomed: "Maximize", "Fill" or unset. */
        ZOOM,
        /** Minimises the window to the Dock: "Minimize". */
        MINIMIZE,
        /** Does nothing: "None". */
        NONE
    }

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile Action current = Action.ZOOM;

    private MacTitleDoubleClick() { }

    /**
     * The system setting's action.
     *
     * @return the action read by {@link #prefetch()}, or {@link Action#ZOOM} until it has been read
     */
    public static Action current() { return current; }

    /**
     * Reads the system setting on a virtual thread, once per process; later calls do nothing. A failure, a
     * time-out or an unset key keeps {@link Action#ZOOM}.
     */
    public static void prefetch() {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("jasper-title-double-click").start(MacTitleDoubleClick::read);
    }

    private static void read() {
        try {
            Process process = new ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleActionOnDoubleClick")
                .redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null"))).start();
            try (var out = process.getInputStream()) {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return;
                }
                String output = new String(out.readAllBytes(), StandardCharsets.UTF_8);
                // A non-zero exit means the key is unset.
                if (process.exitValue() == 0) current = parse(output);
            }
        } catch (IOException ignored) {
            // No defaults tool: keep zooming.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static Action parse(String value) {
        if (value == null) return Action.ZOOM;
        return switch (value.trim()) {
            case "Minimize" -> Action.MINIMIZE;
            case "None" -> Action.NONE;
            default -> Action.ZOOM;
        };
    }
}
