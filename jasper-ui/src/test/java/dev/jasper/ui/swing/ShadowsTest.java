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

    /** A palette whose height follows its results must not blur a new mask on the EDT for every height. */
    @Test void oneMaskServesEveryFaceSizeWithTheSameTreatment() {
        int before = ThemePainting.shadowMasks();
        var image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { for (int i = 0; i < 20; i++) ThemePainting.softShadow(g, new Rectangle2D.Double(60, 60, 700 + i, 300 + 7 * i), 6, 12, 32, .5); }
        finally { g.dispose(); }
        assertThat(ThemePainting.shadowMasks() - before).as("masks blurred for twenty face sizes").isLessThanOrEqualTo(1);
    }

    /** Faces of different sizes share their corners and edge profiles, so a sliced shadow is seamless. */
    @Test void aLargerFacesShadowHasTheSameCornersAndEdgesAsASmallerOnesShadow() {
        BufferedImage[] images = new BufferedImage[2];
        for (int k = 0; k < 2; k++) {
            images[k] = new BufferedImage(800, 900, BufferedImage.TYPE_INT_ARGB);
            var g = images[k].createGraphics();
            try { ThemePainting.softShadow(g, new Rectangle2D.Double(200, 100, 400, k == 0 ? 300 : 600), 12, 30, 80, .58); } finally { g.dispose(); }
        }
        for (int d = 1; d < 120; d += 7) {
            assertThat(images[1].getRGB(400, 100 + 600 + 30 + d)).as("below the face, %s px", d).isEqualTo(images[0].getRGB(400, 100 + 300 + 30 + d));
            assertThat(images[1].getRGB(200 - d, 100 + 30 + 20)).as("the top-left corner, %s px out", d).isEqualTo(images[0].getRGB(200 - d, 100 + 30 + 20));
            assertThat(images[1].getRGB(200 - d, 100 + 600 + 30 - 20)).as("the bottom-left corner, %s px out", d).isEqualTo(images[0].getRGB(200 - d, 100 + 300 + 30 - 20));
        }
    }

    /** The drop lies outside the button, so a change to the button must repaint its view there, as the focus halo does. */
    @Test void aPrimaryButtonsDropIsRepaintedWhenTheButtonIsDisabledHiddenOrRemoved() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var previous = javax.swing.RepaintManager.currentManager((javax.swing.JComponent) null);
            var dirty = new java.util.ArrayList<java.util.Map.Entry<javax.swing.JComponent, java.awt.Rectangle>>();
            try (var view = new KitView(context, KitViewTest.stack(new dev.jasper.ui.model.Nodes.Button("go", dev.jasper.ui.model.ControlState.READY,
                    "Continue", "", dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY, "go", "")), e -> {})) {
                KitFixtures.render(view, 300, 120, 1);
                var button = KitViewTest.find(view, "go", javax.swing.JButton.class);
                var below = javax.swing.SwingUtilities.convertPoint(button, button.getWidth() / 2, button.getHeight() + 1, view);
                javax.swing.RepaintManager.setCurrentManager(new javax.swing.RepaintManager() {
                    @Override public void addDirtyRegion(javax.swing.JComponent c, int x, int y, int w, int h) {
                        dirty.add(java.util.Map.entry(c, new java.awt.Rectangle(x, y, w, h)));
                    }
                });
                java.util.function.Supplier<Boolean> repaintsTheDrop = () -> dirty.stream().anyMatch(entry -> entry.getKey() == view && entry.getValue().contains(below));
                button.setEnabled(false);
                assertThat(repaintsTheDrop.get()).as("disabling repaints the drop's strip below the face").isTrue();
                dirty.clear(); button.setVisible(false);
                assertThat(repaintsTheDrop.get()).as("hiding repaints it").isTrue();
                button.setVisible(true); KitFixtures.render(view, 300, 120, 1);
                dirty.clear(); button.getParent().remove(button);
                assertThat(repaintsTheDrop.get()).as("removing repaints it").isTrue();
            } finally { javax.swing.RepaintManager.setCurrentManager(previous); KitViewTest.release(context); }
        });
    }
}
