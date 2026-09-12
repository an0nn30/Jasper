package dev.moray.terminal;

import java.util.List;
import java.util.Objects;

/** Everything about how a terminal looks and behaves that the app can configure. */
public record TerminalOptions(String fontFamily, float fontSize, List<String> fallbackFonts, boolean ligatures,
                              Palette palette, CursorStyle cursorStyle, boolean cursorBlink,
                              OptionAsMeta optionAsMeta, int scrollback, boolean copyOnSelect, float lineHeight, BellMode bell) {

    public TerminalOptions(String fontFamily, float fontSize, List<String> fallbackFonts, boolean ligatures,
                           Palette palette, CursorStyle cursorStyle, boolean cursorBlink,
                           OptionAsMeta optionAsMeta, int scrollback, boolean copyOnSelect) {
        this(fontFamily, fontSize, fallbackFonts, ligatures, palette, cursorStyle, cursorBlink,
            optionAsMeta, scrollback, copyOnSelect, 1f, BellMode.VISUAL);
    }

    public TerminalOptions {
        requireFontName(fontFamily);
        if (!Float.isFinite(fontSize) || fontSize < 6f || fontSize > 72f) {
            throw new IllegalArgumentException("fontSize must be finite and within 6–72");
        }
        if (!Float.isFinite(lineHeight) || lineHeight < 1f || lineHeight > 3f) {
            throw new IllegalArgumentException("lineHeight must be finite and within 1–3");
        }
        if (scrollback < 0 || scrollback > 1_000_000) {
            throw new IllegalArgumentException("scrollback must be within 0–1000000");
        }
        fallbackFonts = List.copyOf(fallbackFonts);
        fallbackFonts.forEach(TerminalOptions::requireFontName);
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(cursorStyle, "cursorStyle");
        Objects.requireNonNull(optionAsMeta, "optionAsMeta");
        Objects.requireNonNull(bell, "bell");
    }

    private static void requireFontName(String value) {
        Objects.requireNonNull(value, "font name");
        if (value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("font names must be nonblank and contain no NUL");
        }
    }

    /** Standalone terminal defaults; applications can supply their own saved defaults. */
    public static TerminalOptions defaults() {
        return new TerminalOptions("JetBrains Mono", 14f, List.of("Symbols Nerd Font Mono", "Apple Color Emoji"), true,
            Palette.morayDark(), CursorStyle.BLOCK, true, OptionAsMeta.LEFT, 10_000, false);
    }
}
