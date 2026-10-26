package dev.jasper.ui.theme;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DesktopHaloTest {
    @Test void authoredThemesKeepExactOriginalAlphaRounding() {
        for (var authored : JasperTheme.builtIns()) for (boolean dark : new boolean[] {false, true}) {
            if (!authored.has(dark)) continue;
            var theme = authored.generated(dark);
            for (var kit : new Kit[] {Kit.DEFAULT, Kit.CLASSIC}) for (boolean error : new boolean[] {false, true}) {
                double share = share(kit, dark, error);
                assertThat(DerivedPalette.halo(theme, error, share)).isEqualTo(ColorMath.opacity(theme.semantic().color(
                    error ? SemanticColor.BORDER_ERROR : SemanticColor.BORDER_FOCUS), share));
            }
        }
    }

    private static double share(Kit kit, boolean dark, boolean error) {
        return kit.metrics().value(error ? DesignMetrics.Role.ERROR_HALO_PERCENT : dark
            ? DesignMetrics.Role.FOCUS_HALO_PERCENT_DARK : DesignMetrics.Role.FOCUS_HALO_PERCENT_LIGHT) / 100.0;
    }
}
