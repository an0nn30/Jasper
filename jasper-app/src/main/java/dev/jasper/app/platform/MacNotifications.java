package dev.jasper.app.platform;

import java.nio.file.Path;

/** In-process UserNotifications bridge; macOS derives identity and icon from the main bundle. */
final class MacNotifications {
    private static boolean loaded;

    private MacNotifications() { }

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
        return contents.resolve("app/libjasper-notifications.dylib");
    }

    @SuppressWarnings("restricted") // The packaged launcher explicitly enables native access.
    static synchronized void send(String title, String detail) {
        if (!loaded) {
            Path library = libraryPath(System.getProperty("jpackage.app-path"));
            if (library == null) return;
            System.load(library.toString());
            loaded = true;
        }
        post(title, detail);
    }

    // NSStrings are built from Java UTF-16, not JNI's modified UTF-8; emoji and NUL stay data.
    private static native void post(String title, String detail);
}
