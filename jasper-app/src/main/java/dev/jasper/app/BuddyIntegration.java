package dev.jasper.app;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.config.BuddyPosition;
import dev.jasper.buddy.view.BuddyCompanion;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/** EDT owner of companion availability, host visibility, position persistence and typing activity. */
final class BuddyIntegration implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(BuddyIntegration.class.getName());
    private static final long POKE_INTERVAL_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
    private final BuddyCompanion companion;
    private final BuddyVisibility visibility;
    private BuddyOptions options;
    private BooleanSupplier show;
    private Predicate<Component> owns;
    private Function<AWTEventListener, Runnable> registerActivity;
    private Runnable removeActivity;
    private boolean unavailable, closed;
    private long lastPokeNanos;

    BuddyIntegration(Path stateFile, Font font, boolean dark, Runnable activateHost,
                     Runnable toggleRequested, Predicate<Component> owns) {
        this(options(stateFile, font, dark, activateHost, toggleRequested), owns);
    }

    private BuddyIntegration(BuddyOptions options, Predicate<Component> owns) {
        this(options, new BuddyCompanion(options), new BuddyVisibility(), null, owns, listener -> {
            Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK);
            return () -> Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        });
    }

    /** JDK boundary callbacks let headless tests exercise the same availability/registration path. */
    BuddyIntegration(BuddyOptions options, BuddyCompanion companion, BuddyVisibility visibility,
                     BooleanSupplier show, Predicate<Component> owns,
                     Function<AWTEventListener, Runnable> registerActivity) {
        this.options = Objects.requireNonNull(options);
        this.companion = Objects.requireNonNull(companion);
        this.visibility = Objects.requireNonNull(visibility);
        this.show = show == null ? companion::show : show;
        this.owns = Objects.requireNonNull(owns);
        this.registerActivity = Objects.requireNonNull(registerActivity);
    }

    /** Reads once during composition; only the drag-end callback writes the strict existing format. */
    static BuddyOptions options(Path stateFile, Font font, boolean dark, Runnable activateHost, Runnable toggleRequested) {
        var builder = BuddyOptions.builder(font).dark(dark).activateHost(activateHost).toggleRequested(toggleRequested);
        if (stateFile != null) {
            try { BuddyStateFile.read(stateFile).ifPresent(p -> builder.initialPosition(new BuddyPosition(p.x, p.y))); }
            catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable buddy state " + stateFile, failure); }
            builder.positionChanged(p -> {
                try { BuddyStateFile.write(stateFile, new java.awt.Point(p.x(), p.y())); }
                catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not save buddy position to " + stateFile, failure); }
            });
        }
        return builder.build();
    }

    BuddyCompanion companion() { return companion; }
    void configured(boolean enabled) { if (closed) return; visibility.configure(enabled); sync(); }
    void toggle() { if (closed) return; visibility.toggle(); sync(); }
    boolean enabled() { return !closed && visibility.enabled(); }
    void window(Object key, boolean showing, boolean iconified) {
        if (closed) return; visibility.window(key, showing, iconified); sync();
    }
    void removeWindow(Object key) { if (closed) return; visibility.remove(key); sync(); }
    void appearance(Font font, boolean dark) {
        if (closed) return;
        options = options.toBuilder().primaryFont(font).dark(dark).build();
        companion.applyOptions(options);
    }
    void greet() { if (!closed) companion.greet(); }
    void activity() {
        if (closed || unavailable) return;
        long now = System.nanoTime();
        if (now - lastPokeNanos < POKE_INTERVAL_NANOS) return;
        lastPokeNanos = now;
        companion.poke();
    }

    private void sync() {
        if (closed || unavailable) return;
        try {
            if (!visibility.shown()) { companion.hide(); return; }
            if (!show.getAsBoolean()) { unavailable = true; return; }
            if (removeActivity == null) {
                lastPokeNanos = System.nanoTime() - POKE_INTERVAL_NANOS;
                removeActivity = registerActivity.apply(event -> {
                    if (!closed && event.getID() == KeyEvent.KEY_PRESSED
                            && event.getSource() instanceof Component source && owns.test(source)) activity();
                });
            }
        } catch (RuntimeException failure) {
            unavailable = true;
            try { companion.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            removeActivity();
            LOG.log(System.Logger.Level.WARNING, "Desk buddy disabled for this session", failure);
        }
    }
    private void removeActivity() {
        if (removeActivity == null) return;
        Runnable remove = removeActivity; removeActivity = null; remove.run();
    }
    @Override public void close() {
        if (closed) return; closed = true;
        try { removeActivity(); }
        finally {
            options = null; show = () -> false; owns = component -> false; registerActivity = null;
            visibility.clear(); companion.close();
        }
    }
}
