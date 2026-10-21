package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * One tab Move Pane to Tab offers an item (pane flexibility spec 4): another tab of the item's window, numbered as
 * Select Tab numbers the window's strip, from 1, and titled after its active pane's item, as the strip titles it.
 * {@link ContentTab#tabChoices()} lists them, and {@link ContentTab#moveToTab(int)} takes one's number.
 *
 * @param number the tab's place in its window's strip, from 1
 * @param title the tab's title, past 200 characters its start and an ellipsis within that limit, as Jasper's menus
 *     show it
 * @since 0.21.0
 */
public record TabChoice(int number, String title) {
    /** Requires the title and a number from 1. */
    public TabChoice {
        Objects.requireNonNull(title, "title");
        if (number < 1) throw new IllegalArgumentException("A tab's number starts at 1: " + number);
    }

    /**
     * What a menu shows for it, as Jasper's own Move Pane to Tab words its entries: the number, a space and the title, as
     * {@code 2 notes.txt}.
     *
     * @return the entry's text
     */
    public String label() {
        return number + " " + title;
    }
}
