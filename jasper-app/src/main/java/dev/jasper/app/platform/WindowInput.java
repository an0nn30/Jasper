package dev.jasper.app.platform;

import java.awt.Component;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/** Shared root-pane input marker: app shortcuts defer to the host's active modal overlay. */
public final class WindowInput {
    public static final String OVERLAY = "Jasper.windowOverlay";
    public static final String SHORTCUT_RECORDER = "Jasper.shortcutRecorder";
    /** Typed root property for native menus that bypass Swing key dispatch. */
    public record ShortcutRecorder(java.util.function.Predicate<javax.swing.KeyStroke> capture) { }
    /** Consumes a native accelerator only when its own window is recording shortcuts. */
    public static boolean captureShortcut(Component source, javax.swing.KeyStroke stroke) {
        if (source == null) return false;
        JRootPane root = source instanceof JRootPane pane ? pane : SwingUtilities.getRootPane(source);
        return root != null && root.getClientProperty(SHORTCUT_RECORDER) instanceof ShortcutRecorder recorder
            && recorder.capture().test(stroke);
    }
    private WindowInput() { }
    public static boolean blocked(Component source) {
        JRootPane root = source instanceof JRootPane pane ? pane : SwingUtilities.getRootPane(source);
        return root != null && root.getClientProperty(OVERLAY) != null;
    }
}
