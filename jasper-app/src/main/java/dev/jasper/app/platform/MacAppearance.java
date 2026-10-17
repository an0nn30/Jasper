package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.function.IntConsumer;
import javax.swing.JRootPane;

/**
 * Dedicated appearance capability: observes the application's macOS appearance and sets a window's. It never changes
 * the packaged-only notification/Dock loader.
 */
public final class MacAppearance {
    private static boolean loaded;
    private MacAppearance() { }

    /**
     * Gives {@code root}'s window the macOS appearance of a dark or light theme (JBR's {@code apple.awt.windowAppearance}),
     * which colours its native title bar and controls. Other systems ignore it. EDT.
     *
     * @param root the window's root pane
     * @param dark whether the window takes the dark appearance
     */
    public static void follow(JRootPane root, boolean dark) {
        root.putClientProperty("apple.awt.windowAppearance", dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
    }
    static Path library(String override, String appPath, Path jar) {
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath();
        Path packaged = MacNative.libraryPath(appPath);
        if (packaged != null) return packaged.resolveSibling("libjasper-appearance.dylib");
        return jar != null && jar.getFileName().toString().endsWith(".jar") ? jar.toAbsolutePath().resolveSibling("libjasper-appearance.dylib") : null;
    }
    @SuppressWarnings("restricted")
    private static synchronized void load() {
        if (loaded) return;
        Path path = library(System.getProperty("jasper.appearanceLibrary"), System.getProperty("jpackage.app-path"),
            CodeLocation.of(MacAppearance.class));
        if (path == null) throw new IllegalStateException("No development or packaged appearance bridge");
        System.load(path.toString()); loaded = true;
    }
    static Runnable open(IntConsumer observed) {
        load();
        long handle = watch(observed);
        if (handle == 0) throw new IllegalStateException("Application appearance could not be observed");
        var live = new java.util.concurrent.atomic.AtomicLong(handle);
        return () -> { long value = live.getAndSet(0); if (value != 0) unwatch(value); };
    }
    private static native long watch(IntConsumer observed);
    private static native void unwatch(long handle);
}
