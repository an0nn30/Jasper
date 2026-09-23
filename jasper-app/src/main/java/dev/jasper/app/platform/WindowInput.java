package dev.jasper.app.platform;

import java.awt.Component;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/** Shared root-pane input marker: app shortcuts defer to the host's active modal overlay. */
public final class WindowInput {
    public static final String OVERLAY = "Jasper.windowOverlay";
    private WindowInput() { }
    public static boolean blocked(Component source) {
        JRootPane root = source instanceof JRootPane pane ? pane : SwingUtilities.getRootPane(source);
        return root != null && root.getClientProperty(OVERLAY) != null;
    }
}
