package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SlateSteelPaletteTest {
    static String bundledJson() throws Exception {
        try (var in = SlateSteelPaletteTest.class.getResourceAsStream(SlateSteel.RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void bothVariantsCoverEveryRoleByValueOrRule() {
        for (boolean dark : new boolean[] {true, false}) {
            var palette = SlateSteel.bundled().palette(dark);
            for (var role : SemanticColor.values()) assertThat(palette.color(role)).as("%s %s", dark, role.key()).isNotNull();
            assertThat(palette.color(SemanticColor.BORDER_FOCUS)).isEqualTo(palette.color(SemanticColor.ACCENT));
            assertThat(palette.color(SemanticColor.SURFACE_STATUS)).isEqualTo(palette.color(SemanticColor.SURFACE_APP));
            assertThat(palette.color(SemanticColor.BORDER_IDENTITY)).isEqualTo(palette.color(SemanticColor.BORDER_CONTROL));
            assertThat(palette.color(SemanticColor.HUE_BLUE_SURFACE)).isEqualTo(palette.color(SemanticColor.HUE_BLUE));
            assertThat(palette.color(SemanticColor.HUE_NEUTRAL_SURFACE)).isEqualTo(palette.color(SemanticColor.HUE_NEUTRAL));
        }
    }

    @Test void valuesAreTheFilesIncludingTheirAlpha() {
        var dark = SlateSteel.bundled().palette(true); var light = SlateSteel.bundled().palette(false);
        assertThat(dark.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0x1E2127));
        assertThat(light.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0xE4E7EB));
        assertThat(dark.color(SemanticColor.SURFACE_SCRIM)).isEqualTo(new Color(0x0A, 0x0C, 0x10, 0x8C));
        assertThat(light.color(SemanticColor.SURFACE_SCRIM)).isEqualTo(new Color(0x1C, 0x20, 0x27, 0x40));
        assertThat(dark.color(SemanticColor.BORDER_SUBTLE)).isEqualTo(new Color(255, 255, 255, 0x0F));
        assertThat(dark.color(SemanticColor.HOVER_OVERLAY)).isEqualTo(Color.WHITE);
        assertThat(light.color(SemanticColor.HOVER_OVERLAY)).isEqualTo(Color.BLACK);
        assertThat(dark.color(SemanticColor.SURFACE_TABSTRIP)).isEqualTo(new Color(0x1F2228));
        assertThat(light.color(SemanticColor.TEXT_STRONG_SECONDARY)).isEqualTo(new Color(0x383E48));
    }

    @Test void aFileTheEngineCannotUseFailsNamingTheKey() throws Exception {
        String json = bundledJson();
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"surface.app\": \"#1E2127\",", "\"surface.app\": \"#1E2127\", \"surface.nope\": \"#000000\","), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("ui.slate-steel-dark").hasMessageContaining("Unknown colour \"surface.nope\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"surface.panel\": \"#272A31\", ", ""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("Missing colour \"surface.panel\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"#1E2127\"", "\"#1E21\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("surface.app").hasMessageContaining("#RRGGBB");
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"slate-steel-light\": {\n      \"surface.app\"", "\"slate-steel-pale\": {\n      \"surface.app\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("ui");
        assertThatThrownBy(() -> SlateSteel.parse("{ \"ui\": {} }", "t.json")).isInstanceOf(ThemeFileException.class)
            .hasMessageContaining("Missing object \"terminal\"");
    }

    @Test void aMissingOrUnknownSchemeOrEditorEntryIsNamed() throws Exception {
        String json = bundledJson();
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"fg\\.bold\": \"#[0-9A-Fa-f]+\",\\s*", ""), "t.json"))
            .hasMessageContaining("terminal.slate-steel-dark: Missing colour \"fg.bold\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"bg\":", "\"bgx\": \"#000000\", \"bg\":"), "t.json"))
            .hasMessageContaining("terminal.slate-steel-dark: Unknown colour \"bgx\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"caret\": \"[^\"]+\",\\s*", ""), "t.json"))
            .hasMessageContaining("editor.slate-steel-dark: Missing entry \"caret\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"editor\":", "\"extra\": {}, \"editor\":"), "t.json"))
            .hasMessageContaining("Unknown object \"extra\"");
    }
}
