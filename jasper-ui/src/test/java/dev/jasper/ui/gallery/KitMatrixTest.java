package dev.jasper.ui.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.KitFaces;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SurfaceContrast;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 16.1.3, the default kit's part: the gallery renders under Kit.DEFAULT over every theme and paints no literal. */
class KitMatrixTest {
    static List<KitGallery.Capture> captures(GeneratedTheme theme, Kit kit) { return captures(theme, kit, KitGallery.fixtures()); }

    static List<KitGallery.Capture> captures(GeneratedTheme theme, Kit kit, List<KitGallery.Fixture> fixtures) {
        var captures = new ArrayList<KitGallery.Capture>();
        Edt.run(() -> KitGallery.installed(theme, kit, (context, focus) -> {
            for (var fixture : fixtures) captures.add(KitGallery.capture(fixture, context, focus));
        }));
        return captures;
    }

    @Test void everyFixtureRendersUnderTheDefaultKitOverTheThemeSet() {
        for (var theme : KitFixtures.themes()) {
            var captures = captures(theme, Kit.DEFAULT);
            assertThat(captures).hasSameSizeAs(KitGallery.fixtures());
            for (var capture : captures)
                assertThat(colours(capture.image())).as("%s %s: %s", theme.name(), theme.dark() ? "Dark" : "Light", capture.fixture().name())
                    .hasSizeGreaterThan(1);
        }
    }

    @Test void underCustomNoFixturePaintsAColourOnlyAnotherThemeHas() {
        var foreign = foreign(KitFixtures.custom());
        var found = new ArrayList<String>();
        for (var capture : captures(KitFixtures.custom(), Kit.DEFAULT)) {
            var painted = areas(capture.image());
            painted.retainAll(foreign);
            for (int rgb : painted) found.add(capture.fixture().name() + " #" + Integer.toHexString(rgb & 0xFFFFFF));
        }
        assertThat(found).as("pixels in another theme's colours under custom()").isEmpty();
    }

    @Test void aLiteralOfAnotherThemesColourIsFound() {
        var literal = KitFixtures.dark().semantic().color(SemanticColor.ACCENT);
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y <= 2; y++) for (int x = 1; x <= 2; x++) image.setRGB(x, y, literal.getRGB());
        var painted = areas(image);
        painted.retainAll(foreign(KitFixtures.custom()));
        assertThat(painted).containsExactly(literal.getRGB());
    }

    /** Plan 4 (Rulings 3): an antialiased edge pixel that happens to equal another theme's colour is not a literal. */
    @Test void aSingleAntialiasedPixelOfAnotherThemesColourIsNotALiteral() {
        var literal = KitFixtures.dark().semantic().color(SemanticColor.ACCENT);
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(2, 2, literal.getRGB());
        var painted = areas(image);
        painted.retainAll(foreign(KitFixtures.custom()));
        assertThat(painted).isEmpty();
    }

    /** The theme set's opaque role colours that {@code custom} does not have, black and white aside: a literal paints one. */
    static Set<Integer> foreign(GeneratedTheme custom) {
        var foreign = new HashSet<Integer>();
        for (var theme : KitFixtures.themes()) for (var role : SemanticColor.values()) {
            var colour = theme.semantic().color(role);
            if (colour.getAlpha() == 255) foreign.add(colour.getRGB());
        }
        for (var role : SemanticColor.values()) foreign.remove(custom.semantic().color(role).getRGB() | 0xFF000000);
        foreign.remove(0xFF000000);
        foreign.remove(0xFFFFFFFF);
        return foreign;
    }

    /** The opaque colours that fill at least one 2 × 2 block of {@code image}: painted areas, not antialiased edges. */
    static Set<Integer> areas(BufferedImage image) {
        var colours = new HashSet<Integer>();
        for (int y = 0; y + 1 < image.getHeight(); y++) for (int x = 0; x + 1 < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) == 0xFF && image.getRGB(x + 1, y) == argb && image.getRGB(x, y + 1) == argb
                    && image.getRGB(x + 1, y + 1) == argb) colours.add(argb);
        }
        return colours;
    }

    /** The opaque colours in {@code image}. */
    static Set<Integer> colours(BufferedImage image) {
        var colours = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) == 0xFF) colours.add(argb);
        }
        return colours;
    }

    /** Spec 16.1.3: the gallery and the Enterprise fixtures render under the Enterprise fixture kit over the theme set. */
    @Test void everyFixtureRendersUnderTheEnterpriseKitOverTheThemeSet() {
        var themes = new ArrayList<>(KitFixtures.themes());
        themes.add(KitFixtures.enterpriseLight());
        var fixtures = KitGallery.enterpriseCatalog();
        for (var theme : themes) {
            var captures = captures(theme, KitFixtures.enterprise(), fixtures);
            assertThat(captures).hasSameSizeAs(fixtures);
            for (var capture : captures)
                assertThat(colours(capture.image())).as("%s %s: %s", theme.name(), theme.dark() ? "Dark" : "Light", capture.fixture().name())
                    .hasSizeGreaterThan(1);
        }
    }

    /** The checked-in covering array's rows, each the 14 control points' classic flags in spec 5.2's order. */
    static List<boolean[]> coveringArray() {
        try (var in = KitMatrixTest.class.getResourceAsStream("control-covering-array.tsv")) {
            var rows = new ArrayList<boolean[]>();
            for (var line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                var cells = line.split("\t");
                var row = new boolean[cells.length];
                for (int i = 0; i < cells.length; i++) row[i] = cells[i].equals("1");
                rows.add(row);
            }
            return rows;
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Rulings 19: every pair of control points shows all four value combinations in the seven rows. */
    @Test void theCoveringArrayHasStrengthTwoOverTheFourteenControlPoints() {
        var rows = coveringArray();
        assertThat(rows).hasSize(7).allSatisfy(row -> assertThat(row).hasSize(14));
        for (int a = 0; a < 14; a++) for (int b = a + 1; b < 14; b++) {
            var seen = new HashSet<String>();
            for (var row : rows) seen.add((row[a] ? "1" : "0") + (row[b] ? "1" : "0"));
            assertThat(seen).as("points %d and %d", a, b).containsExactlyInAnyOrder("00", "01", "10", "11");
        }
    }

    /** Spec 16.1.3: the complement kit and the seven covering-array kits over Slate Cobalt Dark and Light, Latte, marked() and custom(). */
    @Test void theComplementAndTheCoveringArrayRenderOverFivePalettes() {
        var latte = KitFixtures.themes().stream().filter(t -> t.name().equals("Catppuccin Latte")).findFirst().orElseThrow();
        var palettes = List.of(KitFixtures.dark(), KitFixtures.light(), latte, KitFixtures.marked(), KitFixtures.custom());
        var kits = new ArrayList<Kit>(List.of(KitFixtures.complement()));
        for (var row : coveringArray()) kits.add(KitFixtures.withControls(KitFixtures.enterprise(), ControlVariants.of(row)));
        assertThat(kits.getFirst().controls()).isEqualTo(KitFixtures.enterprise().controls().complement());
        var fixtures = KitGallery.enterpriseCatalog();
        for (var kit : kits) for (var theme : palettes) {
            var captures = captures(theme, kit, fixtures);
            assertThat(captures).hasSameSizeAs(fixtures);
            for (var capture : captures)
                assertThat(colours(capture.image())).as("%s under %s: %s", theme.name(), kit.controls().keys(), capture.fixture().name()).hasSizeGreaterThan(1);
        }
    }

    /** Rulings 18: under marked(), every one-colour 3 x 3 block of a classic surface is a role or a blend of two. */
    @Test void underMarkedEveryClassicAreaIsARoleOrABlendOfTwo() {
        var theme = KitFixtures.marked();
        var kit = KitFixtures.enterprise();
        var allowed = new ArrayList<java.awt.Color>();
        for (var role : SemanticColor.values()) allowed.add(theme.semantic().color(role));
        for (var role : DerivedColor.values()) allowed.add(role.color(theme));
        for (var pair : KitFaces.declared(kit)) allowed.add(SurfaceContrast.painted(theme, pair));
        allowed.add(java.awt.Color.BLACK);
        allowed.add(java.awt.Color.WHITE);
        var found = new ArrayList<String>();
        for (var capture : captures(theme, kit, EnterpriseFixtures.create()))
            blocks(capture.image(), 3).forEach((rgb, at) -> {
                if (!blend(rgb, allowed)) found.add(capture.fixture().name() + " #" + Integer.toHexString(rgb & 0xFFFFFF) + " at " + at);
            });
        assertThat(found).as("classic areas in no role and no blend of two").isEmpty();
    }

    /** Spec 16.1.3: under custom(), the Enterprise kit paints no area in a colour only another theme has. */
    @Test void underCustomTheEnterpriseKitPaintsNoForeignColour() {
        var foreign = foreign(KitFixtures.custom());
        var found = new ArrayList<String>();
        for (var capture : captures(KitFixtures.custom(), KitFixtures.enterprise(), KitGallery.enterpriseCatalog())) {
            var painted = areas(capture.image());
            painted.retainAll(foreign);
            for (int rgb : painted) found.add(capture.fixture().name() + " #" + Integer.toHexString(rgb & 0xFFFFFF));
        }
        assertThat(found).as("pixels in another theme's colours under custom() and the Enterprise kit").isEmpty();
    }

    @Test void aBlendOfTwoAllowedColoursIsAllowedAndAThirdColourIsNot() {
        var allowed = List.of(new java.awt.Color(0x000000), new java.awt.Color(0xFFFFFF), new java.awt.Color(0x2F65CA));
        assertThat(blend(0x808080, allowed)).isTrue();
        assertThat(blend(0x97B2E4, allowed)).as("accent halfway to white").isTrue();
        assertThat(blend(0xC03030, allowed)).isFalse();
    }

    /** The opaque colours that fill an n x n block of {@code image}, each with its first block's position. */
    static java.util.Map<Integer, String> blocks(BufferedImage image, int n) {
        var found = new java.util.LinkedHashMap<Integer, String>();
        for (int y = 0; y + n <= image.getHeight(); y++) for (int x = 0; x + n <= image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) != 0xFF || found.containsKey(argb)) continue;
            boolean block = true;
            for (int dy = 0; block && dy < n; dy++) for (int dx = 0; block && dx < n; dx++) block = image.getRGB(x + dx, y + dy) == argb;
            if (block) found.put(argb, x + "," + y);
        }
        return found;
    }

    /** Whether {@code rgb} lies within 3 levels of one of {@code colours} or of the segment between two of them. */
    static boolean blend(int rgb, List<java.awt.Color> colours) {
        double r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < colours.size(); i++) for (int j = i; j < colours.size(); j++) {
            var p = colours.get(i); var q = colours.get(j);
            double dr = q.getRed() - p.getRed(), dg = q.getGreen() - p.getGreen(), db = q.getBlue() - p.getBlue();
            double length = dr * dr + dg * dg + db * db;
            double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((r - p.getRed()) * dr + (g - p.getGreen()) * dg + (b - p.getBlue()) * db) / length));
            if (Math.abs(r - (p.getRed() + t * dr)) <= 3 && Math.abs(g - (p.getGreen() + t * dg)) <= 3 && Math.abs(b - (p.getBlue() + t * db)) <= 3) return true;
        }
        return false;
    }
}
