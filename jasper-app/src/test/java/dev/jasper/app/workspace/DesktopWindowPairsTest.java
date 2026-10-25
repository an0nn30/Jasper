package dev.jasper.app.workspace;

import dev.jasper.app.appearance.DesktopPalette;
import dev.jasper.app.platform.DesktopObservation;
import dev.jasper.ui.theme.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DesktopWindowPairsTest {
    @Test void everyClassicWindowPaintPairMeetsDesktopTargets() {
        for (boolean dark : List.of(false, true)) for (boolean high : List.of(false, true)) {
            var input = new DesktopObservation(new DesktopObservation.Portal(0, DesktopObservation.Polarity.UNKNOWN,
                Optional.empty(), Optional.of(high), ""), Map.of());
            var theme = DesktopPalette.resolve(dark, DesktopPalette.SourceMode.AUTO, "", input).theme();
            for (var pair : WindowPairs.ALL) {
                if (pair.guard().kind() != SurfaceContrast.Guard.Kind.CONTRAST || pair.guard().equals(SurfaceContrast.Guard.STRUCTURAL)) continue;
                double minimum = pair.guard().equals(SurfaceContrast.Guard.TEXT) ? high ? 7 : 4.5 : 3;
                assertThat(ColorMath.contrast(SurfaceContrast.painted(theme, pair), pair.against().color(theme)))
                    .as("dark=%s high=%s %s", dark, high, pair).isGreaterThanOrEqualTo(minimum);
            }
        }
    }
}
