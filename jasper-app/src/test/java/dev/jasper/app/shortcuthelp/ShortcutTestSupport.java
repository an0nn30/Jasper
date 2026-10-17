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

    /** The core catalog's rows on macOS (no actions, no plugins), as ShortcutHelpTest builds them. */
    static java.util.List<ShortcutEntry> rows() {
        return ShortcutCatalog.rows(dev.jasper.app.config.KeyBindings.defaults(true), java.util.List.of(), java.util.Map.of(), true);
    }

    static dev.jasper.ui.model.ViewSpec toolsSpec(ShortcutPanel panel) { return panel.toolsSpec(); }

    static dev.jasper.ui.model.ViewSpec statusSpec(ShortcutPanel panel) { return panel.statusSpec(); }

    /** Sends the action {@code id} to the panel, as its tools' buttons do. */
    static void action(ShortcutPanel panel, String id) { panel.event(new dev.jasper.ui.model.UiEvent.Action(id)); }
}
