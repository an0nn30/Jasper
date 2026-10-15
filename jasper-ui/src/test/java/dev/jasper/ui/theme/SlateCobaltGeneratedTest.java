package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SlateCobaltGeneratedTest {
    @Test void eachVariantIsACompleteGeneratedTheme() {
        for (boolean dark : new boolean[] {true, false}) {
            var cobalt = JasperTheme.bundled();
            var theme = cobalt.generated(dark);
            assertThat(theme).isSameAs(cobalt.generated(dark));
            assertThat(theme.name()).isEqualTo("Slate Cobalt");
            assertThat(theme.dark()).isEqualTo(dark);
            assertThat(theme.semantic()).isEqualTo(cobalt.palette(dark));
            assertThat(theme.terminal()).isEqualTo(cobalt.matching(dark).colors());
            assertThat(theme.author()).isEqualTo("Jasper");
            assertThat(theme.syntax()).isEqualTo(cobalt.syntax(dark));
            assertThat(theme.glass()).isEqualTo(GlassLevels.OPAQUE);
            assertThat(theme.editor()).isEqualTo(cobalt.editor(dark));
            assertThat(theme.uiKeys()).containsKeys("*.background", "Panel.background");
            assertThat(theme.uiKeys().keySet()).as("no Jasper.* aliases: chrome reads Jasper.semantic.*").noneMatch(key -> key.startsWith("Jasper."));
            assertThat(theme.uiKeys().get("Panel.background")).isEqualTo(ColorMath.hex(theme.semantic().color(SemanticColor.SURFACE_PANEL)));
        }
    }
}
