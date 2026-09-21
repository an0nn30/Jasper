package dev.jasper.terminal.config;

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
            Palette.jasperDark(), CursorStyle.BLOCK, true, OptionAsMeta.LEFT, 10_000, false);
    }

    /** Starts a builder with the standalone library defaults. */
    public static Builder builder() { return defaults().toBuilder(); }
    /** Copies every option into a new mutable builder. */
    public Builder toBuilder() { return new Builder(this); }
    /** Mutable, thread-confined construction of immutable terminal options. */
    public static final class Builder {
        private String fontFamily;
        private float fontSize;
        private List<String> fallbackFonts;
        private boolean ligatures;
        private Palette palette;
        private CursorStyle cursorStyle;
        private boolean cursorBlink;
        private OptionAsMeta optionAsMeta;
        private int scrollback;
        private boolean copyOnSelect;
        private float lineHeight;
        private BellMode bell;
        private Builder(TerminalOptions source) {
            fontFamily = source.fontFamily();
            fontSize = source.fontSize();
            fallbackFonts = source.fallbackFonts();
            ligatures = source.ligatures();
            palette = source.palette();
            cursorStyle = source.cursorStyle();
            cursorBlink = source.cursorBlink();
            optionAsMeta = source.optionAsMeta();
            scrollback = source.scrollback();
            copyOnSelect = source.copyOnSelect();
            lineHeight = source.lineHeight();
            bell = source.bell();
        }
        /** Sets the primary font family used when options are applied to a view. */
        public Builder fontFamily(String value) { fontFamily = value; return this; }
        /** Sets the font size in points, from 6 through 72, for the view. */
        public Builder fontSize(float value) { fontSize = value; return this; }
        /** Copies the ordered fallback families used for glyphs absent from the primary font. */
        public Builder fallbackFonts(List<String> value) { fallbackFonts = List.copyOf(value); return this; }
        /** Enables font ligatures when these options are applied to the view. */
        public Builder ligatures(boolean value) { ligatures = value; return this; }
        /** Sets the palette used to resolve terminal colors in the view. */
        public Builder palette(Palette value) { palette = value; return this; }
        /** Sets the default cursor shape, which the running application may override. */
        public Builder cursorStyle(CursorStyle value) { cursorStyle = value; return this; }
        /** Sets whether the default cursor blinks. */
        public Builder cursorBlink(boolean value) { cursorBlink = value; return this; }
        /** Selects which Option keys encode the Meta modifier for input. */
        public Builder optionAsMeta(OptionAsMeta value) { optionAsMeta = value; return this; }
        /** Sets the history capacity, from 0 through 1,000,000 lines, for new sessions. */
        public Builder scrollback(int value) { scrollback = value; return this; }
        /** Enables copying a completed selection to the clipboard. */
        public Builder copyOnSelect(boolean value) { copyOnSelect = value; return this; }
        /** Sets the line-height multiplier, from 1 through 3, for the view. */
        public Builder lineHeight(float value) { lineHeight = value; return this; }
        /** Selects how the view signals a terminal bell. */
        public Builder bell(BellMode value) { bell = value; return this; }
        /** Builds and validates an independent immutable value. */
        public TerminalOptions build() {
            return new TerminalOptions(fontFamily, fontSize, fallbackFonts, ligatures, palette, cursorStyle, cursorBlink, optionAsMeta, scrollback, copyOnSelect, lineHeight, bell);
        }
    }
}
