package dev.jasper.app.application;

import dev.jasper.app.presentation.Motion;
import dev.jasper.app.presentation.Projections;
import java.util.Objects;

/**
 * The application's one animations supplier (Enterprise presentation spec 10.9): motion runs only when the look moves
 * and the user's setting allows it. The only reader of {@link Motion}; the setting itself is never changed.
 *
 * @param motion the look's motion
 */
record LookMotion(Motion motion) {
    /** The default look: the setting decides. */
    static final LookMotion STANDARD = new LookMotion(Motion.STANDARD);

    /** The motion is required. */
    LookMotion { Objects.requireNonNull(motion, "motion"); }

    /** The look of {@code projections}. */
    static LookMotion of(Projections projections) { return new LookMotion(projections.motion()); }

    /** Whether anything may move, given the user's {@code setting}. */
    boolean allows(boolean setting) { return motion == Motion.STANDARD && setting; }
}
