package dev.jasper.ui.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SemanticPalette;
import java.util.Set;
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

    /** Spec 16.1.2's theme set: the shipped variants, the converter goldens that have a UI variant, then Enterprise. */
    @Test void theThemeSetIsTheShippedVariantsThenTheConvertedOnes() {
        assertThat(KitFixtures.themes()).extracting(theme -> theme.name() + (theme.dark() ? " Dark" : " Light")).containsExactly(
            "Slate Cobalt Dark", "Slate Cobalt Light", "Catppuccin Latte Light", "Catppuccin Frappé Dark",
            "Catppuccin Macchiato Dark", "Catppuccin Mocha Dark", "Ubuntu Yaru Dark", "Ubuntu Yaru Light", "Ocean Dark", "Ocean Dawn Light", "Stray Dark",
            "Enterprise Dark", "Enterprise Light");
        assertThat(KitFixtures.themes().getFirst()).isSameAs(KitFixtures.dark());
    }

    /** Spec 16.1.2's eight degenerate palettes, each a Slate Cobalt variant with its terminal, editor and alpha kept. */
    @Test void theDegeneratePalettesAreTheEightNamedOnes() {
        var palettes = KitFixtures.degenerate();
        assertThat(palettes).extracting(GeneratedTheme::name).containsExactly("Degenerate: one grey", "Degenerate: all black",
            "Degenerate: all white", "Degenerate: accent on the surfaces", "Degenerate: maximum chroma",
            "Degenerate: text on the surfaces", "Degenerate: borders on the surfaces", "Degenerate: light under the dark flag");
        for (var role : SemanticColor.values()) {
            assertThat(rgb(palettes.get(0).semantic(), role)).as("one grey " + role.key()).isEqualTo(0x808080);
            assertThat(rgb(palettes.get(1).semantic(), role)).as("all black " + role.key()).isZero();
            assertThat(rgb(palettes.get(2).semantic(), role)).as("all white " + role.key()).isEqualTo(0xFFFFFF);
            assertThat(Set.of(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF)).as("maximum chroma " + role.key())
                .contains(rgb(palettes.get(4).semantic(), role));
            assertThat(palettes.get(0).semantic().color(role).getAlpha()).as("alpha kept " + role.key())
                .isEqualTo(KitFixtures.dark().semantic().color(role).getAlpha());
        }
        assertThat(KitFixtures.degenerate().get(4).semantic()).as("seeded").isEqualTo(palettes.get(4).semantic());
        var accent = palettes.get(3).semantic();
        int card = rgb(KitFixtures.light().semantic(), SemanticColor.SURFACE_CARD);
        for (var role : new SemanticColor[] {SemanticColor.ACCENT, SemanticColor.SELECTION_BG, SemanticColor.SURFACE_APP, SemanticColor.SURFACE_INPUT})
            assertThat(rgb(accent, role)).as("accent on the surfaces " + role.key()).isEqualTo(card);
        assertThat(accent.color(SemanticColor.TEXT_PRIMARY)).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
        var text = palettes.get(5).semantic();
        for (var role : new SemanticColor[] {SemanticColor.TEXT_PRIMARY, SemanticColor.TEXT_ONACCENT, SemanticColor.STATUS_WARNING, SemanticColor.SURFACE_PANEL})
            assertThat(rgb(text, role)).as("text on the surfaces " + role.key()).isEqualTo(card);
        var borders = palettes.get(6).semantic();
        for (var role : new SemanticColor[] {SemanticColor.BORDER_WINDOW, SemanticColor.BORDER_CONTROL, SemanticColor.BUTTON_SECONDARY_BORDER, SemanticColor.SURFACE_APP})
            assertThat(rgb(borders, role)).as("borders on the surfaces " + role.key()).isEqualTo(card);
        assertThat(borders.color(SemanticColor.ACCENT)).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.ACCENT));
        var flagged = palettes.get(7);
        assertThat(flagged.dark()).isTrue();
        assertThat(flagged.semantic()).isEqualTo(KitFixtures.light().semantic());
        for (int i = 0; i < palettes.size(); i++)
            assertThat(palettes.get(i).terminal()).as(palettes.get(i).name())
                .isEqualTo((i < 3 || i == 4 ? KitFixtures.dark() : KitFixtures.light()).terminal());
    }

    private static int rgb(SemanticPalette palette, SemanticColor role) { return palette.color(role).getRGB() & 0xFFFFFF; }

    /** Plan 4 (Rulings 4): custom() tints every authored override as it tints the roles; marked() drops them. */
    @Test void customTintsAndMarkedDropsTheAuthoredOverrides() {
        var latte = KitFixtures.themes().stream().filter(t -> t.name().equals("Catppuccin Latte")).findFirst().orElseThrow();
        assertThat(latte.derivedOverrides()).as("Latte authors overrides").isNotEmpty();
        var custom = KitFixtures.custom(latte);
        assertThat(custom.derivedOverrides().keySet()).isEqualTo(latte.derivedOverrides().keySet());
        latte.derivedOverrides().forEach((role, colour) ->
            assertThat(custom.derivedOverrides().get(role)).as(role.key()).isNotEqualTo(colour));
        var marked = KitFixtures.marked(latte);
        assertThat(marked.derivedOverrides()).isEmpty();
        for (var role : SemanticColor.values()) assertThat(marked.semantic().color(role)).isEqualTo(KitFixtures.marker(role));
        assertThat(KitFixtures.custom().semantic()).isEqualTo(KitFixtures.custom(KitFixtures.dark()).semantic());
        assertThat(KitFixtures.marked().semantic()).isEqualTo(KitFixtures.marked(KitFixtures.dark()).semantic());
    }

    /** Plan 4c Rulings 20: Enterprise Light names spec 7.6's Light roles and overrides; the rest is Slate Cobalt Light's. */
    @org.junit.jupiter.api.Test void enterpriseLightIsSpecSevenSixsLightTable() {
        var theme = KitFixtures.enterpriseLight();
        org.assertj.core.api.Assertions.assertThat(theme.name()).isEqualTo("Enterprise");
        org.assertj.core.api.Assertions.assertThat(theme.dark()).isFalse();
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP)).isEqualTo(new java.awt.Color(0xECEDEF));
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT)).isEqualTo(new java.awt.Color(0x2F65CA));
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_CONTROL)).isEqualTo(new java.awt.Color(0xB4B8BF));
        org.assertj.core.api.Assertions.assertThat(dev.jasper.ui.theme.DerivedColor.SURFACE_TABLE_HEADER.color(theme)).isEqualTo(new java.awt.Color(0xF0F1F3));
        org.assertj.core.api.Assertions.assertThat(dev.jasper.ui.theme.DerivedColor.SURFACE_ZEBRA.color(theme)).isEqualTo(new java.awt.Color(0xF7F8FA));
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL)).as("an unnamed role")
            .isEqualTo(KitFixtures.light().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL));
        // The ruled roles follow the named ones, as JasperTheme derives them for a theme file.
        var semantic = theme.semantic();
        for (var rule : java.util.Map.of(dev.jasper.ui.theme.SemanticColor.BORDER_FOCUS, dev.jasper.ui.theme.SemanticColor.ACCENT,
                dev.jasper.ui.theme.SemanticColor.SURFACE_STATUS, dev.jasper.ui.theme.SemanticColor.SURFACE_APP,
                dev.jasper.ui.theme.SemanticColor.BORDER_IDENTITY, dev.jasper.ui.theme.SemanticColor.BORDER_CONTROL,
                dev.jasper.ui.theme.SemanticColor.HUE_BLUE_SURFACE, dev.jasper.ui.theme.SemanticColor.HUE_BLUE,
                dev.jasper.ui.theme.SemanticColor.HUE_GREEN_SURFACE, dev.jasper.ui.theme.SemanticColor.HUE_GREEN,
                dev.jasper.ui.theme.SemanticColor.HUE_AMBER_SURFACE, dev.jasper.ui.theme.SemanticColor.HUE_AMBER,
                dev.jasper.ui.theme.SemanticColor.HUE_PURPLE_SURFACE, dev.jasper.ui.theme.SemanticColor.HUE_PURPLE,
                dev.jasper.ui.theme.SemanticColor.HUE_NEUTRAL_SURFACE, dev.jasper.ui.theme.SemanticColor.HUE_NEUTRAL).entrySet())
            org.assertj.core.api.Assertions.assertThat(semantic.color(rule.getKey())).as(rule.getKey().key()).isEqualTo(semantic.color(rule.getValue()));
        org.assertj.core.api.Assertions.assertThat(semantic.color(dev.jasper.ui.theme.SemanticColor.HUE_BLUE_SURFACE)).isEqualTo(new java.awt.Color(0x2F65CA));
    }
}
