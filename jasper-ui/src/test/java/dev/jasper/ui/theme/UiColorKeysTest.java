package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Review Focus 3: every FlatLaf key is one of the variant's colours, from the role its key names. */
class UiColorKeysTest {
    /**
     * Each key takes the source frozen in {@code ui-color-keys.tsv} (captured when the keys matched the former template),
     * in order. Every role and both derived sources get a colour of their own, so a key moved to another role is caught
     * even where Slate Steel gives the two roles one colour.
     */
    @Test void eachKeyTakesItsFrozenSourceInOrder() throws IOException {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            int i = role.ordinal() + 1;
            colors.put(role, new Color(i * 3, 255 - i * 2, (i * 53) % 256));
        }
        var palette = new SemanticPalette(colors);
        var selection = new Color(0xAA, 0x01, 0xFE);
        var sources = new LinkedHashMap<String, Object>();
        for (var role : SemanticColor.values()) sources.put(role.name(), ColorMath.hex(palette.color(role)));
        sources.put("terminal.selection", ColorMath.hex(selection));
        sources.put("error.tint", ColorMath.hex(UiColorKeys.errorTint(palette)));
        assertThat(new HashSet<>(sources.values())).as("every source has its own colour").hasSize(sources.size());
        var expected = new LinkedHashMap<String, Object>();
        try (var in = UiColorKeysTest.class.getResourceAsStream("ui-color-keys.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                assertThat(sources).as(cells[0]).containsKey(cells[1]);
                expected.put(cells[0], sources.get(cells[1]));
            }
        }
        assertThat(UiColorKeys.flatLaf(palette, selection)).containsExactlyEntriesOf(expected);
    }

    @Test void everyKeyIsARolesColour() {
        for (boolean dark : new boolean[]{true, false}) {
            var p = SlateSteel.bundled().palette(dark);
            var selection = SlateSteel.bundled().terminal(TerminalScheme.matching(dark)).colors().selection();
            var allowed = new HashSet<Object>();
            for (var role : SemanticColor.values()) allowed.add(ColorMath.hex(p.color(role)));
            allowed.add(ColorMath.hex(selection)); allowed.add(ColorMath.hex(UiColorKeys.errorTint(p)));
            for (var entry : UiColorKeys.flatLaf(p, selection).entrySet())
                assertThat(entry.getValue()).as("%s in %s", entry.getKey(), dark ? "dark" : "light").isIn(allowed);
        }
    }

    @Test void textSelectionAndTheSearchTintKeepTheirSources() {
        for (boolean dark : new boolean[]{true, false}) {
            var p = SlateSteel.bundled().palette(dark);
            var selection = SlateSteel.bundled().terminal(TerminalScheme.matching(dark)).colors().selection();
            var keys = UiColorKeys.flatLaf(p, selection);
            assertThat(keys.get("*.selectionBackground")).isEqualTo(ColorMath.hex(selection));
            assertThat(keys.get("SearchField.errorBackground")).isEqualTo(ColorMath.hex(UiColorKeys.errorTint(p)));
            assertThat(keys.get("Panel.background")).isEqualTo(ColorMath.hex(p.color(SemanticColor.SURFACE_PANEL)));
        }
    }
}
