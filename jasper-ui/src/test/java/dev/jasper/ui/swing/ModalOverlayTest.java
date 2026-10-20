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
 * logical into place over 220 ms with an ease-out cubic, the scrim dimming with it; closing is the opposite over
 * 200 ms, the card lifting off and rising another 16 (ease-out) as it fades (ease-in) and the scrim lifts. Never a
 * slide across the window; instant with motion off. Frames are driven by a test clock.
 */
class ModalOverlayTest {
    static final long MS = 1_000_000;

    static final class Fixture implements AutoCloseable {
        final AtomicBoolean motion = new AtomicBoolean(true);
        final AtomicLong now = new AtomicLong(1_000 * MS);
        final KitContext base = KitFixtures.context();
        final KitContext context = KitContext.of(base.kit(), base.binding(), base.route(), base.icons(), motion::get, action -> "");
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
        /** Opens and draws the first frame, which starts the entrance's clock. */
        void open() { overlay.open(); paint(); }
        /** Starts the exit and draws its first frame, which starts its clock. */
        void dismiss(Runnable removed) { overlay.dismiss(removed); paint(); }
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
            f.open();
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
            assertThat(dimmed.getAlpha()).as("the scrim dims everything else at its own alpha").isEqualTo(scrim.getAlpha());
            assertThat(new int[] {dimmed.getRed(), dimmed.getGreen(), dimmed.getBlue()}).as("in the theme's scrim colour")
                .satisfies(rgb -> { assertThat(rgb[0]).isCloseTo(scrim.getRed(), within(1)); assertThat(rgb[1]).isCloseTo(scrim.getGreen(), within(1));
                    assertThat(rgb[2]).isCloseTo(scrim.getBlue(), within(1)); });
        } });
    }

    /**
     * The exit is the entrance's opposite: the card lifts off at once and keeps rising (easing out) while it is still
     * clearly visible, and fades (easing in) as it goes. Sharing one ease-in, it barely moved before it was gone.
     */
    @Test void closingLiftsOffWhileStillVisibleFadesOutAndRemovesTheOverlayWhenDone() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.open(); f.at(220);
            assertThat(f.context.metrics().value(DesignMetrics.Role.OVERLAY_EXIT_MILLIS)).isBetween(150, 200);
            int[] removed = {0};
            f.dismiss(() -> removed[0]++);
            assertThat(f.overlay.active()).isFalse();
            assertThat(f.overlay.card().isVisible()).as("the leaving card takes no input").isFalse();
            f.at(40);
            assertThat(f.overlay.cardBounds().y).as("a fifth of the way through, already rising").isLessThanOrEqualTo(f.restingY() - 7);
            assertThat(f.overlay.shown()).as("and still nearly opaque").isGreaterThan(.95);
            f.at(60);
            assertThat(f.overlay.shown()).isEqualTo(.75);
            assertThat(f.overlay.cardBounds().y).as("half way: most of the rise done").isEqualTo(f.restingY() - 14);
            // Its picture at .75 over the scrim and shadow, themselves at .75 of their strength.
            // .75 + .25 x .41: only the scrim (black 55% x .75) lies under the card; the dialog shadow stays outside it, as
            // box-shadow does, so the fading card does not darken.
            assertThat(f.paint().getRGB(400, 300) >>> 24).as("its picture fades over the scrim alone").isBetween(213, 222);
            assertThat(removed[0]).isZero();
            f.at(100);
            assertThat(removed[0]).isEqualTo(1);
            assertThat(f.overlay.getParent()).isNull();
            assertThat(f.overlay.phase()).isEqualTo(ModalOverlay.Phase.CLOSED);
        } });
    }

    /**
     * The entrance's clock starts at the first frame drawn: a new form's layout and the card's snapshot come before it,
     * and an ease-out spends most of its motion early, so starting at open() showed a card already nearly in place.
     */
    @Test void theClockStartsAtTheFirstFrameDrawnSoNoneOfTheEntranceIsLost() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.overlay.open();
            f.at(100);
            assertThat(f.overlay.shown()).as("nothing drawn yet, nothing spent").isZero();
            assertThat(f.overlay.cardBounds().y).isEqualTo(f.restingY() + 16);
            f.paint();
            f.at(110);
            assertThat(f.overlay.shown()).isEqualTo(.875);
        } });
    }

    /** A window that never draws (hidden, or headless) still runs its transitions, a little late. */
    @Test void withoutAFrameDrawnTheClockStartsAfterAShortWait() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.overlay.open();
            f.at(130);
            assertThat(f.overlay.shown()).isZero();
            f.at(110);
            assertThat(f.overlay.shown()).isEqualTo(.875);
            f.at(110);
            assertThat(f.overlay.phase()).isEqualTo(ModalOverlay.Phase.OPEN);
        } });
    }

    /**
     * The dialog's content goes as soon as it closes; the leaving card keeps its size, so its picture rises and fades
     * whole. Re-measured each frame it shrank to a dot at the centre, its shadow a small dark circle.
     */
    @Test void theLeavingCardKeepsItsSizeWhenItsContentIsGone() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.open(); f.at(220);
            f.dismiss(() -> { });
            f.overlay.card().removeAll();
            f.overlay.card().revalidate();
            f.at(100);
            assertThat(f.overlay.cardBounds().getSize()).isEqualTo(new Dimension(402, 192));
            assertThat(f.overlay.cardBounds().y).isEqualTo(f.restingY() - 14);
            var image = f.paint();
            assertThat(new Color(image.getRGB(400, f.overlay.cardBounds().y + 96), true).getRed()).as("the content's picture, fading").isGreaterThan(150);
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
            f.open(); f.at(50);
            f.motion.set(false); f.at(10);
            assertThat(f.overlay.phase()).isEqualTo(ModalOverlay.Phase.OPEN);
            assertThat(f.overlay.cardBounds().y).isEqualTo(f.restingY());
        } });
    }

    /** Dismissed while still rising in, it carries on upward from where it was as it fades out. */
    @Test void closingDuringTheEntranceCarriesOnUpwardFromWhereItWas() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.open(); f.at(110);
            f.dismiss(() -> { });
            assertThat(f.overlay.shown()).isEqualTo(.875);
            int previous = f.overlay.cardBounds().y;
            assertThat(previous).isEqualTo(f.restingY() + 2);
            for (int frame = 0; frame < 11; frame++) {
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
            f.open(); f.at(220);
            f.dismiss(f.overlay::close); f.at(200);
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
