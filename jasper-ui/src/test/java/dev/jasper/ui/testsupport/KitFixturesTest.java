package dev.jasper.ui.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import org.junit.jupiter.api.Test;

class KitFixturesTest {
    @Test void theFixturesAreSlateCobaltAndCustomDiffersInEveryOpaqueRole() {
        assertThat(KitFixtures.dark()).isSameAs(JasperTheme.bundled().generated(true));
        assertThat(KitFixtures.light()).isSameAs(JasperTheme.bundled().generated(false));
        var custom = KitFixtures.custom();
        assertThat(custom.dark()).isTrue();
        for (var role : SemanticColor.values()) {
            var base = KitFixtures.dark().semantic().color(role);
            assertThat(custom.semantic().color(role).getAlpha()).as(role.key()).isEqualTo(base.getAlpha());
            if (base.getAlpha() == 255) assertThat(custom.semantic().color(role)).as(role.key()).isNotEqualTo(base);
        }
    }
}
