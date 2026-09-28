package dev.jasper.app.shortcuthelp;

import dev.jasper.app.windows.AuxiliarySurface;
import javax.swing.KeyStroke;

/** Owner-package access to the shortcut editor for tests and previews. */
public final class ShortcutTestSupport {
    private ShortcutTestSupport() { }

    /** Searches for {@code query}, edits the first row found, records {@code shortcut} and clicks Set. */
    public static void set(KeymapPage page, String query, KeyStroke shortcut) { set(page.panel(), query, shortcut); }

    public static boolean editing(KeymapPage page) { return page.panel().editor.isVisible(); }

    /** As {@link #set(KeymapPage, String, KeyStroke)}, in the Keyboard Shortcuts window (removed in Task 4). */
    public static void save(AuxiliarySurface surface, String query, KeyStroke shortcut) {
        set((ShortcutPanel) surface.holder().getComponent(0), query, shortcut);
    }

    public static boolean editing(AuxiliarySurface surface) {
        return ((ShortcutPanel) surface.holder().getComponent(0)).editor.isVisible();
    }

    private static void set(ShortcutPanel panel, String query, KeyStroke shortcut) {
        panel.search.setText(query);
        panel.tree.setSelectionRow(1);
        panel.tree.getActionMap().get("editShortcut").actionPerformed(null);
        if (!panel.capture(shortcut)) throw new AssertionError("not recording");
        panel.editor.set.doClick();
    }
}
