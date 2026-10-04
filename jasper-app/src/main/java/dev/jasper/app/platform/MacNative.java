package dev.jasper.app.platform;

import java.nio.file.Path;

/** Jasper's signed in-process macOS bridge, {@code Contents/app/libjasper-macos.dylib}, loaded once on first use. */
final class MacNative {
    static final String LIBRARY = "libjasper-macos.dylib";
    private static boolean loaded;

    private MacNative() { }

    /** The bridge beside the running jpackage launcher; null for any launch that is not a packaged macOS app. */
    static Path libraryPath(String appPath) {
        if (appPath == null || appPath.isBlank()) return null;
        Path executable = Path.of(appPath).toAbsolutePath().normalize();
        Path macOS = executable.getParent();
        if (macOS == null || !macOS.endsWith("MacOS")) return null;
        Path contents = macOS.getParent();
        if (contents == null || !contents.endsWith("Contents")) return null;
        Path bundle = contents.getParent();
        if (bundle == null || bundle.getFileName() == null
                || !bundle.getFileName().toString().endsWith(".app")) return null;
        return contents.resolve("app/" + LIBRARY);
    }

    /**
     * Loads the bridge unless it already is. False, with nothing loaded, outside a packaged macOS app; a library that is
     * present but cannot load throws {@link LinkageError}, and the next call tries again.
     */
    @SuppressWarnings("restricted") // The packaged launcher explicitly enables native access.
    static synchronized boolean load() {
        if (loaded) return true;
        Path library = libraryPath(System.getProperty("jpackage.app-path"));
        if (library == null) return false;
        System.load(library.toString());
        loaded = true;
        return true;
    }

    static synchronized boolean loaded() { return loaded; }
}
