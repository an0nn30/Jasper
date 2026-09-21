package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.animation.BubbleSpring;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyColumnPlacementTest {
    private final Rectangle screen = new Rectangle(0, 0, 1200, 1000);
    private final Dimension size = new Dimension(394, 152);
    private final BuddyColumnPlacement motion = new BuddyColumnPlacement();

    @Test void ordinaryDraggingTracksTheAnchorWithoutLag() {
        Rectangle anchor = new Rectangle(500, 700, 84, 96);
        assertThat(motion.update(anchor, size, screen, 0, true)).isFalse();
        Point before = motion.at(0);
        anchor.translate(15, -20);
        motion.update(anchor, size, screen, 20_000_000, false);
        before.translate(15, -20);
        assertThat(motion.at(20_000_000)).isEqualTo(before);
        assertThat(motion.moving(20_000_000)).isFalse();
    }

    @Test void midpointFlipStartsContinuouslyAndSettlesOnTheOtherSide() {
        Rectangle anchor = new Rectangle(500, 480, 84, 96);
        motion.update(anchor, size, screen, 0, true);
        Point before = motion.at(0);
        anchor.translate(0, -80);
        assertThat(motion.update(anchor, size, screen, 10_000_000, false)).isTrue();
        before.translate(0, -80);
        assertThat(motion.at(10_000_000)).isEqualTo(before);
        assertThat(motion.at(160_000_000).y).isGreaterThan(before.y);
        assertThat(motion.at(810_000_000)).isEqualTo(BuddyColumnWindow.place(anchor, size, screen, true));
        assertThat(motion.moving(810_000_000)).isFalse();
    }

    @Test void interruptedFlipKeepsItsPositionAndReshowSettlesImmediately() {
        Rectangle anchor = new Rectangle(500, 700, 84, 96);
        motion.update(anchor, size, screen, 0, true);
        anchor.y = 300; motion.update(anchor, size, screen, 20_000_000, false);
        Point before = motion.at(120_000_000);
        anchor.y += 300;
        motion.update(anchor, size, screen, 120_000_000, false);
        before.y += 300;
        assertThat(motion.at(120_000_000)).isEqualTo(before);
        motion.update(anchor, size, screen, 140_000_000, true);
        assertThat(motion.at(140_000_000)).isEqualTo(BuddyColumnWindow.place(anchor, size, screen, false));
        assertThat(motion.moving(140_000_000)).isFalse();
    }

    @Test void everyFrameStaysInsideOffsetMonitorBoundsEvenDuringOvershoot() {
        Rectangle monitor = new Rectangle(-1200, -200, 1200, 1000);
        for (int frame = 0; frame < 240; frame++) {
            long now = frame * 1_000_000_000L / 60;
            Rectangle anchor = new Rectangle(-700 + (int) (650 * Math.sin(frame * .06)),
                200 + (int) (500 * Math.cos(frame * .04)), 84, 96);
            motion.update(anchor, size, monitor, now, frame == 0);
            assertThat(monitor.contains(new Rectangle(motion.at(now), size))).isTrue();
        }
    }

    @Test void reversingTheSpringPreservesVelocityAsWellAsPosition() {
        var spring = new BubbleSpring(0);
        spring.target(300, 0, false);
        long now = 170_000_000;
        double before = spring.at(now), velocity = (spring.at(now) - spring.at(now - 1000)) * 1e6;
        spring.target(-100, now, false);
        assertThat(spring.at(now)).isEqualTo(before);
        double afterVelocity = (spring.at(now + 1000) - spring.at(now)) * 1e6;
        assertThat(afterVelocity).isCloseTo(velocity, org.assertj.core.data.Offset.offset(.1));
    }

    @Test void midpointDeadBandDoesNotOscillateBetweenSides() {
        Rectangle anchor = new Rectangle(500, 447, 84, 96);
        assertThat(motion.update(anchor, size, screen, 0, true)).isTrue();
        anchor.y += 10;
        assertThat(motion.update(anchor, size, screen, 20_000_000, false)).isTrue();
        anchor.y += 10;
        assertThat(motion.update(anchor, size, screen, 40_000_000, false)).isFalse();
    }
}
