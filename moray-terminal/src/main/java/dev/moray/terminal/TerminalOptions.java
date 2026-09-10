package dev.moray.terminal;

import java.util.List;

/** Everything about how a terminal looks and behaves that the app can configure. */
public record TerminalOptions(String fontFamily, float fontSize, List<String> fallbackFonts, boolean ligatures,
                              Palette palette, CursorStyle cursorStyle, boolean cursorBlink,
                              OptionAsMeta optionAsMeta, int scrollback) {

    public TerminalOptions {
        fallbackFonts = List.copyOf(fallbackFonts);
    }

    /** The spec's defaults (spec §7.2), used until plan 4 adds the config file. */
    public static TerminalOptions defaults() {
        return new TerminalOptions("JetBrains Mono", 14f, List.of("Symbols Nerd Font Mono", "Apple Color Emoji"), true,
            Palette.morayDark(), CursorStyle.BLOCK, true, OptionAsMeta.LEFT, 10_000);
    }
}
