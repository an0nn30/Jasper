package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The modal overlay's motion, as the user specified it: the card fades from transparent to opaque while rising 16
 * logical into place over 220 ms with an ease-out cubic, the scrim dimming with it; closing is the opposite, the card
 * rising on another 16 as it fades out and the scrim lifting, over 170 ms with an ease-in. Never a slide across the
 * window; instant with motion off. Frames are driven by a test clock.
 */
class ModalOverlayTest {
    static final long MS = 1_000_000;

    static final class Fixture implements AutoCloseable {
        final AtomicBoolean motion = new AtomicBoolean(true);
        final AtomicLong now = new AtomicLong(1_000 * MS);
        final KitContext base = KitFixtures.context();
        final KitContext context = new KitContext(base.binding(), base.route(), base.metrics(), base.icons(), motion::get);
        final JPanel content = new JPanel();
        final JLayeredPane pane = new JLayeredPane();
        final ModalOverlay overlay;
        Fixture() {
            content.setBackground(Color.RED); content.setOpaque(true); content.setPreferredSize(new Dimension(400, 190));
            overlay = new ModalOverlay(context, content, now::get);
            pane.setSize(800, 600);
            pane.add(overlay, JLayeredPane.MODAL_LAYER);
            overlay.setBounds(0, 0, 800, 600);
        }
        void at(long millis) { now.addAndGet(millis * MS); overlay.tick(); }
        /** The card's top when in place: centred in the window less the margin. */
        int restingY() { return 16 + (600 - 32 - 192) / 2; }
        BufferedImage paint() {
            var image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { overlay.paint(g); } finally { g.dispose(); }
            return image;
        }
        @Override public void close() { overlay.close(); release(base); }
    }

    @Test void easingIsCubicOutForTheEntranceAndCubicInForTheExit() {
        assertThat(ModalOverlay.easeOutCubic(0)).isZero();
        assertThat(ModalOverlay.easeOutCubic(1)).isEqualTo(1);
        assertThat(ModalOverlay.easeOutCubic(.5)).isEqualTo(.875);
        assertThat(ModalOverlay.easeInCubic(.5)).isEqualTo(.125);
        double previous = 0;
        for (int step = 1; step <= 100; step++) {
            double eased = ModalOverlay.easeOutCubic(step / 100.0);
            assertThat(eased).as("decelerates smoothly, never backwards").isGreaterThanOrEqualTo(previous);
            previous = eased;
        }
    }

    @Test void theCardFadesUpSixteenIntoPlaceOverTheEntranceAsTheScrimDims() {
        Edt.run(() -> { try (var f = new Fixture()) {
            assertThat(f.context.metrics().value(DesignMetrics.Role.OVERLAY_ENTER_MILLIS)).isBetween(200, 250);
            f.overlay.open();
            assertThat(f.overlay.shown()).as("starts transparent").isZero();
            assertThat(f.overlay.cardBounds()).isEqualTo(new Rectangle(199, f.restingY() + 16, 402, 192));
            assertThat(f.paint().getRGB(20, 20) >>> 24).as("no scrim yet").isZero();
            f.at(110);
            assertThat(f.overlay.shown()).isEqualTo(.875);
            assertThat(f.overlay.cardBounds().y).as("an eighth of the rise to go").isEqualTo(f.restingY() + 2);
            var card = new BufferedImage(402, 192, BufferedImage.TYPE_INT_ARGB);
            var g = card.createGraphics();
            try { f.overlay.card().paint(g); } finally { g.dispose(); }
            assertThat(new Color(card.getRGB(200, 96), true)).as("the card fades as one surface: its content at .875")
                .isEqualTo(new Color(255, 0, 0, 223));
            f.at(110);
            assertThat(f.overlay.phase()).isEqualTo(ModalOverlay.Phase.OPEN);
            assertThat(f.overlay.cardBounds()).isEqualTo(new Rectangle(199, f.restingY(), 402, 192));
            var image = f.paint();
            assertThat(image.getRGB(400, 300)).as("the live content").isEqualTo(Color.RED.getRGB());
            Color scrim = f.context.theme().semantic().color(SemanticColor.SURFACE_SCRIM);
            var dimmed = new Color(image.getRGB(20, 20), true);
            assertThat(dimmed.getAlpha()).as("the scrim dims everything else at 40%").isEqualTo(102);
            assertThat(new int[] {dimmed.getRed(), dimmed.getGreen(), dimmed.getBlue()}).as("in the theme's scrim colour")
                .satisfies(rgb -> { assertThat(rgb[0]).isCloseTo(scrim.getRed(), within(1)); assertThat(rgb[1]).isCloseTo(scrim.getGreen(), within(1));
                    assertThat(rgb[2]).isCloseTo(scrim.getBlue(), within(1)); });
        } });
    }

    /** The exit is the entrance's opposite: the card keeps rising as it fades out, never sinking back down. */
    @Test void closingKeepsRisingAsItFadesOutFasterAndRemovesTheOverlayWhenDone() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.overlay.open(); f.at(220);
            assertThat(f.context.metrics().value(DesignMetrics.Role.OVERLAY_EXIT_MILLIS)).isBetween(150, 200);
            int[] removed = {0};
            f.overlay.dismiss(() -> removed[0]++);
            assertThat(f.overlay.active()).isFalse();
            assertThat(f.overlay.card().isVisible()).as("the leaving card takes no input").isFalse();
            f.at(85);
            assertThat(f.overlay.shown()).isEqualTo(.875);
            assertThat(f.overlay.cardBounds().y).as("an eighth of the way up and out").isEqualTo(f.restingY() - 2);
            // Its picture at .875 over the scrim and shadow, themselves at .875 of their strength.
            assertThat(f.paint().getRGB(400, 300) >>> 24).as("its picture fades").isBetween(232, 244);
            f.at(68);
            assertThat(f.overlay.cardBounds().y).as("near the end, most of a rise above its place").isLessThan(f.restingY() - 10);
            assertThat(removed[0]).isZero();
            f.at(17);
            assertThat(removed[0]).isEqualTo(1);
            assertThat(f.overlay.getParent()).isNull();
            assertThat(f.overlay.phase()).isEqualTo(ModalOverlay.Phase.CLOSED);
        } });
    }

    @Test void withMotionOffItAppearsAndLeavesAtOnce() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.motion.set(false);
            f.overlay.open();
            assertThat(f.overlay.phase()).isEqualTo(ModalOverlay.Phase.OPEN);
            assertThat(f.overlay.cardBounds().y).isEqualTo(f.restingY());
            boolean[] removed = {false};
            f.overlay.dismiss(() -> removed[0] = true);
            assertThat(removed[0]).isTrue();
            assertThat(f.overlay.getParent()).isNull();
        } });
    }

    @Test void turningMotionOffMidwaySettlesInPlace() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.overlay.open(); f.at(50);
            f.motion.set(false); f.at(10);
            assertThat(f.overlay.phase()).isEqualTo(ModalOverlay.Phase.OPEN);
            assertThat(f.overlay.cardBounds().y).isEqualTo(f.restingY());
        } });
    }

    /** Dismissed while still rising in, it carries on upward from where it was as it fades out. */
    @Test void closingDuringTheEntranceCarriesOnUpwardFromWhereItWas() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.overlay.open(); f.at(110);
            f.overlay.dismiss(() -> { });
            assertThat(f.overlay.shown()).isEqualTo(.875);
            int previous = f.overlay.cardBounds().y;
            assertThat(previous).isEqualTo(f.restingY() + 2);
            for (int frame = 0; frame < 9; frame++) {
                f.at(17);
                int y = f.overlay.cardBounds().y;
                assertThat(y).as("frame %s never moves down", frame).isLessThanOrEqualTo(previous);
                previous = y;
            }
            assertThat(previous).as("above its resting place by the end").isLessThan(f.restingY() - 10);
            f.at(17);
            assertThat(f.overlay.getParent()).isNull();
        } });
    }

    /** The host closes the overlay once its exit ends; that still releases its theme subscription. */
    @Test void closingAfterAnAnimatedExitReleasesTheThemeSubscription() throws ReflectiveOperationException {
        var field = dev.jasper.ui.theme.ThemeBinding.class.getDeclaredField("listeners");
        field.setAccessible(true);
        Edt.run(() -> { try (var f = new Fixture()) {
            int before;
            try { before = ((java.util.List<?>) field.get(f.context.binding())).size(); }
            catch (IllegalAccessException e) { throw new AssertionError(e); }
            f.overlay.open(); f.at(220);
            f.overlay.dismiss(f.overlay::close); f.at(170);
            assertThat(f.overlay.getParent()).isNull();
            try { assertThat(((java.util.List<?>) field.get(f.context.binding())).size()).isEqualTo(before - 1); }
            catch (IllegalAccessException e) { throw new AssertionError(e); }
        } });
    }

    @Test void theScrimTakesPointerEventsOutsideTheCard() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.overlay.open();
            var press = new MouseEvent(f.overlay, MouseEvent.MOUSE_PRESSED, 0, 0, 5, 5, 1, false);
            f.overlay.dispatchEvent(press);
            assertThat(press.isConsumed()).isTrue();
        } });
    }
}
