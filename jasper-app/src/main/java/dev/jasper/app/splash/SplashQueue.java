package dev.jasper.app.splash;

import dev.jasper.app.startup.StartupSteps;
import java.util.List;

/** Deterministic latest-step transition state; the splash paint lock owns access. */
final class SplashQueue {
    private static final long DURATION = 240_000_000L;
    private List<String> labels;
    private int index = 1, previous;
    private long began;

    record Frame(int index, int previousIndex, double progress) {}

    SplashQueue(List<String> labels) {
        this.labels = List.copyOf(labels);
        if (labels.isEmpty()) throw new IllegalArgumentException("Empty splash queue");
    }

    void show(StartupSteps.Step step, long nowNanos, boolean animations) {
        if (!step.queue().isEmpty() && !step.queue().equals(labels)) {
            labels = step.queue();
            index = step.index();
            previous = 0;
            began = nowNanos;
            return;
        }
        if (step.index() <= index) return;
        if (step.index() > labels.size()) throw new IllegalArgumentException("Step outside queue");
        previous = animations ? index : 0;
        index = step.index();
        began = nowNanos;
    }

    List<String> labels() { return labels; }

    Frame frame(long nowNanos) {
        double elapsed = Math.clamp((nowNanos - began) / (double) DURATION, 0, 1);
        if (elapsed >= 1) previous = 0;
        return new Frame(index, previous, previous == 0 ? 1 : elapsed);
    }
}
