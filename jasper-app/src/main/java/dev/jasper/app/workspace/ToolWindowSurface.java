package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.theme.SemanticColor;
import javax.swing.JComponent;

/** The app-owned tool-window shell only. Kit views own every descendant's presentation. */
final class ToolWindowSurface {
    private ToolWindowSurface() {}
    static void apply(JComponent root) {
        if (!dev.jasper.ui.swing.KitOwnership.owns(root))
            root.setBackground(ThemeZone.color(root, SemanticColor.SURFACE_PANEL));
    }
}
