package dev.jasper.app.contributions;

import java.util.Objects;
import java.util.OptionalDouble;

/** Native, atomic presentation of a contributed progress control. */
public record ProgressState(String text, String detail, String accessibleDescription,
                            OptionalDouble fraction, String actionId, String secondaryActionId) {
    public ProgressState {
        Objects.requireNonNull(text); Objects.requireNonNull(detail); Objects.requireNonNull(accessibleDescription);
        Objects.requireNonNull(fraction);
        if (fraction.isPresent() && (!Double.isFinite(fraction.getAsDouble())
                || fraction.getAsDouble() < 0 || fraction.getAsDouble() > 1))
            throw new IllegalArgumentException("Progress must be between zero and one");
    }
}
