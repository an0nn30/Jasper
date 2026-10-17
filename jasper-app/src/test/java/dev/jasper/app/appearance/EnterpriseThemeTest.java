package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Spec 7.6: the Enterprise theme's two variants, as the tables give them and as the mocks paint them. */
class EnterpriseThemeTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final Path FILE = ROOT.resolve("plugins/enterprise/src/main/resources/themes/enterprise.json");
    static final Map<String, String> LIGHT = Map.ofEntries(Map.entry("surface.app", "#ECEDEF"), Map.entry("surface.dialog", "#ECEDEF"),
        Map.entry("surface.titlebar", "#E1E3E6"), Map.entry("surface.panel", "#F7F7F8"), Map.entry("surface.sidebar", "#F7F7F8"),
        Map.entry("surface.card", "#FFFFFF"), Map.entry("surface.input", "#FFFFFF"), Map.entry("surface.terminal", "#FFFFFF"),
        Map.entry("accent", "#2F65CA"), Map.entry("selection.bg", "#2F65CA"), Map.entry("hue.blue", "#2F65CA"),
        Map.entry("text.onAccent", "#FFFFFF"), Map.entry("text.onSelection", "#FFFFFF"), Map.entry("text.onAccent.secondary", "#DCE5F7"),
        Map.entry("text.onSelection.secondary", "#DCE5F7"), Map.entry("status.warning", "#8A5A00"), Map.entry("hue.amber", "#B5651D"),
        Map.entry("border.window", "#C4C7CC"), Map.entry("border.control", "#B4B8BF"), Map.entry("border.input", "#B4B8BF"),
        Map.entry("button.secondary.border", "#B4B8BF"), Map.entry("text.primary", "#1E1F22"), Map.entry("text.strong.secondary", "#3A3D42"),
        Map.entry("text.secondary", "#5C6370"), Map.entry("text.tertiary", "#5C6370"), Map.entry("text.placeholder", "#7A808A"),
        Map.entry("status.success", "#2E9E4F"), Map.entry("hue.green", "#2E9E4F"), Map.entry("status.danger", "#D03A3A"),
        Map.entry("hue.purple", "#8A4FBF"));
    static final Map<String, String> DARK = Map.ofEntries(Map.entry("surface.app", "#3C3F44"), Map.entry("surface.dialog", "#3C3F44"),
        Map.entry("surface.titlebar", "#36393E"), Map.entry("surface.panel", "#35383D"), Map.entry("surface.sidebar", "#35383D"),
        Map.entry("surface.card", "#2E3135"), Map.entry("surface.input", "#2E3135"), Map.entry("surface.terminal", "#2E3135"),
        Map.entry("accent", "#3D74D6"), Map.entry("selection.bg", "#3D74D6"), Map.entry("hue.blue", "#3D74D6"),
        Map.entry("text.onAccent", "#FFFFFF"), Map.entry("text.onSelection", "#FFFFFF"), Map.entry("button.secondary.bg", "#45484D"),
        Map.entry("surface.control.selected", "#50545B"), Map.entry("hue.amber", "#D08A4A"), Map.entry("border.window", "#2A2C30"),
        Map.entry("border.control", "#5A5E65"), Map.entry("border.input", "#5A5E65"), Map.entry("button.secondary.border", "#5A5E65"),
        Map.entry("text.primary", "#DFE1E5"), Map.entry("text.strong.secondary", "#C4C8CE"), Map.entry("text.secondary", "#A0A5AD"),
        Map.entry("text.tertiary", "#A0A5AD"), Map.entry("text.placeholder", "#8E939B"), Map.entry("status.success", "#4CB86A"),
        Map.entry("hue.green", "#4CB86A"), Map.entry("status.warning", "#E3C27A"));
    static final Map<DerivedColor, String> LIGHT_OVERRIDES = Map.of(DerivedColor.SURFACE_HEADER, "#E1E3E6", DerivedColor.SURFACE_HEADER_ACTIVE, "#D9DFEA",
        DerivedColor.SURFACE_STRIPE, "#E4E6E9", DerivedColor.SURFACE_TABLE_HEADER, "#F0F1F3", DerivedColor.SURFACE_ZEBRA, "#F7F8FA",
        DerivedColor.SURFACE_ROW_HOVER, "#EEF2FA", DerivedColor.WARNING_GROUND, "#FBF1DA", DerivedColor.WARNING_BORDER, "#E6CF95");
    static final Map<DerivedColor, String> DARK_OVERRIDES = Map.of(DerivedColor.SURFACE_HEADER, "#36393E", DerivedColor.SURFACE_HEADER_ACTIVE, "#3D4658",
        DerivedColor.SURFACE_STRIPE, "#383B40", DerivedColor.SURFACE_TABLE_HEADER, "#393C41", DerivedColor.SURFACE_ZEBRA, "#313439",
        DerivedColor.WARNING_GROUND, "#4A4128", DerivedColor.WARNING_BORDER, "#6B5A30");
    /** The neutral roles 7.6 leaves to its rule (Rulings 7). */
    static final List<String> NEUTRAL = List.of("surface.panel.raised", "surface.field.raised", "surface.control", "surface.control.hover",
        "surface.control.selected", "surface.menu", "surface.tabstrip", "surface.emptyTile", "border.field.raised", "border.field.raised.hover",
        "border.menu", "text.field.raised", "segment.track", "segment.selected.bg", "toggle.track.off", "toggle.knob", "button.secondary.bg",
        "button.secondary.bg.hover", "status.idle", "hue.neutral");

    static JasperTheme theme() throws Exception { return JasperTheme.parse(Files.readString(FILE), "enterprise"); }
    static Color role(JasperTheme theme, boolean dark, String key) { return theme.palette(dark).color(SemanticColor.of(key).orElseThrow()); }

    @Test void bothVariantsAreSpecSevenSixsTables() throws Exception {
        var theme = theme();
        assertThat(theme.name()).isEqualTo("Enterprise");
        LIGHT.forEach((key, hex) -> assertThat(ColorMath.hex(role(theme, false, key))).as("light " + key).isEqualToIgnoringCase(hex));
        DARK.forEach((key, hex) -> assertThat(ColorMath.hex(role(theme, true, key))).as("dark " + key).isEqualToIgnoringCase(hex));
        LIGHT_OVERRIDES.forEach((role, hex) -> assertThat(ColorMath.hex(theme.generated(false).derivedOverrides().get(role))).as("light " + role).isEqualToIgnoringCase(hex));
        DARK_OVERRIDES.forEach((role, hex) -> assertThat(ColorMath.hex(theme.generated(true).derivedOverrides().get(role))).as("dark " + role).isEqualToIgnoringCase(hex));
        assertThat(theme.generated(true).derivedOverrides()).as("Dark's row hover keeps its recipe").doesNotContainKey(DerivedColor.SURFACE_ROW_HOVER);
    }

    @Test void theUnnamedRolesFollowTheRule() throws Exception {
        var theme = theme();
        var cobalt = JasperTheme.bundled();
        double lightCard = ColorMath.lightness(role(theme, false, "surface.card")), darkCard = ColorMath.lightness(role(theme, true, "surface.card"));
        for (String key : NEUTRAL) {
            double light = ColorMath.lightness(role(theme, false, key));
            assertThat(light).as("light " + key + " keeps Slate Cobalt Light's L*").isCloseTo(ColorMath.lightness(role(cobalt, false, key)), org.assertj.core.data.Offset.offset(0.6));
            if (DARK.containsKey(key)) continue;
            assertThat(ColorMath.lightness(role(theme, true, key)) - darkCard).as("dark " + key + " keeps Light's distance from the card, lighter")
                .isCloseTo(Math.abs(light - lightCard), org.assertj.core.data.Offset.offset(0.6));
        }
        for (String key : List.of("status.danger", "hue.purple", "text.onAccent.secondary", "text.onSelection.secondary"))
            assertThat(ColorMath.contrast(role(theme, true, key), role(theme, true, "surface.card"))).as("dark " + key).isGreaterThanOrEqualTo(4.5);
        assertThat(theme.schemes()).extracting(scheme -> scheme.id().toString())
            .containsExactly("enterprise/light", "enterprise/dark", "enterprise/dark-terminal");
        var darkTerminal = theme.schemes().get(2).colors();
        assertThat(ColorMath.hex(darkTerminal.background())).isEqualToIgnoringCase("#1F2125");
        assertThat(ColorMath.hex(darkTerminal.foreground())).isEqualToIgnoringCase("#D5D8DD");
        assertThat(darkTerminal.ansi()).isEqualTo(cobalt.matching(true).colors().ansi());
        assertThat(ColorMath.hex(theme.matching(true).colors().background())).isEqualToIgnoringCase("#2E3135");
    }

    /** Verified fact 12: where the mocks paint a theme role, at their 2x pixels. */
    @Test void theThemeMatchesTheMocksWhereTheyShowIt() throws Exception {
        var reference = ROOT.resolve("docs/design/enterprise/reference");
        var mocks = Map.of(false, reference.resolve("01 Workstation/Workstation - Connected (plugins in use).png"),
            true, reference.resolve("08 Dark Mode/Workstation Dark - Connected (plugins in use).png"));
        var theme = theme();
        for (boolean dark : new boolean[] {false, true}) {
            var image = ImageIO.read(mocks.get(dark).toFile());
            var generated = theme.generated(dark);
            Map<int[], Color> points = Map.of(new int[] {2592, 835}, role(theme, dark, "surface.card"), new int[] {2448, 455}, role(theme, dark, "selection.bg"),
                new int[] {1728, 144}, role(theme, dark, "surface.app"), new int[] {2434, 204}, generated.derivedOverrides().get(DerivedColor.SURFACE_HEADER_ACTIVE),
                new int[] {2600, 1158}, generated.derivedOverrides().get(DerivedColor.SURFACE_TABLE_HEADER),
                new int[] {700, 1730}, generated.derivedOverrides().get(DerivedColor.SURFACE_STRIPE), new int[] {1296, 864}, theme.matching(dark).colors().background());
            points.forEach((at, color) -> assertThat(ColorMath.hex(new Color(image.getRGB(at[0], at[1]) & 0xFFFFFF)))
                .as((dark ? "dark" : "light") + " mock at " + at[0] + "," + at[1]).isEqualToIgnoringCase(ColorMath.hex(color)));
        }
    }
}
