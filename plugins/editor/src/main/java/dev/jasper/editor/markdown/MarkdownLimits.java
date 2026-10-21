package dev.jasper.editor.markdown;

import java.util.Objects;

/** Preview work limits, checked before creating any Swing document. */
public final class MarkdownLimits {
    public static final int SOURCE_UNITS = 2_097_152;
    public static final int HTML_BYTES = 16 * 1024 * 1024;
    public static final int BLOCKS = 50_000;

    private MarkdownLimits() { }

    /** Rejects a source snapshot before parsing. Offsets and this bound use UTF-16 units. */
    public static void requireSource(String source) {
        if (Objects.requireNonNull(source).length() > SOURCE_UNITS) throw new PreviewLimitException();
    }

    /** Counts UTF-8 bytes before appending, so a rejected append never grows the builder. */
    static final class Html {
        private final StringBuilder value = new StringBuilder();
        private int bytes;
        private boolean trailingHighSurrogate;

        void append(String text) {
            int count = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (i == 0 && trailingHighSurrogate && Character.isLowSurrogate(c)) count += 3;
                else if (c < 0x80) count++;
                else if (c < 0x800) count += 2;
                else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    count += 4; i++;
                } else count += Character.isSurrogate(c) ? 1 : 3;
                if (count > HTML_BYTES - bytes) throw new PreviewLimitException();
            }
            bytes += count;
            value.append(text);
            if (!text.isEmpty()) trailingHighSurrogate = Character.isHighSurrogate(text.charAt(text.length() - 1));
        }
        @Override public String toString() { return value.toString(); }
    }
}
