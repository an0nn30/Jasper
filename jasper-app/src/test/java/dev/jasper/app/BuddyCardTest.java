package dev.jasper.app;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyCardTest {
    private final JComponent owner = new JComponent() {};

    private static BuddyNotice notice(BuddyNotice.State state, String detail) {
        return new BuddyNotice("terminal", "k", BuddyNotice.Kind.TASK, "./gradlew build", state,
            () -> detail, () -> { });
    }

    private void paint(BuddyNotice notice, boolean dismissable, int count) {
        Rectangle bounds = new Rectangle(0, 0, BuddyCard.WIDTH, BuddyCard.height(owner));
        BufferedImage image = new BufferedImage(bounds.width + 40, bounds.height + 40,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            BuddyCard.paint(g, owner, notice, bounds, false, 1f, 1f, dismissable, count);
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
            BuddyNotice any = new BuddyNotice("s", "k", kind, "title", state, () -> "detail", () -> { });
            Rectangle bounds = new Rectangle(0, 0, BuddyCard.WIDTH, BuddyCard.height(owner));
            BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try { BuddyCard.paint(g, owner, any, bounds, true, 1.04f, 1f, true, 0); } finally { g.dispose(); }
        }
    }

    /** A producer's supplier is arbitrary code; a broken one must not take the surface down. */
    @Test void aDetailSupplierThatThrowsIsSurvived() {
        assertThat(BuddyCard.detailOf(new BuddyNotice("s", "k", BuddyNotice.Kind.TASK, "t",
            BuddyNotice.State.RUNNING, () -> { throw new IllegalStateException("boom"); }, () -> { })))
            .isEmpty();
        assertThat(BuddyCard.detailOf(notice(BuddyNotice.State.DONE, null))).isEmpty();
        assertThat(BuddyCard.detailOf(notice(BuddyNotice.State.DONE, "Finished in 3s")))
            .isEqualTo("Finished in 3s");
    }

    @Test void paintingIsSafeWithADismissTargetAndWithACount() {
        paint(notice(BuddyNotice.State.DONE, "Finished in 3s"), true, 0);
        paint(notice(BuddyNotice.State.RUNNING, "Running · 3s"), false, 7);
    }
}
