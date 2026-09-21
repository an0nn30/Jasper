package dev.jasper.app.restart;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * What a restart needs to know about a resident Jasper process. Both calls block on a socket and
 * belong off the EDT.
 *
 * @param live whether some process holds the handoff endpoint right now
 * @param retire asks that process to quit; true when it accepted
 */
public record ResidentControl(BooleanSupplier live, BooleanSupplier retire) {
    /** No endpoint to speak of: tests, and platforms without one. */
    public static final ResidentControl NONE = new ResidentControl(() -> false, () -> false);

    /** Rejects nulls. */
    public ResidentControl {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(retire, "retire");
    }
}
