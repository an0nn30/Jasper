package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.*;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** spec 8: shadows are black at theme.md's strengths, never theme colours. */
class ShadowsTest {
    @Test void aSoftShadowReachesItsStrengthUnderTheFaceAndFadesOutward() {
        var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { ThemePainting.softShadow(g, new Rectangle2D.Double(50, 50, 100, 100), 8, 10, 20, .5); } finally { g.dispose(); }
        var under = new Color(image.getRGB(100, 110), true);
        assertThat(under.getRed()).isZero();
        assertThat(under.getAlpha()).as("full strength under the face").isBetween(120, 135);
        var edge = new Color(image.getRGB(100, 160 + 15), true);
        assertThat(edge.getAlpha()).as("fading toward the blur's reach").isBetween(1, 120);
        assertThat(new Color(image.getRGB(5, 5), true).getAlpha()).as("nothing beyond the blur").isZero();
    }

    @Test void popoverShadowKeysCarryTheVariantsStrength() {
        var dark = dev.jasper.ui.theme.SlateSteel.bundled().generated(true).uiKeys();
        var light = dev.jasper.ui.theme.SlateSteel.bundled().generated(false).uiKeys();
        assertThat(dark).containsEntry("Popup.dropShadowColor", "#000000").containsEntry("Popup.dropShadowOpacity", .5);
        assertThat(light).containsEntry("Popup.dropShadowOpacity", .18);
    }

    @Test void aShadowFollowsTheCompositeSoAFadingCardsShadowFadesWithIt() {
        var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive(.5f));
            ThemePainting.softShadow(g, new Rectangle2D.Double(50, 50, 100, 100), 8, 10, 20, .5);
        } finally { g.dispose(); }
        assertThat(new Color(image.getRGB(100, 110), true).getAlpha()).isBetween(60, 68);
    }

    /** A pixel flattened over white: smaller is darker, whether or not the view paints a background there. */
    static int flat(int argb) {
        var c = new Color(argb, true);
        return (int) Math.round(c.getRed() * c.getAlpha() / 255.0 + 255 * (1 - c.getAlpha() / 255.0));
    }

    /** theme.md's primary drop lies below the face, as box-shadow does: the face keeps its calibrated extent. */
    @Test void aPrimaryButtonsDropLiesBelowItsFaceWhichKeepsItsCalibratedExtent() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, KitViewTest.stack(new dev.jasper.ui.model.Nodes.Button("go", dev.jasper.ui.model.ControlState.READY,
                    "Continue", "", dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY, "go", "")), e -> {})) {
                var image = KitFixtures.render(view, 300, 120, 1);
                var button = KitViewTest.find(view, "go", javax.swing.JButton.class);
                var bottom = javax.swing.SwingUtilities.convertPoint(button, button.getWidth() / 2, button.getHeight() - 1, view);
                assertThat(image.getRGB(bottom.x, bottom.y)).as("the face reaches its bottom row")
                    .isEqualTo(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT).getRGB());
                assertThat(flat(image.getRGB(bottom.x, bottom.y + 1))).as("the drop darkens the row below the face")
                    .isLessThan(flat(image.getRGB(bottom.x, bottom.y + 8)));
            } finally { KitViewTest.release(context); }
        });
    }
}
