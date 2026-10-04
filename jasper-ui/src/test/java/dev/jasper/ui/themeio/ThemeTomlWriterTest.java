package dev.jasper.ui.themeio;

import dev.jasper.ui.theme.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeTomlWriterTest {
    @Test void readsStructuredIconsWithoutFlatteningPaletteKeys() {
        var document = ThemeToml.parse("""
            name = "Icons"
            extends = "Jasper Dark"
            [icons]
            ColorPalette = { "Actions.Red" = "#aabbcc", "Actions.Red.Dark" = "#ccbbaa", enabled = false }
            paths = ["/icons/a.svg", "/icons/b.svg"]
            empty = {}
            """, "icons.toml");
        assertThat(document.icons()).containsEntry("ColorPalette", Map.of(
            "Actions.Red", "#aabbcc", "Actions.Red.Dark", "#ccbbaa", "enabled", false));
        assertThat(document.icons()).containsEntry("paths", List.of("/icons/a.svg", "/icons/b.svg"));
        assertThat(document.icons()).containsEntry("empty", Map.of());
    }

    @Test void documentsFreezeNestedValuesBeforePublication() {
        var palette = new LinkedHashMap<String, Object>();
        palette.put("Actions.Red", "#aabbcc");
        var paths = new ArrayList<>(List.of("a.svg"));
        ThemeDocument base = ThemeToml.parse("name='Frozen'\nextends='Jasper Dark'", "frozen.toml");
        var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), base.parentLine(),
            base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), base.tokens(), base.syntax(),
            Map.of("Panel.background", palette), Map.of("ColorPalette", palette, "paths", paths));
        palette.put("Actions.Red", "#ffffff");
        paths.add("b.svg");
        assertThat(document.icons()).containsEntry("ColorPalette", Map.of("Actions.Red", "#aabbcc"));
        assertThat(document.icons()).containsEntry("paths", List.of("a.svg"));
        assertThat(document.ui()).containsEntry("Panel.background", Map.of("Actions.Red", "#aabbcc"));
    }

    @Test void rejectsUnsafeAndExcessivelyNestedIconsWithSourceLine() {
        for (String value : List.of("\"org.example.Untrusted\"", "1979-05-27", "nan", "inf")) {
            assertThatThrownBy(() -> ThemeToml.parse("name='Unsafe'\n[icons]\nvalue=" + value, "unsafe.toml"))
                .isInstanceOf(ThemeFileException.class).hasMessageContaining("unsafe.toml").hasMessageContaining("3");
        }
        String deep = "{ child = ".repeat(34) + "true" + " }".repeat(34);
        assertThatThrownBy(() -> ThemeToml.parse("name='Deep'\n[icons]\nvalue=" + deep, "deep.toml"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("nest");
    }

    @Test void roundTripsCompleteNativePayload() throws Exception {
        String text;
        try (var in = getClass().getResourceAsStream("round-trip.toml")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var before = ThemeToml.parse(text, "before.toml");
        String encoded = ThemeTomlWriter.write(before);
        var after = ThemeToml.parse(encoded, "after.toml");
        assertThat(ThemeTomlWriter.write(after)).isEqualTo(encoded);
        assertThat(after.metadata()).isEqualTo(before.metadata());
        assertThat(after.dark()).contains(false);
        assertThat(after.uiComplete()).contains(false);
        assertThat(after.seeds()).isEqualTo(before.seeds());
        assertThat(after.ansi()).isEqualTo(before.ansi());
        assertThat(after.tokens()).isEqualTo(before.tokens());
        assertThat(after.syntax()).isEqualTo(before.syntax());
        assertThat(after.ui()).isEqualTo(before.ui());
        assertThat(new ArrayList<>(after.ui().keySet())).isEqualTo(new ArrayList<>(before.ui().keySet()));
        assertThat(after.icons()).isEqualTo(before.icons());
        assertThat(encoded).contains("#01020380");
    }

    @Test void retainsInheritanceAbsenceAndUiOrdering() {
        for (String keys : List.of("\"*.background\"='#112233'\n\"Panel.background\"='#445566'",
                "\"Panel.background\"='#445566'\n\"*.background\"='#112233'")) {
            var before = ThemeToml.parse("name='Child'\nextends='Jasper Dark'\n[ui]\n" + keys, "child.toml");
            var after = ThemeToml.parse(ThemeTomlWriter.write(before), "copy.toml");
            assertThat(after.parent()).contains("Jasper Dark");
            assertThat(after.dark()).isEmpty();
            assertThat(after.uiComplete()).isEmpty();
            assertThat(new ArrayList<>(after.ui().keySet())).isEqualTo(new ArrayList<>(before.ui().keySet()));
            assertThat(ThemeResolver.resolve(after, name -> List.of(ThemeToml.parse("""
                    name='Jasper Dark'
                    [colors]
                    background='#000000'
                    foreground='#ffffff'
                    accent='#abcdef'
                    [colors.normal]
                    black='#000000'
                    red='#ff0000'
                    green='#00ff00'
                    yellow='#ffff00'
                    blue='#0000ff'
                    magenta='#ff00ff'
                    cyan='#00ffff'
                    white='#ffffff'
                    """, "base.toml"))).seeds())
                .containsKey(Seed.BACKGROUND);
        }
    }

    @Test void rejectsNonFiniteUiNumbersAndTooManyIconValues() {
        for (String value : List.of("nan", "+inf", "-inf")) {
            assertThatThrownBy(() -> ThemeToml.parse("name='Bad'\n[ui]\nvalue=" + value, "bad.toml"))
                .isInstanceOf(ThemeFileException.class).hasMessageContaining("bad.toml");
        }
        String values = "true,".repeat(100_001);
        assertThatThrownBy(() -> ThemeToml.parse("name='Big'\n[icons]\nvalues=[" + values + "]", "big.toml"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("values");
    }
    @Test void supplementaryUnicodeNamesSurviveExport() {
        String name = "Theme " + new String(Character.toChars(0x1f642));
        var original = ThemeToml.parse("name='" + name + "'\nextends='Jasper Dark'", "unicode.toml");
        assertThat(ThemeToml.parse(ThemeTomlWriter.write(original), "copy.toml").name()).isEqualTo(name);
    }
}
