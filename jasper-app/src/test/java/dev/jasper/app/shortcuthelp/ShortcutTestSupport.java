package dev.jasper.app.shortcuthelp;

import dev.jasper.app.windows.AuxiliarySurface;
import javax.swing.KeyStroke;

/** Owner-package access to the real editor for integration tests. */
public final class ShortcutTestSupport {
    private ShortcutTestSupport() { }
    public static void save(AuxiliarySurface surface, String query, KeyStroke shortcut) {
        var panel = (ShortcutPanel) surface.holder().getComponent(0);
        panel.search.setText(query);
        panel.tree.setSelectionRow(1);
        panel.tree.getActionMap().get("editShortcut").actionPerformed(null);
        if (!panel.capture(shortcut)) throw new AssertionError("not recording");
        panel.editor.save.doClick();
    }
    public static boolean editing(AuxiliarySurface surface) {
        return ((ShortcutPanel) surface.holder().getComponent(0)).editor.isVisible();
    }
}
