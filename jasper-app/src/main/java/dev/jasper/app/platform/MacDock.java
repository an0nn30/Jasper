package dev.jasper.app.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Whether a packaged macOS Jasper shows in the Dock and Cmd+Tab (the application's activation policy), and whether
 * macOS is logging out, restarting or shutting down. Both go through the bridge {@code libjasper-macos.dylib}, which
 * loads on first use. Never throws: without the bridge Jasper simply stays as it is.
 */
public final class MacDock {
    private static final System.Logger LOG = System.getLogger(MacDock.class.getName());
    private static boolean warned;

    private MacDock() { }

    /** True for this process when it is a packaged macOS app, whose bundle carries the bridge. */
    public static boolean available() {
        return available(System.getProperty("os.name"), System.getProperty("jpackage.app-path"));
    }

    /**
     * True for a packaged macOS app whose bundle carries the bridge: development runs, tests, other systems and an
     * image missing the library cannot switch, so none of them ever starts hidden.
     *
     * @param osName the {@code os.name} property
     * @param appPath the {@code jpackage.app-path} property, or null
     * @return whether the bridge exists for such a launch
     */
    public static boolean available(String osName, String appPath) {
        if (osName == null || !osName.toLowerCase(Locale.ROOT).startsWith("mac")) return false;
        Path library = MacNative.libraryPath(appPath);
        return library != null && Files.isRegularFile(library);
    }

    /**
     * Shows Jasper in the Dock and Cmd+Tab and activates it, or hides it from both. Returns once AppKit has applied it.
     * EDT.
     *
     * @param visible true for the regular policy, false for the accessory policy
     */
    public static void show(boolean visible) {
        try {
            if (MacNative.load()) setRegular(visible);
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    /** Loads the bridge and starts watching for a log-out or shutdown; start-up calls it once it has settled. EDT. */
    public static void prepare() {
        try {
            if (MacNative.load()) watchPowerOff();
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    /**
     * Whether macOS has started a log-out, restart or shutdown since {@link #prepare}. Any thread.
     *
     * @return false when the bridge is not loaded
     */
    public static boolean poweringOff() {
        try {
            return MacNative.loaded() && poweringOffNow();
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
            return false;
        }
    }

    private static synchronized void warnOnce(Throwable failure) {
        if (warned) return;
        warned = true;
        LOG.log(System.Logger.Level.WARNING, "The macOS Dock bridge failed; Jasper stays as it is", failure);
    }

    private static native void setRegular(boolean regular);

    private static native void watchPowerOff();

    private static native boolean poweringOffNow();
}
