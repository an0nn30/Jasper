package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.DerivedColor.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 7.1: the derived roles' recipes, their overrides, their cache and how a pair is painted through its guard. */
class DerivedPaletteTest {
    static List<GeneratedTheme> palettes() {
        var palettes = new ArrayList<>(KitFixtures.themes());
        palettes.addAll(KitFixtures.degenerate());
        return palettes;
    }

    private static double apart(Color a, Color b) { return Math.abs(ColorMath.lightness(a) - ColorMath.lightness(b)); }

    @Test void slateCobaltLightMeetsTheDesignCalibration() {
        var light = DerivedPalette.of(JasperTheme.bundled().generated(false));
        assertThat(light.color(SURFACE_HEADER)).as("header strip, against #E1E3E6").isEqualTo(new Color(0xDFE2E6));
        assertThat(light.color(SURFACE_TABLE_HEADER)).as("table header, against #F0F1F3").isEqualTo(new Color(0xF1F2F2));
        assertThat(light.color(SURFACE_ROW_HOVER)).as("row hover, against #EEF2FA").isEqualTo(new Color(0xEEF3FB));
    }

    /** Every role is opaque, and the recipes keep the distances spec 7.1 names, over every palette. */
    @Test void everyRoleIsOpaqueAndEveryRecipeKeepsItsDistancesOverEveryPalette() {
        for (var theme : palettes()) {
            var palette = theme.semantic();
            var derived = DerivedPalette.derive(palette, Map.of());
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            for (var role : DerivedColor.values())
                assertThat(DerivedPalette.of(theme).color(role).getAlpha()).as(where + " " + role).isEqualTo(255);
            Color app = palette.color(SemanticColor.SURFACE_APP), card = palette.color(SemanticColor.SURFACE_CARD);
            assertThat(apart(derived.color(SURFACE_HEADER), app)).as(where + " header").isGreaterThanOrEqualTo(3);
            assertThat(apart(derived.color(SURFACE_HEADER_ACTIVE), derived.color(SURFACE_HEADER))).as(where + " active header")
                .isGreaterThanOrEqualTo(2);
            assertThat(apart(derived.color(SURFACE_STRIPE), app)).as(where + " stripe").isGreaterThanOrEqualTo(1.5);
            assertThat(apart(derived.color(SURFACE_TABLE_HEADER), card)).as(where + " table header").isGreaterThanOrEqualTo(3);
            assertThat(apart(derived.color(SURFACE_ZEBRA), card)).as(where + " zebra").isGreaterThanOrEqualTo(1.5)
                .isLessThan(apart(derived.color(SURFACE_TABLE_HEADER), card));
            assertThat(apart(derived.color(SURFACE_FOOTER), palette.color(SemanticColor.SURFACE_DIALOG))).as(where + " footer")
                .isGreaterThanOrEqualTo(2);
            Color edge = derived.color(ACCENT_EDGE), accent = palette.color(SemanticColor.ACCENT);
            if (!edge.equals(Color.BLACK) && !edge.equals(Color.WHITE))
                assertThat(apart(edge, accent)).as(where + " accent edge").isGreaterThanOrEqualTo(7);
        }
    }

    @Test void derivedPalettesAreKeptByThemeIdentity() {
        var light = JasperTheme.bundled().generated(false);
        assertThat(DerivedPalette.of(light)).isSameAs(DerivedPalette.of(light));
        var copy = KitFixtures.withDark(KitFixtures.withDark(light, true), false);
        assertThat(copy).isNotSameAs(light);
        assertThat(DerivedPalette.of(copy)).as("an equal theme object derives equal roles").isEqualTo(DerivedPalette.of(light));
    }

    @Test void underATitleBarOfItsOwnTheHeaderDerivesFromTheHeaderPalette() throws Exception {
        var light = TitleBarThemeTest.darkHeader().generated(false);
        var header = DerivedPalette.of(TitleBarTheme.of(light));
        Color bar = new Color(0x27282E), ink = new Color(0xDFE1E5);
        assertThat(header.color(SURFACE_HEADER)).isEqualTo(SurfaceContrast.apart(ColorMath.mix(ink, bar, .05), bar, 3));
        assertThat(header).as("the header palette's text is the title bar's").isNotEqualTo(DerivedPalette.of(light));
        var plain = JasperTheme.bundled().generated(false);
        assertThat(DerivedPalette.of(TitleBarTheme.of(plain))).isSameAs(DerivedPalette.of(plain));
    }

    @Test void aTranslucentInputIsLaidOverTheSurfaceItsRecipeMixesItWith() {
        var base = KitFixtures.light();
        Color card = base.semantic().color(SemanticColor.SURFACE_CARD), app = base.semantic().color(SemanticColor.SURFACE_APP);
        Color border = new Color(0x10, 0x20, 0x30, 0x80), ink = new Color(0x20, 0x10, 0x00, 0x99);
        var translucent = recoloured(base, Map.of(SemanticColor.BORDER_WINDOW, border, SemanticColor.TEXT_PRIMARY, ink));
        var onCard = recoloured(base, Map.of(SemanticColor.BORDER_WINDOW, ColorMath.mix(border, card, 0x80 / 255.0),
            SemanticColor.TEXT_PRIMARY, ColorMath.mix(ink, card, 0x99 / 255.0)));
        var onApp = recoloured(base, Map.of(SemanticColor.TEXT_PRIMARY, ColorMath.mix(ink, app, 0x99 / 255.0)));
        assertThat(DerivedPalette.of(translucent).color(BORDER_DIVIDER)).isEqualTo(DerivedPalette.of(onCard).color(BORDER_DIVIDER));
        assertThat(DerivedPalette.of(translucent).color(SURFACE_TABLE_HEADER)).isEqualTo(DerivedPalette.of(onCard).color(SURFACE_TABLE_HEADER));
        assertThat(DerivedPalette.of(translucent).color(SURFACE_HEADER)).isEqualTo(DerivedPalette.of(onApp).color(SURFACE_HEADER));
        for (var role : DerivedColor.values()) assertThat(DerivedPalette.of(translucent).color(role).getAlpha()).isEqualTo(255);
    }

    @Test void anOverrideReplacesItsRecipeAndTheRecipesAfterItReadIt() {
        var base = KitFixtures.light();
        Color header = new Color(0xE1E3E6), table = new Color(0xFAFAFA);
        var theme = new GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(),
            base.syntax(), base.glass(), Map.of(SURFACE_HEADER, header, SURFACE_TABLE_HEADER, table));
        var derived = DerivedPalette.of(theme);
        Color card = base.semantic().color(SemanticColor.SURFACE_CARD);
        assertThat(derived.color(SURFACE_HEADER)).isEqualTo(header);
        assertThat(derived.color(SURFACE_HEADER_ACTIVE)).isEqualTo(SurfaceContrast.apart(
            ColorMath.mix(base.semantic().color(SemanticColor.ACCENT), base.semantic().color(SemanticColor.SURFACE_APP), .10), header, 2));
        assertThat(derived.color(SURFACE_TABLE_HEADER)).isEqualTo(table);
        assertThat(derived.color(SURFACE_ZEBRA)).as("halfway, so it stays closer to the card than the authored header")
            .isEqualTo(ColorMath.mix(table, card, .5));
        assertThat(derived.color(BORDER_DIVIDER)).as("a role after them that reads neither").isEqualTo(DerivedPalette.of(base).color(BORDER_DIVIDER));
        var authored = new GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(),
            base.syntax(), base.glass(), Map.of(SURFACE_HEADER, header, SURFACE_HEADER_ACTIVE, header));
        assertThat(DerivedPalette.of(authored).color(SURFACE_HEADER_ACTIVE)).as("the author's colour, not repaired").isEqualTo(header);
    }

    @Test void paintedIsThePairsGuardOverEitherKindOfRole() {
        for (var theme : palettes()) {
            var palette = theme.semantic();
            assertThat(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_FIELD_RAISED,
                SurfaceContrast.Guard.INDICATOR))).isEqualTo(SurfaceContrast.on(palette, SemanticColor.TEXT_PLACEHOLDER,
                SemanticColor.SURFACE_FIELD_RAISED, SurfaceContrast.INDICATOR));
            assertThat(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.SURFACE_TABSTRIP, SemanticColor.SURFACE_TERMINAL,
                SurfaceContrast.Guard.apart(SurfaceContrast.TAB_LIGHTNESS)))).isEqualTo(SurfaceContrast.apart(
                palette.color(SemanticColor.SURFACE_TABSTRIP), palette.color(SemanticColor.SURFACE_TERMINAL), SurfaceContrast.TAB_LIGHTNESS));
            var warning = new PaintPair(SemanticColor.STATUS_WARNING, WARNING_GROUND, SurfaceContrast.Guard.TEXT);
            assertThat(ColorMath.contrast(SurfaceContrast.painted(theme, warning), DerivedPalette.of(theme).color(WARNING_GROUND)))
                .isGreaterThanOrEqualTo(4.5);
            assertThat(WARNING_GROUND.color(theme)).isEqualTo(DerivedPalette.of(theme).color(WARNING_GROUND));
            assertThat(SemanticColor.ACCENT.color(theme)).isEqualTo(palette.color(SemanticColor.ACCENT));
        }
    }

    private static GeneratedTheme recoloured(GeneratedTheme base, Map<SemanticColor, Color> roles) {
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, roles.getOrDefault(role, base.semantic().color(role)));
        return KitFixtures.withSemantic(base, new SemanticPalette(values));
    }
}
