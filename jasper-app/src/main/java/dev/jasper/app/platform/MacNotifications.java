package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * In-process UserNotifications bridge; macOS derives identity and icon from the main bundle. Each banner's identifier
 * is its notification's id, so sending it again replaces the banner and withdrawing it removes the banner; a click on
 * a banner comes back through {@link #clicked} on a thread macOS owns. Only a packaged {@code Jasper.app} loads the
 * library; other launches send nothing.
 */
final class MacNotifications {
    private static final System.Logger LOG = System.getLogger(MacNotifications.class.getName());
    private static boolean loaded;
    private static volatile Consumer<String> clicks = id -> { };

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

    /** Where a click's notification id goes. Any thread. */
    static void onClick(Consumer<String> sink) { clicks = Objects.requireNonNull(sink, "sink"); }

    /** Shows, or replaces, the banner for {@code id}; outside a packaged app nothing happens. */
    static void send(String id, String title, String detail) {
        if (load()) post(id, title, detail);
    }

    /** Removes the banner for {@code id}, if this process loaded the bridge. */
    static void withdraw(String id) {
        if (isLoaded()) remove(id);
    }

    /** Removes every banner Jasper shows, if this process loaded the bridge. */
    static void withdrawAll() {
        if (isLoaded()) removeAll();
    }

    /** Called by the bridge when the user clicks one of Jasper's banners; a failing sink never reaches native code. */
    static void clicked(String id) {
        try {
            clicks.accept(id);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A notification click could not be handled", failure);
        }
    }

    @SuppressWarnings("restricted") // The packaged launcher explicitly enables native access.
    private static synchronized boolean load() {
        if (!loaded) {
            Path library = libraryPath(System.getProperty("jpackage.app-path"));
            if (library == null) return false;
            System.load(library.toString());
            loaded = true;
        }
        return true;
    }

    private static synchronized boolean isLoaded() { return loaded; }

    // NSStrings are built from Java UTF-16, not JNI's modified UTF-8; emoji and NUL stay data.
    private static native void post(String id, String title, String detail);

    private static native void remove(String id);

    private static native void removeAll();
}
