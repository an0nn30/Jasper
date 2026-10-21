package dev.jasper.editor.markdown;

import java.util.Locale;

/** The source document's three Markdown presentations. */
public enum MarkdownMode {
    SOURCE, SPLIT, PREVIEW;

    /** The stable settings key. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** Reads an exact, lower-case settings key. */
    public static MarkdownMode parse(String key) {
        for (var mode : values()) if (mode.key().equals(key)) return mode;
        throw new IllegalArgumentException("Invalid Markdown mode: " + key);
    }
}
