package dev.jasper.ui.model;

import java.util.Objects;

/** Presentation state; error carries validation prose, never secret content. */
public record ControlState(boolean visible, boolean enabled, boolean busy, String error) {
    public static final ControlState READY = new ControlState(true, true, false, "");
    public ControlState { Objects.requireNonNull(error); }
}
