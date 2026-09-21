package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyCardTest {
    private final JComponent owner = new JComponent() {};

    private static BuddyNotice notice(BuddyNotice.State state, String detail) {
        return new BuddyNotice(new BuddyNoticeId("terminal", "k"), BuddyNotice.Kind.TASK, "./gradlew build", state, () -> detail, () -> { });
    }

    private void paint(BuddyNotice notice, boolean dismissable, int count) {
        Rectangle bounds = new Rectangle(0, 0, BuddyCard.WIDTH, BuddyCard.height(owner));
        BufferedImage image = new BufferedImage(bounds.width + 40, bounds.height + 40,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            BuddyCard.paint(BuddyTestAppearance.options(), g, owner, notice, bounds, 0f, 1f, dismissable, count);
        } finally { g.dispose(); }
    }

    @Test void aCardIsTallEnoughForBothItsLines() {
        int height = BuddyCard.height(owner);

        assertThat(height).isGreaterThan(2 * BuddyCard.PAD_Y + BuddyCard.GLYPH_SIZE);
        assertThat(height).isEqualTo(BuddyCard.height(owner));
    }

    @Test void everyStatePaintsWithoutBlowingUp() {
        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            BuddyNotice.Kind kind = state.fits(BuddyNotice.Kind.TASK)
                ? BuddyNotice.Kind.TASK : BuddyNotice.Kind.CONNECTION;
            BuddyNotice any = new BuddyNotice(new BuddyNoticeId("s", "k"), kind, "title", state, () -> "detail", () -> { });
            Rectangle bounds = new Rectangle(0, 0, BuddyCard.WIDTH, BuddyCard.height(owner));
            BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try { BuddyCard.paint(BuddyTestAppearance.options(), g, owner, any, bounds, 1f, 1f, true, 0); } finally { g.dispose(); }
        }
    }

    /** A producer's supplier is arbitrary code; a broken one must not take the surface down. */
    @Test void aDetailSupplierThatThrowsIsSurvived() {
        assertThat(BuddyCard.detailOf(new BuddyNotice(new BuddyNoticeId("s", "k"), BuddyNotice.Kind.TASK, "t", BuddyNotice.State.RUNNING, () -> { throw new IllegalStateException("boom"); }, () -> { })))
            .isEmpty();
        assertThat(BuddyCard.detailOf(notice(BuddyNotice.State.DONE, null))).isEmpty();
        assertThat(BuddyCard.detailOf(notice(BuddyNotice.State.DONE, "Finished in 3s")))
            .isEqualTo("Finished in 3s");
    }

    @Test void paintingIsSafeWithADismissTargetAndWithACount() {
        paint(notice(BuddyNotice.State.DONE, "Finished in 3s"), true, 0);
        paint(notice(BuddyNotice.State.RUNNING, "Running · 3s"), false, 7);
    }
    @Test void bothThemesMatchTheRecordedMaterialAndRemainTranslucent() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var previous = javax.swing.UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[]{true, false}) {
                    var options = BuddyTestAppearance.options(dark);
                    BufferedImage layer = new BufferedImage(420, 150, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = layer.createGraphics();
                    try {
                        BuddyCard.paint(options, g, owner, notice(BuddyNotice.State.RUNNING, "Thinking"),
                            new Rectangle(40, 40, 330, 56), 0, 1, false, 0);
                    } finally { g.dispose(); }
                    var fill = new java.awt.Color(layer.getRGB(330, 65), true);
                    assertThat(fill.getAlpha()).as("actual desktop translucency").isBetween(215, 240);
                    BufferedImage composed = new BufferedImage(420, 150, BufferedImage.TYPE_INT_RGB);
                    Graphics2D composite = composed.createGraphics();
                    try {
                        composite.setColor(new java.awt.Color(253, 253, 253));
                        composite.fillRect(0, 0, 420, 150);
                        composite.drawImage(layer, 0, 0, null);
                    } finally { composite.dispose(); }
                    int expected = dark ? 65 : 249;
                    assertThat(new java.awt.Color(composed.getRGB(330, 65)).getRed())
                        .as("recorded fill over reference white").isBetween(expected - 1, expected + 1);
                    assertThat((layer.getRGB(41, 41) >>> 24)).as("rounded corner is outside the material").isLessThan(20);
                    assertThat((layer.getRGB(200, 108) >>> 24)).as("soft shadow outside the body").isBetween(1, 30);
                }
            } finally {
                try { javax.swing.UIManager.setLookAndFeel(previous); }
                catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

}
