package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * How plugin and app dialogs are placed (spec 9.1 and 10.8).
 *
 * @param kind as each dialog requests, or overlay and attached-sheet dialogs drawn in their window
 * @param windowTitled whether a native title bar already shows a full form's title
 */
public record DialogMapping(Kind kind, boolean windowTitled) {
    /** As requested, or classic. */
    public enum Kind { AS_REQUESTED, CLASSIC }

    /** The kind is required. */
    public DialogMapping { Objects.requireNonNull(kind); }
}
