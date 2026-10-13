package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SlateSteelGeneratedTest {
    @Test void eachVariantIsACompleteGeneratedTheme() {
        for (boolean dark : new boolean[] {true, false}) {
            var steel = SlateSteel.bundled();
            var theme = steel.generated(dark);
            assertThat(theme).isSameAs(steel.generated(dark));
            assertThat(theme.name()).isEqualTo("Slate Steel");
            assertThat(theme.dark()).isEqualTo(dark);
            assertThat(theme.semantic()).isEqualTo(steel.palette(dark));
            assertThat(theme.terminal()).isEqualTo(steel.terminal(TerminalScheme.matching(dark)).colors());
            assertThat(theme.syntax()).isEqualTo(steel.syntax(dark));
            assertThat(theme.glass()).isEqualTo(GlassLevels.OPAQUE);
            assertThat(theme.editor()).isEqualTo(steel.editor(dark));
            assertThat(theme.uiKeys()).containsKeys("*.background", "Panel.background");
            assertThat(theme.uiKeys().keySet()).as("no Jasper.* aliases: chrome reads Jasper.semantic.*").noneMatch(key -> key.startsWith("Jasper."));
            assertThat(theme.uiKeys().get("Panel.background")).isEqualTo(ColorMath.hex(theme.semantic().color(SemanticColor.SURFACE_PANEL)));
        }
    }
}
