package dev.jasper.app.notifications;

/** Normalizes notification text: plain, bounded, never split inside a surrogate pair. Pure. */
final class NotificationText {
    static final String ELLIPSIS = "…";

    private NotificationText() {}

    /** One line: breaks and tabs become spaces, other control characters go, runs of spaces collapse, ends trimmed. */
    static String line(String text, int max) {
        var out = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { space = true; continue; }
            if (Character.isISOControl(c)) continue;
            if (space && !out.isEmpty()) out.append(' ');
            space = false;
            out.append(c);
        }
        return cut(out.toString(), max);
    }

    /** Several lines: CR LF and CR become LF, tabs become spaces, other control characters go, ends trimmed. */
    static String block(String text, int max) {
        String lines = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        var out = new StringBuilder(lines.length());
        for (int i = 0; i < lines.length(); i++) {
            char c = lines.charAt(i);
            if (c == '\n' || !Character.isISOControl(c)) out.append(c);
        }
        return cut(out.toString().strip(), max);
    }

    /** {@code text}, or its first characters and an ellipsis in {@code max}; a surrogate pair is kept whole or dropped. */
    static String cut(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + ELLIPSIS;
    }
}
