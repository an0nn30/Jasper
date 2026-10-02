package dev.jasper.app.terminalthemes;

import dev.jasper.app.appearance.Seed;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeDocument;
import dev.jasper.app.appearance.ThemeGenerator;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Pure transitional conversion of a legacy terminal palette into a native workspace theme. */
public final class AlacrittyConversion {
    private AlacrittyConversion() {}

    /** Preserves every terminal color and derives workspace colors from the palette's background, foreground and blue. */
    public static Theme convert(TerminalTheme theme) {
        var palette = theme.palette();
        String id = "legacy-alacritty:" + theme.file().map(path -> path.toAbsolutePath().normalize().toString()).orElse(theme.name());
        var document = dev.jasper.app.themeimport.AlacrittyConversion.convert(theme.name(), palette, id);
        var generated = ThemeGenerator.generate(document);
        return new Theme(id, generated.name(), generated.dark(), palette, new Theme.LegacyPalette(theme.file(), document));
    }

}
