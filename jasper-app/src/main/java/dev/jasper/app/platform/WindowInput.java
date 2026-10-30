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

    /**
     * Whether a window shows a modal ({@code Windows.hasModal}; browser plan 4a): an overlay inside it, or a visible modal
     * dialog it owns, directly or through the windows it owns.
     *
     * @param window a native window, or its root pane; null for none, which shows nothing
     */
    public static boolean showsModal(Component window) {
        return window != null && (blocked(window) || window instanceof java.awt.Window frame && ownsModalDialog(frame));
    }

    private static boolean ownsModalDialog(java.awt.Window window) {
        for (java.awt.Window owned : window.getOwnedWindows())
            if (owned.isVisible() && (owned instanceof java.awt.Dialog dialog && dialog.isModal() || ownsModalDialog(owned))) return true;
        return false;
    }
}
