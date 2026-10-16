package dev.jasper.ui.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
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
    static List<KitGallery.Capture> captures(GeneratedTheme theme, Kit kit) {
        var captures = new ArrayList<KitGallery.Capture>();
        Edt.run(() -> KitGallery.installed(theme, kit, (context, focus) -> {
            for (var fixture : KitGallery.fixtures()) captures.add(KitGallery.capture(fixture, context, focus));
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
}
