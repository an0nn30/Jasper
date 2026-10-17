package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 8a: the Run window's frame and bands, its field box and its list box. */
class RunWindowSurfacesTest {
    static BufferedImage paint(SurfacePanel panel, int width, int height) {
        panel.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y)); }

    @Test void theRunWindowIsAFramedDialogWithATitleBandAndAFooterBand() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var theme = context.theme();
            var window = new SurfacePanel(context, SurfacePanel.Kind.RUN_WINDOW, () -> 24, () -> 40);
            var image = paint(window, 462, 427);
            assertThat(window.getInsets()).isEqualTo(new java.awt.Insets(1, 1, 1, 1));
            assertThat(at(image, 200, 0)).as("the frame").isEqualTo(DerivedColor.BORDER_HOVER.color(theme));
            assertThat(at(image, 0, 200)).isEqualTo(DerivedColor.BORDER_HOVER.color(theme));
            assertThat(at(image, 200, 12)).as("the title band").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
            assertThat(at(image, 200, 25)).as("its rule").isEqualTo(theme.semantic().color(SemanticColor.BORDER_WINDOW));
            assertThat(at(image, 200, 200)).as("the dialog surface").isEqualTo(theme.semantic().color(context.kit().colors().dialogSurface()));
            assertThat(at(image, 200, 427 - 1 - 40 - 1)).as("the footer's rule").isEqualTo(theme.semantic().color(SemanticColor.BORDER_WINDOW));
            assertThat(at(image, 200, 427 - 20)).as("the footer band").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
            assertThat(image.getRGB(0, 0) >>> 24).as("a rounded corner").isLessThan(255);
            assertThat(window.contains(1, 1)).as("the whole rectangle takes presses").isTrue();
        });
    }

    @Test void theFieldBoxShowsFocusWithAnAccentEdgeAndAHalo() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var palette = context.theme().semantic();
            var field = new SurfacePanel(context, SurfacePanel.Kind.FIELD);
            int ring = context.metrics().value(DesignMetrics.Role.FOCUS_RING);
            assertThat(field.getInsets()).isEqualTo(new java.awt.Insets(ring + 1, ring + 1, ring + 1, ring + 1));
            field.focusShown(false);
            var idle = paint(field, 392, 26);
            assertThat(at(idle, ring, 13)).as("the idle edge").isEqualTo(palette.color(SemanticColor.BORDER_INPUT));
            assertThat(at(idle, 100, 13)).isEqualTo(palette.color(SemanticColor.SURFACE_INPUT));
            assertThat(idle.getRGB(0, 13) >>> 24).as("no halo").isZero();
            field.focusShown(true);
            var focused = paint(field, 392, 26);
            assertThat(at(focused, ring, 13)).as("the focused edge").isEqualTo(palette.color(SemanticColor.BORDER_FOCUS));
            var halo = new Color(focused.getRGB(0, 13), true);
            var expected = ColorMath.opacity(palette.color(SemanticColor.BORDER_FOCUS), ThemePainting.focusHaloShare(context, false));
            // A translucent fill stored unpremultiplied rounds its channels: the alpha is exact, each channel within two levels.
            assertThat(halo.getAlpha()).as("the halo's share").isEqualTo(expected.getAlpha());
            assertThat(List.of(halo.getRed() - expected.getRed(), halo.getGreen() - expected.getGreen(), halo.getBlue() - expected.getBlue()))
                .as("the halo's colour").allMatch(difference -> Math.abs(difference) <= 2);
            field.focusShown(null);
            assertThat(field.focusWithin()).as("nothing focused headless").isFalse();
        });
    }

    @Test void theListBoxIsACardInsideAControlEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var palette = context.theme().semantic();
            var box = new SurfacePanel(context, SurfacePanel.Kind.BOX);
            var image = paint(box, 436, 208);
            assertThat(box.getInsets()).isEqualTo(new java.awt.Insets(1, 1, 1, 1));
            assertThat(at(image, 0, 0)).as("square").isEqualTo(palette.color(SemanticColor.BORDER_CONTROL));
            assertThat(at(image, 435, 100)).isEqualTo(palette.color(SemanticColor.BORDER_CONTROL));
            assertThat(at(image, 100, 100)).isEqualTo(palette.color(SemanticColor.SURFACE_CARD));
        });
    }
}
