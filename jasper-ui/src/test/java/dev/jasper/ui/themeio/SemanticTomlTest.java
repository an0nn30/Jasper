package dev.jasper.ui.themeio;

import dev.jasper.ui.theme.*;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SemanticTomlTest {
    @Test void canonicalColorsAndUnknownMetadataRoundTripWithoutDerivingAuthoredValues() {
        var first = ThemeToml.parse("""
            name = "Roundtrip"
            provenance = "community"
            [colors]
            selection = "#12345670"
            [colors.normal]
            red = "#ab123478"
            [colors.bright]
            red = "#aabbccdd"
            [tokens]
            accent = "#ff000040"
            [semantic]
            "surface.card" = "#aabbcc80"
            "surface.status" = "#f7f8fa"
            accent = "#00ff0070"
            [syntax]
            keyword = { color = "#12345678", bold = true, italic = true }
            [workspace]
            opacity = 0.62
            blur = true
            [ui]
            "Button.arc" = 90
            "*.background" = "#123456"
            """, "roundtrip.toml");
        String output = ThemeTomlWriter.write(first);
        var second = ThemeToml.parse(output, first.origin());
        assertThat(second).isEqualTo(first);
        assertThat(output).contains("[semantic]", "\"surface.card\" = \"#aabbcc80\"");
        assertThat(second.semanticOverrides()).containsEntry(SemanticColor.ACCENT, new Color(0, 255, 0, 112))
            .containsEntry(SemanticColor.SURFACE_STATUS, new Color(0xf7f8fa));
        assertThat(output).contains("\"surface.status\" = \"#f7f8fa\"");
        assertThat(second.metadata()).containsEntry("provenance", "community");
        assertThat(ThemeTomlWriter.write(ThemeToml.parse("name='Legacy'", "legacy.toml"))).doesNotContain("[semantic]");
    }

    @Test void categorySurfaceBasesRoundTripIndependentlyOfGlyphHues() {
        for(String hue:new String[]{"blue","green","amber","purple","neutral"}) {
            var source=ThemeToml.parse("name='Tiles'\n[semantic]\n\"hue."+hue+".surface\"='#12345680'\n\"hue."+hue+"\"='#abcdef'","tiles.toml");
            var output=ThemeTomlWriter.write(source);assertThat(ThemeToml.parse(output,"tiles.toml")).isEqualTo(source);
            assertThat(source.semanticOverrides()).containsEntry(SemanticColor.valueOf("HUE_"+hue.toUpperCase(java.util.Locale.ROOT)+"_SURFACE"),new Color(0x12,0x34,0x56,0x80));
        }
    }

    @Test void invalidSemanticAndBaseValuesIncludeOriginAndLine() {
        for (String key : new String[] {"surface.unknown", "surface.card"}) {
            String value = key.endsWith("unknown") ? "#000000" : "invalid";
            assertThatThrownBy(() -> ThemeToml.parse("name='Invalid'\n[semantic]\n\"" + key + "\"='" + value + "'", "source.toml"))
                .isInstanceOf(ThemeFileException.class).hasMessageContaining("source.toml:3:");
        }
        assertThatThrownBy(() -> ThemeResolver.resolve(ThemeToml.parse("name='Missing'", "missing.toml")))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("missing.toml:").hasMessageContaining("background");
    }

    @Test void delegatesAndVendorClassesCannotEnterThemeData() {
        for (String input : new String[] {"\"ButtonUI\" = 'anything'", "\"Button.border\" = 'com.formdev.flatlaf.ui.FlatButtonBorder'"})
            assertThatThrownBy(() -> ThemeToml.parse("name='Unsafe'\n[ui]\n" + input, "unsafe.toml"))
                .isInstanceOf(ThemeFileException.class).hasMessageContaining("unsafe.toml:3:");
    }
}
