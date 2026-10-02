package dev.jasper.app.themeimport;
import dev.jasper.app.appearance.*;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.LinkedHashMap;
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
        assertThat(document.tokens()).containsEntry(Token.SURFACE, new Color(0x2b2b2b));
        var ui = new LinkedHashMap<String, Object>();
        ui.put("ColorPalette.blue", "#3592c4");
        ui.putAll(resolved.ui());
        assertThat(document.ui()).as("the named colours lead, as FlatLaf's ColorPalette keys").containsExactlyEntriesOf(ui);
        assertThat(document.icons()).isEqualTo(Map.of("ColorPalette", Map.of("Actions.Red", "#c75450")));
        GeneratedTheme generated = ThemeGenerator.generate(document);
        assertThat(generated.uiKeys()).as("authored keys survive inverse tokens").containsAllEntriesOf(ui);
        assertThat(generated.terminal()).isEqualTo(palette);
    }

    @Test void aNamedAccentColourIsTheAccentAsFlatLafReadsIt() {
        var accent = new ThemeLoader(Map.of("t.json", """
            {"name": "Fixture", "dark": true, "colors": {"pink": "#ff79c6", "accentColor": "pink", "accent": "#bd93f9"},
             "ui": {"Component.focusColor": "#3592c4"}}""")::get, Map.of()).load("t.json");
        assertThat(IntellijConversion.convert(accent, Palette.jasperDark(), "t.json").seeds())
            .as("ColorPalette.accent first").containsEntry(Seed.ACCENT, new Color(0xbd93f9));
        var accentColor = new ThemeLoader(Map.of("t.json", """
            {"name": "Fixture", "dark": true, "colors": {"pink": "#ff79c6", "accentColor": "pink"},
             "ui": {"Component.focusColor": "#3592c4"}}""")::get, Map.of()).load("t.json");
        ThemeDocument document = IntellijConversion.convert(accentColor, Palette.jasperDark(), "t.json");
        assertThat(document.seeds()).as("then Component.focusColor (accentColor remains authored UI)").containsEntry(Seed.ACCENT, new Color(0x3592c4));
        assertThat(document.ui()).containsEntry("ColorPalette.accentColor", "#ff79c6").containsEntry("ColorPalette.pink", "#ff79c6");
    }

    @Test void withoutAnAccentKeyTheAccentIsAnsiBlue() {
        var resolved = new ThemeLoader(Map.of("t.json", "{\"name\": \"Plain\", \"ui\": {}}")::get, Map.of()).load("t.json");
        ThemeDocument document = IntellijConversion.convert(resolved, Palette.jasperLight(), "t.json");
        assertThat(document.seeds()).containsEntry(Seed.ACCENT, Palette.jasperLight().ansi().get(4));
        assertThat(document.dark()).contains(false);
    }
}
