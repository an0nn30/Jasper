package dev.jasper.app.appearance;

import java.util.Objects;
import java.util.Optional;

/**
 * A theme's {@code [workspace]} table as written. Empty values inherit through {@code extends}; still empty, they mean
 * opaque and unblurred.
 *
 * @param opacity the authored opacity, 0.1–1
 * @param blur the authored blur, 0–1
 */
public record WorkspaceGlass(Optional<Double> opacity, Optional<Double> blur) {
    /** No {@code [workspace]} table. */
    public static final WorkspaceGlass NONE = new WorkspaceGlass(Optional.empty(), Optional.empty());

    public WorkspaceGlass { Objects.requireNonNull(opacity); Objects.requireNonNull(blur); }

    /** This table laid over {@code base}: each value this table sets wins. */
    WorkspaceGlass over(WorkspaceGlass base) { return new WorkspaceGlass(opacity.or(base::opacity), blur.or(base::blur)); }

    /** The values a generated theme uses. */
    public GlassLevels levels() { return new GlassLevels(opacity.orElse(1.0), blur.orElse(0.0)); }

    /** Whether the table sets nothing. */
    public boolean isEmpty() { return opacity.isEmpty() && blur.isEmpty(); }
}
