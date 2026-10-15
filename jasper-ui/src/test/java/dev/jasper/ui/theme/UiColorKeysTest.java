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
    /** ui-color-keys.tsv: each key of UiKeySet.DEFAULT and the source it takes, in FlatLaf's order. */
    static LinkedHashMap<String, String> frozenSources() throws IOException {
        var table = new LinkedHashMap<String, String>();
        try (var in = UiColorKeysTest.class.getResourceAsStream("ui-color-keys.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                table.put(cells[0], cells[1]);
            }
        }
        return table;
    }

    /** The keys ui-color-keys.tsv gives over {@code palette} with {@code selection} as the terminal selection. */
    static LinkedHashMap<String, Object> expected(SemanticPalette palette, Color selection) throws IOException {
        var sources = new LinkedHashMap<String, Object>();
        for (var role : SemanticColor.values()) sources.put(role.name(), ColorMath.hex(palette.color(role)));
        sources.put("terminal.selection", ColorMath.hex(selection));
        sources.put("error.tint", ColorMath.hex(UiColorKeys.errorTint(palette)));
        var expected = new LinkedHashMap<String, Object>();
        for (var entry : frozenSources().entrySet()) {
            assertThat(sources).as(entry.getKey()).containsKey(entry.getValue());
            expected.put(entry.getKey(), sources.get(entry.getValue()));
        }
        return expected;
    }

    /**
     * Each key takes the source frozen in {@code ui-color-keys.tsv} (captured when the keys matched the former template),
     * in order. Every role and both derived sources get a colour of their own, so a key moved to another role is caught
     * even where Slate Cobalt gives the two roles one colour.
     */
    @Test void eachKeyTakesItsFrozenSourceInOrder() throws IOException {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            int i = role.ordinal() + 1;
            colors.put(role, new Color(i * 3, 255 - i * 2, (i * 53) % 256));
        }
        var palette = new SemanticPalette(colors);
        var selection = new Color(0xAA, 0x01, 0xFE);
        var sources = new HashSet<Object>();
        for (var role : SemanticColor.values()) sources.add(ColorMath.hex(palette.color(role)));
        sources.add(ColorMath.hex(selection));
        sources.add(ColorMath.hex(UiColorKeys.errorTint(palette)));
        assertThat(sources).as("every source has its own colour").hasSize(SemanticColor.values().length + 2);
        assertThat(UiColorKeys.flatLaf(theme(palette, selection), UiKeySet.DEFAULT)).containsExactlyEntriesOf(expected(palette, selection));
    }

    /**
     * Enterprise presentation plan 1: computed at install time, every built-in variant's keys are the frozen keys over its
     * own roles and matching terminal selection, as the keys it carried before the seam were.
     */
    @Test void everyBuiltInVariantGetsTheFrozenKeysOverItsOwnRoles() throws IOException {
        int variants = 0;
        for (var theme : JasperTheme.builtIns())
            for (boolean dark : new boolean[] {true, false}) {
                if (!theme.has(dark)) continue;
                var generated = theme.generated(dark);
                var keys = UiColorKeys.flatLaf(generated, UiKeySet.DEFAULT);
                String name = theme.id() + (dark ? " dark" : " light");
                assertThat(keys).as(name).containsExactlyEntriesOf(expected(generated.semantic(), theme.matching(dark).colors().selection()));
                variants++;
            }
        assertThat(variants).as("Slate Cobalt's two variants and the four Catppuccin flavours").isEqualTo(6);
    }

    /** A theme's keys are its own roles': a copy with another palette installs that palette's colours. */
    @Test void aRecolouredCopyGetsTheKeysOfItsOwnRoles() {
        var base = JasperTheme.bundled().generated(true);
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, base.semantic().color(role));
        colors.put(SemanticColor.SURFACE_APP, new Color(0x12, 0x34, 0x56));
        var copy = dev.jasper.ui.testsupport.KitFixtures.withSemantic(base, new SemanticPalette(colors));
        assertThat(UiColorKeys.flatLaf(copy, UiKeySet.DEFAULT).get("SearchEverywhere.Header.background")).isEqualTo("#123456");
        assertThat(UiColorKeys.flatLaf(base, UiKeySet.DEFAULT).get("SearchEverywhere.Header.background"))
            .isEqualTo(ColorMath.hex(base.semantic().color(SemanticColor.SURFACE_APP)));
    }

    /** Slate Cobalt Dark with {@code palette} and {@code selection} as its terminal selection. */
    private static GeneratedTheme theme(SemanticPalette palette, Color selection) {
        var base = JasperTheme.bundled().generated(true);
        var t = base.terminal();
        return new GeneratedTheme(base.name(), base.dark(), base.author(), palette,
            new TerminalColors(t.background(), t.foreground(), t.cursor(), selection, t.ansi()), base.editor(), base.syntax(), base.glass());
    }

    @Test void everyKeyIsARolesColour() {
        for (boolean dark : new boolean[]{true, false}) {
            var p = JasperTheme.bundled().palette(dark);
            var selection = JasperTheme.bundled().matching(dark).colors().selection();
            var allowed = new HashSet<Object>();
            for (var role : SemanticColor.values()) allowed.add(ColorMath.hex(p.color(role)));
            allowed.add(ColorMath.hex(selection)); allowed.add(ColorMath.hex(UiColorKeys.errorTint(p)));
            for (var entry : UiColorKeys.flatLaf(JasperTheme.bundled().generated(dark), UiKeySet.DEFAULT).entrySet())
                assertThat(entry.getValue()).as("%s in %s", entry.getKey(), dark ? "dark" : "light").isIn(allowed);
        }
    }

    @Test void textSelectionAndTheSearchTintKeepTheirSources() {
        for (boolean dark : new boolean[]{true, false}) {
            var p = JasperTheme.bundled().palette(dark);
            var selection = JasperTheme.bundled().matching(dark).colors().selection();
            Map<String, Object> keys = UiColorKeys.flatLaf(JasperTheme.bundled().generated(dark), UiKeySet.DEFAULT);
            assertThat(keys.get("*.selectionBackground")).isEqualTo(ColorMath.hex(selection));
            assertThat(keys.get("SearchField.errorBackground")).isEqualTo(ColorMath.hex(UiColorKeys.errorTint(p)));
            assertThat(keys.get("Panel.background")).isEqualTo(ColorMath.hex(p.color(SemanticColor.SURFACE_PANEL)));
        }
    }
}
