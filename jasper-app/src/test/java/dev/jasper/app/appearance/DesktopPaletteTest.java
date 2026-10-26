package dev.jasper.app.appearance;

import dev.jasper.app.platform.DesktopObservation;
import dev.jasper.app.platform.DesktopEnvironment;
import org.junit.jupiter.api.Test;
import dev.jasper.app.platform.DesktopObservation.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.*;
import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.assertThat;

class DesktopPaletteTest {
    @Test void qtAndKdeStatusIdentifiesActualSourceImplementationAndVersion() {
        for (var source : List.of(Source.QT5, Source.QT6, Source.KDE5, Source.KDE6)) {
            String implementation = switch (source) {
                case QT5 -> "Qt 5 application palette / QFusionStyle";
                case QT6 -> "Qt 6 application palette / QFusionStyle";
                case KDE5 -> "KDE Frameworks 5 / Qt 5 / QFusionStyle";
                case KDE6 -> "KDE Frameworks 6 / Qt 6 / QFusionStyle";
                default -> throw new AssertionError(source);
            };
            String version = source == Source.QT5 ? "5.15.2" : source == Source.QT6 ? "6.2.4"
                : source == Source.KDE5 ? "5.92.0 (Qt 5.15.2)" : "6.0.0 (Qt 6.5.0)";
            var palette = new Palette(1, source, Polarity.LIGHT, Map.of(Pair.WINDOW, colors(0xffffff, 0)), implementation, version, "");
            var result = DesktopPalette.resolve(false, DesktopPalette.SourceMode.QT,
                DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE")), input(false, Map.of(source, palette), Optional.empty()));
            assertThat(result.source()).contains(source);
            assertThat(result.status()).contains(implementation, version, switch (source) {
                case QT5 -> "Qt 5"; case QT6 -> "Qt 6";
                case KDE5 -> "KDE 5"; case KDE6 -> "KDE 6";
                default -> throw new AssertionError(source);
            }).doesNotContain("GTK", "qt5ct", "qt6ct");
            if (source == Source.KDE5 || source == Source.KDE6) assertThat(result.status()).contains("KColorScheme");
        }
    }
    @Test void kdeResolverRejectsIncompatiblePreferredSourceAndRecoversItsWholeSnapshot() {
        var environment = DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE", "KDE_SESSION_VERSION", "5"));
        var fallback = palette(Source.QT5, false, Map.of(Pair.WINDOW, colors(0xfafafa, 0), Pair.POPOVER, colors(0xeeeeff, 0)));
        var incompatible = palette(Source.KDE5, true, Map.of(Pair.WINDOW, colors(0x111111, 0xffffff)));
        var before = DesktopPalette.resolve(false, DesktopPalette.SourceMode.AUTO, environment,
            input(false, Map.of(Source.KDE5, incompatible, Source.QT5, fallback), Optional.empty()));
        assertThat(before.source()).contains(Source.QT5);
        assertThat(before.theme().semantic().color(SURFACE_APP)).isEqualTo(new Color(0xfafafa));
        var recovered = palette(Source.KDE5, false, Map.of(Pair.WINDOW, colors(0xffffff, 0)));
        var after = DesktopPalette.resolve(false, DesktopPalette.SourceMode.AUTO, environment,
            input(false, Map.of(Source.KDE5, recovered, Source.QT5, fallback), Optional.empty()));
        assertThat(after.source()).contains(Source.KDE5);
        assertThat(after.theme().semantic().color(SURFACE_APP)).isEqualTo(Color.WHITE);
        assertThat(after.theme().semantic().color(SURFACE_MENU)).as("missing optional role stays with the KDE window").isEqualTo(Color.WHITE);
        preservesContent(false, after.theme());
    }
    @Test void explicitQtRefusesUsableGtkWhenQtHasNoCompatibleWindow() {
        var gtk = palette(Source.GTK3, false, Map.of(Pair.WINDOW, colors(0xfafafa, 0)));
        var qt = palette(Source.QT6, true, Map.of(Pair.WINDOW, colors(0x111111, 0xffffff)));
        var observation = input(false, Map.of(Source.GTK3, gtk, Source.QT6, qt), Optional.empty());
        var result = DesktopPalette.resolve(false, DesktopPalette.SourceMode.QT, DesktopEnvironment.from(Map.of()), observation);
        assertThat(result.source()).isEmpty();
        assertThat(result.status()).contains("Slate Cobalt", "Qt 6").doesNotContain("GTK");
    }

    @Test void statusIdentifiesTheActualImplementationAndItsVersion() {
        for (var implementation : List.of("gtk4", "libadwaita")) {
            String version = implementation.equals("gtk4") ? "4.22.4" : "1.9.1 (GTK 4.22.4)";
            var palette = new Palette(1, Source.GTK4, Polarity.DARK,
                Map.of(Pair.WINDOW, colors(0x111111, 0xffffff)), implementation, version, "");
            var result = DesktopPalette.resolve(true, DesktopPalette.SourceMode.GTK4, DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")), input(false, Map.of(Source.GTK4, palette), Optional.empty()));
            assertThat(result.status()).contains(version, implementation.equals("gtk4") ? "GTK 4" : "libadwaita");
            if (implementation.equals("gtk4")) assertThat(result.status()).doesNotContain("libadwaita");
        }
    }

    @Test void noPortalOrGtkStillProducesValidatedBothAppearances() {
        for (boolean dark : List.of(false, true)) {
            var result = resolve(dark, false, Map.of(), Optional.empty());
            assertThat(result.source()).isEmpty();
            assertThat(DesktopPaintPairs.validate(result.theme(), false)).isEmpty();
            preservesContent(dark, result.theme());
        }
    }

    @Test void highContrastValidatesBothKitsAfterDerivedAndPaintGuards() {
        for (boolean dark : List.of(false, true)) {
            var result = resolve(dark, true, Map.of(Source.GTK3, palette(Source.GTK3, dark, Map.of(Pair.WINDOW,
                colors(dark ? 0x333333 : 0xeeeeee, dark ? 0xeeeeee : 0x222222)))), Optional.empty());
            assertThat(DesktopPaintPairs.validate(result.theme(), true)).isEmpty();
            preservesContent(dark, result.theme());
        }
    }

    @Test void validPortalAccentAloneAppliesWithoutToolkit() {
        var result = resolve(false, false, Map.of(), Optional.of(rgba(0x812099)));
        assertThat(result.theme().semantic().color(ACCENT)).isEqualTo(new Color(0x812099));
        assertThat(result.source()).isEmpty();
    }

    @Test void translucentAccentWithoutParentIsIgnored() {
        var absent = resolve(false, false, Map.of(), Optional.empty());
        var alpha = resolve(false, false, Map.of(), Optional.of(new Rgba(1, 0, 0, .5)));
        assertThat(alpha.theme().semantic()).isEqualTo(absent.theme().semantic());
    }

    @Test void explicitUnavailableSourceDoesNotUseUsableAlternate() {
        var input = input(false, Map.of(Source.GTK4, palette(Source.GTK4, false,
            Map.of(Pair.WINDOW, colors(0xfafafa, 0x111111)))), Optional.empty());
        assertThat(DesktopPalette.resolve(false, DesktopPalette.SourceMode.GTK3, DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")), input).source()).isEmpty();
    }

    @Test void missingVariantAndInvalidWindowAreUnavailable() {
        var wrongVariant = palette(Source.GTK3, true, Map.of(Pair.WINDOW, colors(0x111111, 0xffffff)));
        assertThat(resolve(false, false, Map.of(Source.GTK3, wrongVariant), Optional.empty()).source()).isEmpty();
        var low = palette(Source.GTK3, false, Map.of(Pair.WINDOW, colors(0xaaaaaa, 0xbbbbbb)));
        assertThat(resolve(false, false, Map.of(Source.GTK3, low), Optional.empty()).source()).isEmpty();
        var alpha = palette(Source.GTK3, false, Map.of(Pair.WINDOW, new Colors(new Rgba(1,1,1,.5), rgba(0))));
        assertThat(resolve(false, false, Map.of(Source.GTK3, alpha), Optional.empty()).source()).isEmpty();
    }

    @Test void conflictingPopoverRejectsSurfaceInsteadOfReplacingSharedInk() {
        var result = resolve(false, false, Map.of(Source.GTK3, palette(Source.GTK3, false, Map.of(
            Pair.WINDOW, colors(0xfafafa, 0x111111), Pair.POPOVER, colors(0x111111, 0xffffff)))), Optional.empty());
        assertThat(result.theme().semantic().color(SURFACE_MENU)).isEqualTo(new Color(0xfafafa));
        assertThat(result.theme().semantic().color(TEXT_PRIMARY)).isEqualTo(new Color(0x111111));
    }

    @Test void partialExtractionCompositesKnownParentAndNeverBorrowsOtherToolkit() {
        var chosen = palette(Source.GTK3, false, Map.of(Pair.WINDOW, colors(0xffffff, 0),
            Pair.VIEW, new Colors(new Rgba(0, 0, 0, .05), rgba(0))));
        var alternate = palette(Source.GTK4, false, Map.of(Pair.WINDOW, colors(0xffffff, 0), Pair.POPOVER, colors(0xeeeeff, 0)));
        var result = resolve(false, false, Map.of(Source.GTK3, chosen, Source.GTK4, alternate), Optional.empty());
        assertThat(result.source()).contains(Source.GTK3);
        assertThat(result.theme().semantic().color(SURFACE_INPUT)).isEqualTo(new Color(242, 242, 242));
        assertThat(result.theme().semantic().color(SURFACE_MENU)).isEqualTo(Color.WHITE);
        preservesContent(false, result.theme());
    }

    @Test void extremePortalAccentsNeverBreakEitherAppearanceOrContrastMode() {
        for (boolean dark : List.of(false, true)) for (boolean high : List.of(false, true))
            for (int rgb : new int[] {0, 0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0x808080, 0xff00ff, 0x00ffff, 0xffff00}) {
                DesktopPalette.Result result;
                try { result = resolve(dark, high, Map.of(), Optional.of(rgba(rgb))); }
                catch (IllegalStateException failure) { throw new AssertionError("dark="+dark+" high="+high+" rgb="+Integer.toHexString(rgb), failure); }
                assertThat(DesktopPaintPairs.validate(result.theme(), high)).as("dark=%s high=%s rgb=%x", dark, high, rgb).isEmpty();
            }
    }

    @Test void validToolkitAccentIsUsedUnlessPortalOverridesIt() {
        var palette = palette(Source.GTK3, false, Map.of(Pair.WINDOW, colors(0xffffff, 0), Pair.SELECTION, colors(0x812099, 0xffffff)));
        assertThat(resolve(false, false, Map.of(Source.GTK3, palette), Optional.empty()).theme().semantic().color(ACCENT))
            .isEqualTo(new Color(0x812099));
        assertThat(resolve(false, false, Map.of(Source.GTK3, palette), Optional.of(rgba(0x194488))).theme().semantic().color(ACCENT))
            .isEqualTo(new Color(0x194488));
    }

    @Test void preferredInvalidSourceFallsThroughToCompleteCompatibleWindow() {
        var invalid = palette(Source.GTK3, false, Map.of(Pair.VIEW, colors(0xffffff, 0)));
        var valid = palette(Source.GTK4, false, Map.of(Pair.WINDOW, colors(0xffffff, 0)));
        assertThat(resolve(false, false, Map.of(Source.GTK3, invalid, Source.GTK4, valid), Optional.empty()).source()).contains(Source.GTK4);
    }

    @Test void highContrastSwitchKnobRemainsVisibleOnBothTrackStates() {
        for (boolean dark : List.of(false, true)) {
            var theme = resolve(dark, true, Map.of(), Optional.empty()).theme();
            for (var surface : List.of(ACCENT, TOGGLE_TRACK_OFF, STATUS_WARNING))
                assertThat(ColorMath.contrast(TOGGLE_KNOB.color(theme), surface.color(theme))).isGreaterThanOrEqualTo(3);
        }
    }

    @Test void everySemanticAndOverridableDerivedRoleHasExactlyOneMapping() throws Exception {
        var expected = new ArrayList<String>();
        for (var role : SemanticColor.values()) expected.add(role.key());
        for (var role : DerivedColor.values()) if (role.overridable()) expected.add(role.key());
        var rows = resource("desktop-role-map.tsv");
        assertThat(rows.getFirst()).isEqualTo("role\tfamily\tinput\tfallback");
        assertThat(rows.subList(1, rows.size())).allSatisfy(row -> assertThat(row.split("\t", -1)).hasSize(4).doesNotContain(""));
        assertThat(rows.stream().skip(1).map(row -> row.split("\t")[0])).containsExactlyElementsOf(expected);
    }

    @Test void validatorIncludesInheritedTextSelectionInsteadOfOnlyListSelection() {
        var repaired = resolve(true, true, Map.of(), Optional.empty()).theme();
        var overrides = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        overrides.putAll(repaired.derivedOverrides());
        overrides.remove(DerivedColor.SURFACE_TEXT_SELECTION);
        var inherited = new GeneratedTheme(repaired.name(), repaired.dark(), repaired.author(), repaired.semantic(),
            repaired.terminal(), repaired.editor(), repaired.syntax(), repaired.glass(), overrides);
        assertThat(DesktopPaintPairs.validate(inherited, true))
            .contains("inherited-text-selection/text.primary/surface.textSelection");
        assertThat(DesktopPaintPairs.validate(repaired, true)).isEmpty();
    }

    @Test void paintRequirementsMatchReviewedInventory() throws Exception {
        var actual = new ArrayList<String>();
        actual.add("name\tink\tsurface\tkind");
        actual.addAll(DesktopPaintPairs.all().stream().map(p -> p.name()+"\t"+p.ink().key()+"\t"+p.surface().key()+"\t"+(p.text()?"text":"boundary")).toList());
        var written = java.nio.file.Path.of("build/desktop-paint-pairs.tsv");
        java.nio.file.Files.write(written, actual);
        assertThat(actual).containsExactlyElementsOf(resource("desktop-paint-pairs.tsv"));
        assertThat(DesktopPaintPairs.all().stream().map(DesktopPaintPairs.Requirement::name)).doesNotHaveDuplicates();
    }

    private static List<String> resource(String name) throws Exception {
        try (var input = DesktopPaletteTest.class.getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static void preservesContent(boolean dark, GeneratedTheme theme) {
        var base = JasperTheme.builtIn("slate-cobalt").orElseThrow().generated(dark);
        assertThat(theme.terminal()).isSameAs(base.terminal());
        assertThat(theme.editor()).isSameAs(base.editor());
        assertThat(theme.syntax()).isEqualTo(base.syntax());
        assertThat(theme.glass()).isSameAs(base.glass());
    }
    private static DesktopPalette.Result resolve(boolean dark, boolean high, Map<Source, Palette> palettes, Optional<Rgba> accent) {
        return DesktopPalette.resolve(dark, DesktopPalette.SourceMode.AUTO, DesktopEnvironment.from(Map.of()), input(high, palettes, accent));
    }
    private static DesktopObservation input(boolean high, Map<Source, Palette> palettes, Optional<Rgba> accent) {
        return new DesktopObservation(new Portal(0, Polarity.UNKNOWN, accent, Optional.of(high), ""), palettes);
    }
    private static Palette palette(Source source, boolean dark, Map<Pair, Colors> pairs) {
        return new Palette(0, source, dark ? Polarity.DARK : Polarity.LIGHT, pairs, "test", "1", "");
    }
    private static Colors colors(int background, int foreground) { return new Colors(rgba(background), rgba(foreground)); }
    private static Rgba rgba(int value) { return new Rgba((value>>16&255)/255.0, (value>>8&255)/255.0, (value&255)/255.0, 1); }
}
