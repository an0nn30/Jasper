package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 4 (plan 2's hand-offs): what a guard measures, and the structural guard engaging. */
class GuardSurfacesTest {
    /** Slate Cobalt Light with {@code role} replaced by {@code colour}. */
    private static GeneratedTheme with(SemanticColor role, Color colour) {
        var base = KitFixtures.light();
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var each : SemanticColor.values()) values.put(each, base.semantic().color(each));
        values.put(role, colour);
        return KitFixtures.withSemantic(base, new SemanticPalette(values));
    }

    /** Rulings 2: a near-transparent dark text reads as a pale grey on white, and that is what the guard holds to 4.5:1. */
    @Test void aTranslucentSlotIsMeasuredAsItShowsOverItsSurface() {
        var theme = with(SemanticColor.TEXT_SECONDARY, new Color(0x1E, 0x1F, 0x22, 0x30));
        var pair = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
        Color card = theme.semantic().color(SemanticColor.SURFACE_CARD);
        assertThat(ColorMath.contrast(theme.semantic().color(SemanticColor.TEXT_SECONDARY), card))
            .as("its RGB alone would pass").isGreaterThan(SurfaceContrast.TEXT);
        Color painted = SurfaceContrast.painted(theme, pair);
        assertThat(painted.getAlpha()).as("a repair is opaque").isEqualTo(255);
        assertThat(ColorMath.contrast(painted, card)).isGreaterThanOrEqualTo(SurfaceContrast.TEXT);
        assertThat(painted).isNotEqualTo(theme.semantic().color(SemanticColor.TEXT_SECONDARY));
    }

    @Test void aTranslucentSlotThatHoldsKeepsItsAlpha() {
        var slot = new Color(0x1E, 0x1F, 0x22, 0xF0);
        var theme = with(SemanticColor.TEXT_SECONDARY, slot);
        var pair = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
        assertThat(SurfaceContrast.painted(theme, pair)).isEqualTo(slot);
        assertThat(SurfaceContrast.painted(theme, pair).getAlpha()).isEqualTo(0xF0);
    }

    /** Rulings 2: for opaque slots on opaque surfaces the guard paints exactly what it painted before, over every palette. */
    @Test void anOpaquePairIsPaintedAsBefore() {
        var palettes = new java.util.ArrayList<>(KitFixtures.themes());
        palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) for (var slot : SemanticColor.values()) for (var against : new SemanticColor[] {
                SemanticColor.SURFACE_CARD, SemanticColor.SURFACE_APP, SemanticColor.SURFACE_INPUT, SemanticColor.ACCENT}) {
            Color s = slot.color(theme), a = against.color(theme);
            if (s.getAlpha() != 255 || a.getAlpha() != 255) continue;
            for (var guard : new SurfaceContrast.Guard[] {SurfaceContrast.Guard.TEXT, SurfaceContrast.Guard.STRUCTURAL}) {
                var before = guard.apply(s, a, theme.semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(SurfaceContrast.painted(theme, new PaintPair(slot, against, guard)))
                    .as("%s %s on %s", theme.name(), slot.key(), against.key()).isEqualTo(before);
            }
        }
    }

    /** Rulings 5 (spec 7.3): Latte's window border on its app surface is 1.17:1, below the structural floor. */
    @Test void theStructuralGuardEngagesOnLattesWindowBorder() {
        var latte = KitFixtures.themes().stream().filter(t -> t.name().equals("Catppuccin Latte")).findFirst().orElseThrow();
        var pair = new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL);
        Color raw = latte.semantic().color(SemanticColor.BORDER_WINDOW), app = latte.semantic().color(SemanticColor.SURFACE_APP);
        assertThat(ColorMath.contrast(raw, app)).as("precondition").isLessThan(SurfaceContrast.STRUCTURAL);
        Color painted = SurfaceContrast.painted(latte, pair);
        assertThat(painted).isNotEqualTo(raw);
        assertThat(ColorMath.contrast(painted, app)).isGreaterThanOrEqualTo(SurfaceContrast.STRUCTURAL);
        assertThat(latte.semantic().color(SemanticColor.BORDER_WINDOW)).as("the role is unchanged").isEqualTo(raw);
    }
}
