package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SemanticPaletteTest {



    @Test void authoredSharedTextRemainsRawWhileDedicatedSurfacesApplyTheirFloors() {
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        var light = JasperTheme.bundled().palette(false);
        for (var role : SemanticColor.values()) values.put(role, light.color(role));
        values.putAll(Map.of(SemanticColor.TEXT_PLACEHOLDER, new Color(0xeeeeee), SemanticColor.TEXT_SECONDARY, new Color(0xeeeeee),
            SemanticColor.SURFACE_INPUT, Color.WHITE, SemanticColor.SURFACE_CARD, Color.WHITE));
        var palette = new SemanticPalette(values);
        assertThat(palette.color(SemanticColor.TEXT_PLACEHOLDER)).isEqualTo(new Color(0xeeeeee));
        assertThat(palette.color(SemanticColor.TEXT_SECONDARY)).isEqualTo(new Color(0xeeeeee));
        assertThat(ColorMath.contrast(SurfaceContrast.on(palette, SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_INPUT,
            SurfaceContrast.INDICATOR), Color.WHITE)).isGreaterThanOrEqualTo(3);
        assertThat(ColorMath.contrast(SurfaceContrast.on(palette, SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD,
            SurfaceContrast.TEXT), Color.WHITE)).isGreaterThanOrEqualTo(4.5);
    }

    @Test void incompleteSemanticPaletteIsRejected() {
        assertThatThrownBy(() -> new SemanticPalette(Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void everySemanticNameRoundTrips() {
        assertThat(SemanticColor.values()).hasSize(66);
        assertThat(SemanticColor.of("surface.status")).contains(SemanticColor.SURFACE_STATUS);
        for (var role : SemanticColor.values()) assertThat(SemanticColor.of(role.key())).contains(role);
        assertThat(SemanticColor.of("unknown")).isEmpty();
    }

    @Test void paletteCopiesInputAndRejectsNullColors() {
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, Color.RED);
        var palette = new SemanticPalette(values);
        values.put(SemanticColor.ACCENT, Color.GREEN);
        assertThat(palette.color(SemanticColor.ACCENT)).isEqualTo(Color.RED);
        values.put(SemanticColor.ACCENT, null);
        assertThatThrownBy(() -> new SemanticPalette(values)).isInstanceOf(IllegalArgumentException.class);
    }





    /** The selected tab (the terminal surface) and the strip behind the inactive tabs are distinguishable in both variants. */
    @Test void theSelectedTabAndTheStripDifferInBothVariants() {
        for (boolean dark : new boolean[]{true, false}) {
            var p = JasperTheme.bundled().generated(dark).semantic();
            assertThat(ColorMath.luminance(p.color(SemanticColor.SURFACE_TERMINAL))).as(dark ? "dark" : "light")
                .isNotEqualTo(ColorMath.luminance(p.color(SemanticColor.SURFACE_TABSTRIP)));
        }
    }
}
