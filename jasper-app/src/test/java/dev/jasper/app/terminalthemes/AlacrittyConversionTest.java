package dev.jasper.app.terminalthemes;

import dev.jasper.app.appearance.Seed;
import dev.jasper.app.appearance.Theme;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AlacrittyConversionTest {
    @Test void alacrittyConversionKeepsTerminalColorsAndDerivesTheRest() {
        var palette = dev.jasper.terminal.config.Palette.jasperDark();
        var source = new TerminalTheme("Fixture", palette, java.util.Optional.empty());
        Theme converted = AlacrittyConversion.convert(source);
        assertThat(converted.palette()).isEqualTo(palette);
        assertThat(converted.source().document().uiComplete()).contains(false);
        assertThat(converted.source().document().seeds()).containsKeys(Seed.BACKGROUND, Seed.FOREGROUND, Seed.ACCENT);
    }
    @Test void lowContrastBlueIsAdjustedWithoutChangingTerminalColors() {
        var original = dev.jasper.terminal.config.Palette.jasperLight();
        var ansi = new java.util.ArrayList<>(original.ansi());
        ansi.set(4, original.background());
        var palette = new dev.jasper.terminal.config.Palette(original.foreground(), original.background(),
            original.cursor(), original.selection(), ansi);
        var source = new TerminalTheme("Low contrast", palette, java.util.Optional.of(java.nio.file.Path.of("low.toml")));
        var converted = AlacrittyConversion.convert(source);
        var accent = converted.source().document().seeds().get(Seed.ACCENT);
        assertThat(accent).isNotEqualTo(palette.background());
        assertThat(contrast(accent, palette.background())).isGreaterThanOrEqualTo(3);
        assertThat(converted.palette()).isEqualTo(palette);
        assertThat(converted.id()).startsWith("legacy-alacritty:").isEqualTo(AlacrittyConversion.convert(source).id());
        assertThat(((Theme.LegacyPalette) converted.source()).file()).isEqualTo(source.file());
    }

    @Test void catalogReusesUnchangedConversionsAndPrunesRemovedValues() {
        var catalog = TerminalThemeCatalog.bundledOnly();
        var source = catalog.scan().find("Dracula").orElseThrow();
        var first = catalog.converted(source);
        assertThat(catalog.converted(catalog.scan().find("Dracula").orElseThrow())).isSameAs(first);
        var transientSource = new TerminalTheme("Temporary", source.palette(), java.util.Optional.empty());
        var obsolete = catalog.converted(transientSource);
        catalog.scan();
        assertThat(catalog.converted(transientSource)).isNotSameAs(obsolete);
    }

    private static double contrast(java.awt.Color a, java.awt.Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    private static double luminance(java.awt.Color color) {
        double[] rgb = { color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0 };
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
}

