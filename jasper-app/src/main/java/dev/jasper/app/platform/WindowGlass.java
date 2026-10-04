package dev.jasper.app.platform;

import java.awt.GraphicsEnvironment;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Makes one window translucent behind its workspace and blurs the desktop there (macOS). It reports the opacity
 * actually in effect, which painting must use: 1 whenever the window could not be made translucent, so any failure
 * leaves an ordinary opaque window. It follows the frame's root pane, so a recreated native window is made
 * translucent again, and it watches a translucent window: when something else makes it opaque (a look-and-feel switch
 * resets the frame background a turn later), it reports opaque at once and makes the window translucent again on the
 * next turn. EDT only; the window's owner closes it before disposing the frame.
 */
public final class WindowGlass implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(WindowGlass.class.getName());

    /** The blur behind a translucent window: none, or a material that suits a dark or a light workspace. */
    public enum Blur {
        /** No blur: the desktop shows through as it is. */
        NONE,
        /** A blur material for a dark workspace theme. */
        DARK,
        /** A blur material for a light workspace theme. */
        LIGHT
    }

    /**
     * What a platform can do to one native window, given its root pane.
     *
     * @param supported whether this platform offers translucent windows at all
     * @param translucent makes the window translucent; false when the runtime refuses
     * @param opaque makes a window that {@code translucent} changed opaque again
     * @param blur sets the blur behind the window; false when the native bridge is unavailable
     * @param watch watches a window {@code translucent} changed for something else making it opaque again (a
     *              look-and-feel switch resets the frame background); runs the given action after each such reset
     *              and returns what stops watching
     */
    public record Platform(boolean supported, Predicate<JRootPane> translucent, Consumer<JRootPane> opaque,
                           BiPredicate<JRootPane, Blur> blur, BiFunction<JRootPane, Runnable, Runnable> watch) {
        /** A platform without translucent windows. */
        public static final Platform NONE = new Platform(false, root -> false, root -> {}, (root, blur) -> false,
            (root, reset) -> () -> {});

        public Platform {
            Objects.requireNonNull(translucent); Objects.requireNonNull(opaque); Objects.requireNonNull(blur);
            Objects.requireNonNull(watch);
        }

        /**
         * This runtime's platform: macOS with a display, or {@link #NONE}.
         *
         * @return the platform windows use
         */
        public static Platform current() {
            boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");
            if (!mac || GraphicsEnvironment.isHeadless()) return NONE;
            return new Platform(true, MacGlass::translucent, MacGlass::opaque, MacGlass::blur, MacGlass::watch);
        }
    }

    private final JRootPane root;
    private final Platform platform;
    private final DoubleConsumer onOpacity;
    private final HierarchyListener peerChanged = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) displayabilityChanged();
    };
    private double requestedOpacity = 1, opacity = 1;
    private boolean requestedBlur, requestedDark, translucent, refused, blurWarned, closed, reapplying;
    private Runnable unwatch = () -> {};
    private Blur appliedBlur = Blur.NONE;

    /**
     * Follows {@code root}'s window.
     *
     * @param root the frame's root pane
     * @param platform what the platform can do
     * @param onOpacity receives each change of the opacity in effect
     */
    public WindowGlass(JRootPane root, Platform platform, DoubleConsumer onOpacity) {
        this.root = Objects.requireNonNull(root);
        this.platform = Objects.requireNonNull(platform);
        this.onOpacity = Objects.requireNonNull(onOpacity);
        root.addHierarchyListener(peerChanged);
    }

    /**
     * Asks for these levels; they apply now, or once the window has a native peer.
     *
     * @param opacity 0.1–1, where 1 is opaque
     * @param blur whether to blur the desktop behind a translucent window
     * @param dark whether the workspace theme is dark, which picks the blur material
     */
    public void request(double opacity, boolean blur, boolean dark) {
        requireEdt();
        if (closed) return;
        requestedOpacity = Math.max(0.1, Math.min(1, opacity));
        requestedBlur = blur;
        requestedDark = dark;
        apply();
    }

    /**
     * The opacity in effect: what painting must use.
     *
     * @return 0.1–1, where 1 is opaque
     */
    public double opacity() { return opacity; }

    private void displayabilityChanged() {
        if (closed) return;
        if (!root.isDisplayable()) {
            // The native window is gone; a new one starts opaque and unblurred.
            stopWatching();
            translucent = false;
            appliedBlur = Blur.NONE;
        }
        apply();
    }

    private void apply() {
        boolean wanted = requestedOpacity < 1 && platform.supported() && !refused;
        if (root.isDisplayable()) {
            if (wanted && !translucent) {
                if (platform.translucent().test(root)) {
                    translucent = true;
                    unwatch = platform.watch().apply(root, this::resetElsewhere);
                } else {
                    refused = true;
                    LOG.warning("This runtime cannot make Jasper windows translucent; the workspace stays opaque. "
                        + "Jasper needs --add-opens java.desktop/java.awt and --add-exports java.desktop/java.awt.peer.");
                }
            } else if (!wanted && translucent) {
                stopWatching();
                platform.opaque().accept(root);
                translucent = false;
            }
            Blur blur = translucent && requestedBlur ? (requestedDark ? Blur.DARK : Blur.LIGHT) : Blur.NONE;
            if (blur != appliedBlur) {
                if (platform.blur().test(root, blur)) appliedBlur = blur;
                else if (!blurWarned) {
                    blurWarned = true;
                    LOG.warning("The window blur bridge is unavailable; translucent windows are not blurred.");
                }
            }
        }
        publish();
    }

    private void publish() {
        double next = translucent ? requestedOpacity : 1;
        if (next != opacity) {
            opacity = next;
            onOpacity.accept(next);
        }
    }

    /**
     * Something else made the translucent window opaque. It is opaque now, so painting must be told at once; the
     * window goes translucent again on the next turn, after whatever reset it has finished (AWT makes the peer opaque
     * only after it notifies the change).
     */
    private void resetElsewhere() {
        if (closed || !translucent) return;
        stopWatching();
        translucent = false;
        publish();
        if (reapplying) return;
        reapplying = true;
        SwingUtilities.invokeLater(() -> {
            reapplying = false;
            if (!closed) apply();
        });
    }

    private void stopWatching() {
        Runnable stop = unwatch;
        unwatch = () -> {};
        stop.run();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        stopWatching();
        root.removeHierarchyListener(peerChanged);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Window glass requires the EDT");
    }
}
