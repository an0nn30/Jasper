package dev.jasper.sdk.notifications;

import java.util.Objects;

/** The text the host can show: something visible, within a length. Pure. */
final class Texts {
    private Texts() {}

    /**
     * Whether the text shows anything: some character that is not white space, a space separator (no-break spaces
     * included), a formatting mark (zero-width characters, bidi controls) or another control character.
     */
    static boolean visible(String text) {
        return text.codePoints().anyMatch(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)
            && Character.getType(point) != Character.FORMAT && !Character.isISOControl(point));
    }

    /** The text, when it shows something and is at most {@code max} characters long. */
    static String requireVisible(String text, int max, String what) {
        Objects.requireNonNull(text, what);
        if (!visible(text)) throw new IllegalArgumentException("A notification " + what + " must show something");
        if (text.length() > max) throw new IllegalArgumentException("A notification " + what + " has at most " + max + " characters");
        return text;
    }

    /** {@code text}, or its first characters and an ellipsis in {@code max}; a surrogate pair is kept whole or dropped. */
    static String cut(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "…";
    }
}
