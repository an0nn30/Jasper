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
            var keys = UiColorKeys.flatLaf(theme, UiKeySet.DEFAULT);
            assertThat(keys).containsKeys("*.background", "Panel.background");
            assertThat(keys.keySet()).as("no Jasper.* aliases: chrome reads Jasper.semantic.*").noneMatch(key -> key.startsWith("Jasper."));
            assertThat(keys.get("Panel.background")).isEqualTo(ColorMath.hex(theme.semantic().color(SemanticColor.SURFACE_PANEL)));
            assertThat(java.util.Arrays.stream(GeneratedTheme.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                .as("FlatLaf's keys are derived when a theme is installed").doesNotContain("uiKeys");
        }
    }
}
