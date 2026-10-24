package dev.jasper.app.contributions;

import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.TabId;
import java.util.Objects;

/**
 * One of a window's tabs that a pane or a whole tab may move into (pane flexibility spec 4 and 5): Move Pane to Tab and
 * Move Tab Into list them, the tab the pane or the tab is in excepted.
 *
 * @param id the tab's id
 * @param number its place in the window's strip, from 1, as Select Tab numbers it
 * @param title its title, its active pane's item's, as the strip's tab list shows it
 * @param shown the item its active pane shows, which tells whether a plugin may read the title (pane flexibility spec
 *     4, for {@code ContentTab.tabChoices()})
 */
public record OtherTab(ParentTabId id, int number, String title, TabId shown) {
    /** Requires every value, the number from 1. */
    public OtherTab {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(shown, "shown");
        if (number < 1) throw new IllegalArgumentException("A tab's number starts at 1: " + number);
    }

    /**
     * What a menu shows for it: the number, a space and the title, as a window menu numbers its documents (workspace plan
     * 9a Rulings 5).
     *
     * @return the entry's text
     */
    public String label() { return number + " " + title; }
}
