package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * One choice of Jasper's Split lists (tabs and panes spec 2.10 and 8): what a new pane can hold, with the actions that
 * open it in a new pane to the right of and below the focused pane. {@link Windows#splitChoices()} lists them.
 *
 * @param label what the lists show before "Right" and "Down": {@code Terminal}, or a split item's label
 * @param rightAction the action that opens it to the right: {@code split_right} for the terminal
 * @param downAction the action that opens it below: {@code split_down} for the terminal
 * @since 0.21.0
 */
public record SplitChoice(String label, String rightAction, String downAction) {
    /** The terminal, every list's first choice: Jasper's own Split Right and Split Down. */
    public static final SplitChoice TERMINAL = new SplitChoice("Terminal", "split_right", "split_down");

    /** Requires every value. */
    public SplitChoice {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rightAction, "rightAction");
        Objects.requireNonNull(downAction, "downAction");
    }
}
