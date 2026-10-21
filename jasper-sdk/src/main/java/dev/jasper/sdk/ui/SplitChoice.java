package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * One choice of Jasper's Split lists (tabs and panes spec 2.10 and 8): what a new pane can hold, with the actions that
 * open it in a new pane to the right of and below the focused pane. {@link Windows#splitChoices()} lists them; a list
 * a plugin builds shows each choice's {@link #rightLabel()} and {@link #downLabel()}, as Jasper's do.
 *
 * @param label what the lists show before "Right" and "Down": {@code Terminal}, or a split item's label
 * @param rightAction the action that opens it to the right: {@code split_right} for the terminal
 * @param downAction the action that opens it below: {@code split_down} for the terminal
 * @param asks whether its actions ask something before they open the pane ({@link SplitItemSpec#asks()}), so that its
 *             entries end with an ellipsis
 * @since 0.21.0
 */
public record SplitChoice(String label, String rightAction, String downAction, boolean asks) {
    /** The terminal, every list's first choice: Jasper's own Split Right and Split Down. */
    public static final SplitChoice TERMINAL = new SplitChoice("Terminal", "split_right", "split_down", false);

    /** Requires every value. */
    public SplitChoice {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rightAction, "rightAction");
        Objects.requireNonNull(downAction, "downAction");
    }

    /**
     * The entry that opens it to the right, as every Split list words it: the label and "Right", then an ellipsis while
     * its action asks something first, as "Open File Right…" (pane flexibility spec 3).
     *
     * @return the entry's text
     */
    public String rightLabel() {
        return label + " Right" + (asks ? "…" : "");
    }

    /**
     * The entry that opens it below, as every Split list words it: the label and "Down", then an ellipsis while its
     * action asks something first.
     *
     * @return the entry's text
     */
    public String downLabel() {
        return label + " Down" + (asks ? "…" : "");
    }
}
