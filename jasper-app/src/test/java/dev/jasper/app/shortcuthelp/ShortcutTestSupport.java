package dev.jasper.app.shortcuthelp;

import javax.swing.KeyStroke;

/** Owner-package access to the shortcut editor for tests and previews. */
public final class ShortcutTestSupport {
    private ShortcutTestSupport() { }

    /** Searches for {@code query}, edits the first row found, records {@code shortcut} and clicks Set. */
    public static void set(KeymapPage page, String query, KeyStroke shortcut) { set(page.panel(), query, shortcut); }

    public static boolean editing(KeymapPage page) { return page.panel().editor.isVisible(); }

    private static void set(ShortcutPanel panel, String query, KeyStroke shortcut) {
        ShortcutKit.search(panel).setText(query);
        if (panel.getWidth() == 0) panel.setSize(900, 600);
        ShortcutKit.layout(panel);
        ShortcutKit.selectRow(panel, 1);
        ShortcutKit.key(panel, "ENTER");
        if (!panel.capture(shortcut)) throw new AssertionError("not recording");
        ShortcutKit.button(panel, "editor.set").doClick(0);
    }
}
