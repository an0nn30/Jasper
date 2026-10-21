package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.FontConfig;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.TerminalTypography;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Runtime-owned saved terminal typography; publication and registrations belong to the EDT. */
final class HostedTypography {
    private volatile TerminalTypography snapshot = from(ConfigSnapshot.defaults().font());
    private final List<Registration> registrations = new ArrayList<>();

    static TerminalTypography from(FontConfig font) {
        return new TerminalTypography(font.family(), font.size(), font.fallback(), font.ligatures(), font.lineHeight());
    }

    TerminalTypography current() { return snapshot; }

    void publish(TerminalTypography next) {
        requireUi();
        Objects.requireNonNull(next, "typography");
        if (snapshot.equals(next)) return;
        snapshot = next;
        for (var registration : List.copyOf(registrations))
            if (!registration.closed.get()) registration.handler.accept(next);
    }

    Subscription subscribe(Consumer<TerminalTypography> handler) {
        requireUi();
        var registration = new Registration(Objects.requireNonNull(handler, "handler"));
        registrations.add(registration);
        return registration;
    }

    private static void requireUi() {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Terminal typography publication and registration require the UI thread");
    }

    private final class Registration implements Subscription {
        private final Consumer<TerminalTypography> handler;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Registration(Consumer<TerminalTypography> handler) { this.handler = handler; }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (SwingUtilities.isEventDispatchThread()) registrations.remove(this);
            else SwingUtilities.invokeLater(() -> registrations.remove(this));
        }
    }
}
