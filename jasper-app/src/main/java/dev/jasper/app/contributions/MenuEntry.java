package dev.jasper.app.contributions;

import java.util.List;

/** One immutable node of a contributed menu section. */
public sealed interface MenuEntry permits MenuEntry.Item, MenuEntry.Separator, MenuEntry.Submenu {
    /** A contributed action. */
    record Item(String actionId) implements MenuEntry { }

    /** A separator. */
    record Separator() implements MenuEntry { }

    /** A nested menu. */
    record Submenu(String title, List<MenuEntry> entries) implements MenuEntry {
        public Submenu { entries = List.copyOf(entries); }
    }
}
