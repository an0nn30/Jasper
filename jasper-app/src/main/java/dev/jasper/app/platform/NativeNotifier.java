package dev.jasper.app.platform;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

/** A macOS system notification, attributed to the installed Jasper application. */
public final class NativeNotifier implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(NativeNotifier.class.getName());
    private final ExecutorService worker;
    private final boolean supported;
    private final BiConsumer<String, String> delivery;
    private boolean warned;

    public NativeNotifier() {
        this(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"));
    }

    public NativeNotifier(boolean supported) {
        this(supported && MacNative.libraryPath(System.getProperty("jpackage.app-path")) != null,
            MacNotifications::send);
    }

    NativeNotifier(boolean supported, BiConsumer<String, String> delivery) {
        this.supported = supported;
        this.delivery = delivery;
        this.worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-notify").factory());
    }

    /** Shows a system notification in a packaged macOS app; other launches are silent. */
    public void send(String title, String detail) {
        if (!supported) return;
        try {
            worker.execute(() -> {
                try {
                    delivery.accept(title, detail);
                } catch (RuntimeException | LinkageError failure) {
                    // A missing native library must never take down the application or fall back
                    // to osascript: that attributes the notice (and icon) to AppleScript again.
                    if (!warned) {
                        warned = true;
                        LOG.log(System.Logger.Level.WARNING, "Could not show a system notification", failure);
                    }
                }
            });
        } catch (RejectedExecutionException closed) {
            // Shutting down; a missed notification is not worth reporting.
        }
    }

    @Override public void close() { worker.shutdownNow(); }
}
