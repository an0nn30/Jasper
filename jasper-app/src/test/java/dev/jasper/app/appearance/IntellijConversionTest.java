package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IntellijConversionTest {
    @Test void aResolvedIntellijThemeBecomesACompleteDocumentWithItsPaletteAsSeeds() {
        var resolved = new ThemeLoader(Map.of("t.json", """
            {"name": "Fixture", "dark": true, "author": "Someone", "colors": {"blue": "#3592c4"},
             "ui": {"*": {"background": "#2b2b2b"}, "Panel.background": "#2b2b2b", "Component.focusColor": "blue"},
             "icons": {"ColorPalette": {"Actions.Red": "#c75450"}}}""")::get, Map.of()).load("t.json");
        Palette palette = Palette.jasperDark();
        ThemeDocument document = IntellijConversion.convert(resolved, palette, "fixture.jar!/t.json");
        assertThat(document.origin()).isEqualTo("fixture.jar!/t.json");
        assertThat(document.name()).isEqualTo("Fixture");
        assertThat(document.dark()).contains(true);
        assertThat(document.uiComplete()).contains(true);
        assertThat(document.parent()).isEmpty();
        assertThat(document.seeds()).containsEntry(Seed.BACKGROUND, palette.background())
            .containsEntry(Seed.FOREGROUND, palette.foreground()).containsEntry(Seed.CURSOR, palette.cursor())
            .containsEntry(Seed.SELECTION, palette.selection()).containsEntry(Seed.ACCENT, new Color(0x3592c4));
        for (int i = 0; i < 16; i++) assertThat(document.ansi()).containsEntry(i, palette.ansi().get(i));
        assertThat(document.tokens()).isEmpty();
        assertThat(document.ui()).isEqualTo(resolved.ui());
        assertThat(document.icons()).isEqualTo(Map.of("ColorPalette", Map.of("Actions.Red", "#c75450")));
        GeneratedTheme generated = ThemeGenerator.generate(document);
        assertThat(generated.uiKeys()).as("a complete theme installs its own keys only").isEqualTo(resolved.ui());
        assertThat(generated.terminal()).isEqualTo(palette);
    }

    @Test void withoutAnAccentKeyTheAccentIsAnsiBlue() {
        var resolved = new ThemeLoader(Map.of("t.json", "{\"name\": \"Plain\", \"ui\": {}}")::get, Map.of()).load("t.json");
        ThemeDocument document = IntellijConversion.convert(resolved, Palette.jasperLight(), "t.json");
        assertThat(document.seeds()).containsEntry(Seed.ACCENT, Palette.jasperLight().ansi().get(4));
        assertThat(document.dark()).contains(false);
    }
}
