package dev.jasper.app.platform;

import java.util.function.IntSupplier;
import javax.swing.JRootPane;

/**
 * Where a root's classic title chrome ends: the macOS title band's height, or the content pane's top under the
 * Windows classic title bar. In-window dialogs and overlays start below it. A root that publishes nothing has no
 * inset. EDT.
 */
public final class TitleInset {
    private static final String KEY = "Jasper.titleInset";
    /**
     * The root property change fired when the inset may have moved, with the old and new inset as {@code Integer}s.
     * Nothing else announces it: FlatLaf can republish macOS full screen after the last resize.
     */
    public static final String CHANGED = "Jasper.titleInsetChanged";

    private TitleInset() { }

    /**
     * Makes {@code height} the source of {@code root}'s inset and announces the change.
     *
     * @param root the window's root pane
     * @param height the classic title chrome's height in pixels, read each time the inset is asked for
     */
    public static void publish(JRootPane root, IntSupplier height) {
        int before = of(root);
        root.putClientProperty(KEY, java.util.Objects.requireNonNull(height));
        changed(root, before);
    }

    /**
     * Reads {@code root}'s inset.
     *
     * @param root the window's root pane
     * @return the published height, never below 0, or 0 when nothing is published
     */
    public static int of(JRootPane root) {
        return root.getClientProperty(KEY) instanceof IntSupplier height ? Math.max(0, height.getAsInt()) : 0;
    }

    /**
     * Fires {@link #CHANGED} on {@code root} when its inset is no longer {@code before}.
     *
     * @param root the window's root pane
     * @param before the inset before the change
     */
    public static void changed(JRootPane root, int before) {
        root.firePropertyChange(CHANGED, before, of(root));
    }
}
