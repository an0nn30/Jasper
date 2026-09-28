package dev.jasper.app.shortcuthelp;

import javax.swing.KeyStroke;

/** Owner-package access to the shortcut editor for tests and previews. */
public final class ShortcutTestSupport {
    private ShortcutTestSupport() { }

    /** Searches for {@code query}, edits the first row found, records {@code shortcut} and clicks Set. */
    public static void set(KeymapPage page, String query, KeyStroke shortcut) { set(page.panel(), query, shortcut); }

    public static boolean editing(KeymapPage page) { return page.panel().editor.isVisible(); }

    private static void set(ShortcutPanel panel, String query, KeyStroke shortcut) {
        panel.search.setText(query);
        panel.tree.setSelectionRow(1);
        panel.tree.getActionMap().get("editShortcut").actionPerformed(null);
        if (!panel.capture(shortcut)) throw new AssertionError("not recording");
        panel.editor.set.doClick();
    }
}
