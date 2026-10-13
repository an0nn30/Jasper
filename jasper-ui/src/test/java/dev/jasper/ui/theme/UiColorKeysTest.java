package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** Review Focus 3: every FlatLaf key is one of the variant's colours, from the role its key names. */
class UiColorKeysTest {
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
