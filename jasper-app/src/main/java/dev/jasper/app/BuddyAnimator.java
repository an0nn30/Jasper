package dev.jasper.app;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Clock-driven idle/hover frame selection; owns no timers and touches no Swing. */
final class BuddyAnimator {
    static final long BLINK_MIN_NANOS = TimeUnit.SECONDS.toNanos(3);
    static final long BLINK_MAX_NANOS = TimeUnit.SECONDS.toNanos(6);
    static final long BLINK_NANOS = TimeUnit.MILLISECONDS.toNanos(120);
    static final long STEP_NANOS = TimeUnit.MILLISECONDS.toNanos(125);
    static final List<BuddyFrame> DANCE = List.of(BuddyFrame.WAVE_A, BuddyFrame.WAVE_B, BuddyFrame.HOP,
        BuddyFrame.LEAN_LEFT, BuddyFrame.LEAN_RIGHT);

    private enum Mode { HIDDEN, IDLE, BLINKING, DANCING }

    private final Random random;
    private Mode mode = Mode.HIDDEN;
    private BuddyFrame frame = BuddyFrame.IDLE;
    private boolean hovered;
    private int step;
    private long due;

    BuddyAnimator(Random random) { this.random = Objects.requireNonNull(random, "random"); }

    BuddyFrame frame() { return frame; }

    OptionalLong nextDueNanos() { return mode == Mode.HIDDEN ? OptionalLong.empty() : OptionalLong.of(due); }

    void shown(long now) {
        mode = Mode.IDLE; frame = BuddyFrame.IDLE; hovered = false;
        scheduleBlink(now);
    }

    void hidden() {
        mode = Mode.HIDDEN; frame = BuddyFrame.IDLE; hovered = false; step = 0;
    }

    void hoverEntered(long now) {
        hovered = true;
        if (mode == Mode.IDLE || mode == Mode.BLINKING) startDance(now);
    }

    void hoverExited(long now) { hovered = false; }

    /** Applies every step whose due time has passed; a late tick never skips a dance frame. */
    void tick(long now) {
        for (int guard = 0; guard < 64 && mode != Mode.HIDDEN && now >= due; guard++) advance();
    }

    private void advance() {
        switch (mode) {
            case IDLE -> {
                frame = random.nextInt(4) == 0 ? BuddyFrame.WINK : BuddyFrame.BLINK;
                mode = Mode.BLINKING; due += BLINK_NANOS;
            }
            case BLINKING -> { frame = BuddyFrame.IDLE; mode = Mode.IDLE; scheduleBlink(due); }
            case DANCING -> {
                step++;
                if (step >= DANCE.size()) {
                    if (!hovered) { frame = BuddyFrame.IDLE; mode = Mode.IDLE; scheduleBlink(due); return; }
                    step = 0;
                }
                frame = DANCE.get(step); due += STEP_NANOS;
            }
            case HIDDEN -> { }
        }
    }

    private void startDance(long now) {
        mode = Mode.DANCING; step = 0; frame = DANCE.getFirst(); due = now + STEP_NANOS;
    }

    private void scheduleBlink(long from) {
        due = from + BLINK_MIN_NANOS + (long) (random.nextDouble() * (BLINK_MAX_NANOS - BLINK_MIN_NANOS));
    }
}
