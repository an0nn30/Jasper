package dev.jasper.editor.session;

import java.util.List;
import java.util.Objects;

/**
 * A question for the user, answered with one of its choices.
 *
 * @param title the dialog title
 * @param message the question
 * @param choices the buttons; the last is what closing the dialog means
 * @param destructive whether the first choice overwrites or discards something, so Enter picks the last
 *     choice (Cancel) rather than the first
 */
public record Question(String title, String message, List<String> choices, boolean destructive) {
    /** Validates and copies. */
    public Question {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A question needs a title");
        Objects.requireNonNull(message, "message");
        choices = List.copyOf(choices);
        if (choices.isEmpty()) throw new IllegalArgumentException("A question needs a choice");
    }

    /**
     * A question whose first choice is the default.
     *
     * @param title the dialog title
     * @param message the question
     * @param choices the buttons, first the default; the last is what closing the dialog means
     */
    public Question(String title, String message, List<String> choices) { this(title, message, choices, false); }

    /** @return the answer when the dialog is closed without a choice */
    public String dismissed() { return choices.getLast(); }
}
