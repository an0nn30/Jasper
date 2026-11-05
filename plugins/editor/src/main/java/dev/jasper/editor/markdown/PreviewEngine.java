package dev.jasper.editor.markdown;

import java.util.Locale;

/**
 * What draws the Markdown preview, the {@code markdown_preview} setting: Chromium, through the Browser plugin, in
 * GitHub's look; or Basic, the Editor's own preview, which screen readers can read.
 */
public enum PreviewEngine {
    CHROMIUM, BASIC;

    /** The stable settings key. */
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
