package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 7.6: the Enterprise theme, the port of IntelliJ Light and New UI Dark (tools/themes/enterprise/port.json). */
class EnterpriseThemeTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final Path FILE = ROOT.resolve("plugins/enterprise/src/main/resources/themes/enterprise.json");

    /** Spec 7.6 (plan 12): the file is the port of IntelliJ Light and New UI Dark, with port.json's choices. */
    @Test void theFileIsThePortOfIntelliJLightAndNewUiDark() throws Exception {
        var theme = JasperTheme.parse(Files.readString(FILE), "enterprise");
        assertThat(theme.name()).isEqualTo("Enterprise");
        assertThat(theme.author()).isEqualTo("JetBrains, ported by Jasper");
        assertThat(theme.schemes()).extracting(scheme -> scheme.id().toString()).containsExactly("enterprise/light", "enterprise/dark");
        var light = theme.generated(false); var dark = theme.generated(true);
        Map<SemanticColor, Integer> lightRoles = Map.of(SemanticColor.SURFACE_APP, 0xF2F2F2, SemanticColor.SURFACE_CARD, 0xFFFFFF,
            SemanticColor.TEXT_PRIMARY, 0x000000, SemanticColor.ACCENT, 0x2675BF, SemanticColor.SELECTION_BG, 0x2675BF,
            SemanticColor.BORDER_WINDOW, 0xD1D1D1, SemanticColor.BORDER_CONTROL, 0xC4C4C4, SemanticColor.SURFACE_TERMINAL, 0xFFFFFF);
        Map<SemanticColor, Integer> darkRoles = Map.of(SemanticColor.SURFACE_APP, 0x2B2D30, SemanticColor.SURFACE_CARD, 0x2B2D30,
            SemanticColor.TEXT_PRIMARY, 0xDFE1E5, SemanticColor.ACCENT, 0x366ACE, SemanticColor.SELECTION_BG, 0x2E436E,
            SemanticColor.BORDER_WINDOW, 0x1E1F22, SemanticColor.BORDER_CONTROL, 0x4E5157, SemanticColor.SURFACE_TERMINAL, 0x1E1F22);
        lightRoles.forEach((role, rgb) -> assertThat(light.semantic().color(role).getRGB() & 0xFFFFFF).as("light " + role.key()).isEqualTo(rgb));
        darkRoles.forEach((role, rgb) -> assertThat(dark.semantic().color(role).getRGB() & 0xFFFFFF).as("dark " + role.key()).isEqualTo(rgb));
        assertThat(light.derivedOverrides()).containsOnly(entry(DerivedColor.SURFACE_HEADER, new Color(0xF2F2F2)),
            entry(DerivedColor.SURFACE_HEADER_ACTIVE, new Color(0xDFE2E9)), entry(DerivedColor.SURFACE_TABLE_HEADER, new Color(0xFFFFFF)),
            entry(DerivedColor.SURFACE_ZEBRA, new Color(0xF2F5F9)), entry(DerivedColor.SURFACE_ROW_HOVER, new Color(0xEDF5FC)));
        assertThat(dark.derivedOverrides()).containsOnly(entry(DerivedColor.SURFACE_HEADER, new Color(0x2B2D30)),
            entry(DerivedColor.SURFACE_ZEBRA, new Color(0x2B2D30)));
        assertThat(theme.matching(false).colors().background()).isEqualTo(new Color(0xFFFFFF));
        assertThat(theme.matching(true).colors().background()).isEqualTo(new Color(0x1E1F22));
    }

    /** Rulings 15: both accents keep their labels white under the TEXT guard. */
    @Test void bothAccentsKeepWhiteText() throws Exception {
        var theme = JasperTheme.parse(Files.readString(FILE), "enterprise");
        for (boolean dark : new boolean[] {false, true}) {
            var g = theme.generated(dark);
            assertThat(SurfaceContrast.painted(g, new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT)))
                .as(dark ? "dark" : "light").isEqualTo(Color.WHITE);
        }
    }

    /** The user's request (3), Rulings 14 and 17: Light's focused header paints exactly #DFE2E9; Dark's keeps a visible tint. */
    @Test void theFocusedHeaderPaintsTheUsersColourInLightAndATintInDark() throws Exception {
        var theme = JasperTheme.parse(Files.readString(FILE), "enterprise");
        assertThat(SurfaceContrast.painted(theme.generated(false), ChromePainter.FOCUSED_HEADER)).isEqualTo(new Color(0xDFE2E9));
        var dark = theme.generated(true);
        assertThat(SurfaceContrast.painted(dark, ChromePainter.FOCUSED_HEADER)).isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(dark))
            .isNotEqualTo(DerivedColor.SURFACE_HEADER.color(dark));
    }
}
