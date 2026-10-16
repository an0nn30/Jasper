package dev.jasper.ui.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.ColorSlots;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.MetricTokens;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.UiKeySet;
import org.junit.jupiter.api.Test;

/** Spec 16.1.3: the Enterprise fixture kit mirrors the shipped kit file; the complement flips every control point. */
class FixtureKitsTest {
    @Test void theEnterpriseFixtureIsTheShippedKitFilesValues() {
        var kit = KitFixtures.enterprise();
        assertThat(kit.id()).isEqualTo("enterprise");
        var tokens = KitFixtures.enterpriseTokens();
        assertThat(java.util.List.of(MetricTokens.Token.values()).stream().map(tokens::value).toList())
            .as("spec 5.1's [metrics]").containsExactly(22, 22, 3, 2, 3, 2, 24, 12, 20, 230, 7);
        assertThat(kit.metrics()).isEqualTo(DesignMetrics.CLASSIC.with(tokens)).isEqualTo(DesignMetrics.CLASSIC);
        assertThat(kit.type()).isEqualTo(TypeScale.CLASSIC.withBody(12));
        assertThat(kit.controls()).isEqualTo(ControlVariants.CLASSIC);
        assertThat(kit.colors()).isEqualTo(new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD));
        assertThat(kit.uiKeys()).isEqualTo(UiKeySet.CLASSIC);
    }

    @Test void theComplementKeepsEnterprisesTokensAndTypeAndFlipsEveryPoint() {
        var complement = KitFixtures.complement();
        assertThat(complement.metrics()).isEqualTo(KitFixtures.enterprise().metrics());
        assertThat(complement.type()).isEqualTo(KitFixtures.enterprise().type());
        assertThat(complement.controls()).isEqualTo(ControlVariants.DEFAULT);
        assertThat(KitFixtures.withControls(Kit.DEFAULT, ControlVariants.CLASSIC).controls()).isEqualTo(ControlVariants.CLASSIC);
        assertThat(KitFixtures.withControls(Kit.DEFAULT, ControlVariants.CLASSIC).metrics()).isSameAs(DesignMetrics.DEFAULT);
    }
}
