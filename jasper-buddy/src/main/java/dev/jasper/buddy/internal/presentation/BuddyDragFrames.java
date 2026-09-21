package dev.jasper.buddy.internal.presentation;

import java.awt.Point;
import java.util.function.Consumer;
import javax.swing.Timer;

/** EDT frame clock: pointer bursts replace the pending position instead of queueing native moves. */
final class BuddyDragFrames {
    private final Consumer<Point> present;
    private final Timer timer = new Timer(16, event -> frame());
    private Point latest;

    BuddyDragFrames(Consumer<Point> present) {
        this.present = present;
        timer.setCoalesce(true);
        timer.setInitialDelay(0);
    }

    void offer(Point point) {
        latest = new Point(point);
        if (!timer.isRunning()) timer.start();
    }

    // Keep ticking while held: a flip and shimmer must settle even if the pointer pauses.
    void frame() { if (latest != null) present.accept(new Point(latest)); }

    void finish() { frame(); cancel(); }
    void cancel() { timer.stop(); latest = null; }
    boolean running() { return timer.isRunning(); }
}
