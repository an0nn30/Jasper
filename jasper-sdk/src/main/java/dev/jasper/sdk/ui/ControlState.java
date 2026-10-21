package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * Presentation state; error carries validation prose, never secret content. A hidden, disabled or busy node also
 * disables every node inside it. Enter in a {@link Nodes.Form} does not submit while a visible, enabled node in it is
 * busy or has an error.
 *
 * @param visible false hides the node and everything in it
 * @param enabled false disables the node and everything in it, so it reports no events
 * @param busy true while the node's work runs: it is disabled as if {@code enabled} were false
 * @param error validation prose, empty for none: a field, secret or choice shows it below itself and a toggle as its
 *     tooltip; each of them, and a button, also gives it as its accessible description. A PREVIEWS choice, and a SLIDER
 *     field where the look draws a slider (as the default look does), neither show it nor describe it
 */
public record ControlState(boolean visible, boolean enabled, boolean busy, String error) {
    /** Visible, enabled, not busy and without an error. */
    public static final ControlState READY = new ControlState(true, true, false, "");
    public ControlState { Objects.requireNonNull(error); }
}
