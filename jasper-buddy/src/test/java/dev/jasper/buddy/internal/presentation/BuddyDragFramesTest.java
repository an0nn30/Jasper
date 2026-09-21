package dev.jasper.buddy.internal.presentation;

import java.awt.Point;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyDragFramesTest {
    @Test void pointerBurstsPresentOnlyTheNewestPositionAndFinishFlushesIt() throws Exception {
        BuddyTestAppearance.edt(() -> {
            var presented = new ArrayList<Point>();
            var frames = new BuddyDragFrames(presented::add);
            try {
                for (int i = 0; i < 200; i++) frames.offer(new Point(i, i * 2));
                assertThat(presented).isEmpty();
                frames.frame();
                assertThat(presented).containsExactly(new Point(199, 398));
                frames.offer(new Point(300, 400));
                frames.finish();
                assertThat(presented).containsExactly(new Point(199, 398), new Point(300, 400));
                assertThat(frames.running()).isFalse();
                frames.frame();
                assertThat(presented).hasSize(2);
            } finally { frames.cancel(); }
        });
    }

    @Test void pausedPointerStillAdvancesAnimationAndCancellationDropsPendingMoves() throws Exception {
        BuddyTestAppearance.edt(() -> {
            var presented = new ArrayList<Point>();
            var frames = new BuddyDragFrames(presented::add);
            try {
                Point pointer = new Point(10, 20);
                frames.offer(pointer); pointer.translate(900, 900);
                frames.frame(); frames.frame();
                assertThat(presented).containsExactly(new Point(10, 20), new Point(10, 20));
                frames.offer(new Point(30, 40)); frames.cancel(); frames.frame();
                assertThat(frames.running()).isFalse();
                assertThat(presented).hasSize(2);
            } finally { frames.cancel(); }
        });
    }
}
