package dev.jasper.app.platform;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * In-process UserNotifications bridge in {@code libjasper-macos.dylib}; macOS derives identity and icon from the main
 * bundle. Each banner's identifier is its notification's id, so sending it again replaces the banner and withdrawing it
 * removes the banner; a click on a banner comes back through {@link #clicked} on a thread macOS owns. Only a packaged
 * {@code Jasper.app} loads the library; other launches send nothing. The library is shared with the Dock bridge, so the
 * click callback is bound by {@link #bind()} the first time a notification needs the bridge, not when the library loads:
 * a bridge that cannot bind it sends nothing (logged once), and the Dock keeps working.
 */
final class MacNotifications {
    private static final System.Logger LOG = System.getLogger(MacNotifications.class.getName());
    private static volatile Consumer<String> clicks = id -> { };
    /** Null until the first send has loaded the bridge and tried to bind the click callback; then whether it bound. */
    private static Boolean bound;

    private MacNotifications() { }

    /** Where a click's notification id goes. Any thread. */
    static void onClick(Consumer<String> sink) { clicks = Objects.requireNonNull(sink, "sink"); }

    /** Shows, or replaces, the banner for {@code id}; outside a packaged app, or unbound, nothing happens. */
    static void send(String id, String title, String detail) {
        if (ready()) post(id, title, detail);
    }

    /** Removes the banner for {@code id}, if this process bound the bridge. */
    static void withdraw(String id) {
        if (isBound()) remove(id);
    }

    /** Removes every banner Jasper shows, if this process bound the bridge. */
    static void withdrawAll() {
        if (isBound()) removeAll();
    }

    /** Called by the bridge when the user clicks one of Jasper's banners; a failing sink never reaches native code. */
    static void clicked(String id) {
        try {
            clicks.accept(id);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A notification click could not be handled", failure);
        }
    }

    /**
     * Loads the bridge and binds the click callback, once. False outside a packaged app, and for good when the callback
     * cannot be bound: banners whose clicks are dropped without a trace would be worse than none. A library that cannot
     * load throws {@link LinkageError}, and the next send tries again.
     */
    private static synchronized boolean ready() {
        if (bound != null) return bound;
        if (!MacNative.load()) return false;
        bound = bind();
        if (!bound) LOG.log(System.Logger.Level.WARNING,
            "The macOS notification bridge could not bind its click callback; Jasper shows no banners");
        return bound;
    }

    private static synchronized boolean isBound() { return Boolean.TRUE.equals(bound); }

    /**
     * Caches the JVM, this class and {@link #clicked}'s method id for the bridge's click delegate. True once they are
     * resolved, now or by an earlier call; false, with nothing cached, when they cannot be.
     */
    private static native boolean bind();

    // NSStrings are built from Java UTF-16, not JNI's modified UTF-8; emoji and NUL stay data.
    private static native void post(String id, String title, String detail);

    private static native void remove(String id);

    private static native void removeAll();
}
