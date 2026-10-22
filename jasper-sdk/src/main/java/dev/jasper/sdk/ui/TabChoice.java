package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * One tab Move Pane to Tab offers an item: another tab of the item's window, numbered as
 * Select Tab numbers the window's strip, from 1, and titled after its active pane's item, as the strip titles it, when
 * the plugin may read that title. {@link ContentTab#tabChoices()} lists them and says when, and
 * {@link ContentTab#moveToTab(int)} takes one's number.
 *
 * @param number the tab's place in its window's strip, from 1
 * @param title the tab's title, past 200 characters its start and an ellipsis within that limit, as Jasper's menus
 *     show it; empty when the plugin may not read it (see {@link ContentTab#tabChoices()})
 * @since 0.21.0
 */
public record TabChoice(int number, String title) {
    /** Requires the title, which may be empty, and a number from 1. */
    public TabChoice {
        Objects.requireNonNull(title, "title");
        if (number < 1) throw new IllegalArgumentException("A tab's number starts at 1: " + number);
    }

    /**
     * What a menu shows for it, as Jasper's own Move Pane to Tab words its entries: the number, a space and the title, as
     * {@code 2 notes.txt}; {@code Tab 2} when the title is empty.
     *
     * @return the entry's text
     */
    public String label() {
        return title.isEmpty() ? "Tab " + number : number + " " + title;
    }
}
