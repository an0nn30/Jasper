package dev.jasper.ui.swing;

import java.util.List;
import java.util.Objects;
import javax.swing.Icon;

/**
 * Host-owned metadata for a borrowed kit panel header: a detail (a count or connection name) after the title and
 * count, an optional connected status dot before it, and icon-only actions placed after the panel's own header
 * actions and before the host's placement/collapse actions. Module/app integration only; not projected into the SDK.
 * Raw artwork belongs to the host, which keeps it readable on the panel surface; optional immutable monochrome
 * artwork delegates tint and canonical compact optics to the receiving view.
 *
 * @param detail text after the title and count; empty to omit
 * @param active whether a connected status dot precedes the detail
 * @param actions icon-only actions, in order
 * @param frame how the host frames the header: as the view draws it, or as a flush frame's header strip
 */
public record PanelHostHeader(String detail, boolean active, List<Action> actions, Frame frame) {
    /** No detail, no dot and no actions. */
    public static final PanelHostHeader EMPTY = new PanelHostHeader("", false, List.of());

    /**
     * How the host frames a borrowed header. {@code FLUSH} and {@code FLUSH_FOCUSED} make it a header strip (Enterprise
     * presentation spec 10.7): the strip's surface over a 1 px rule, a parenthesised count and digits-only detail, a muted
     * detail and actions 2 px apart; focused while the frame holds the focus owner.
     */
    public enum Frame { STANDARD, FLUSH, FLUSH_FOCUSED }

    /** Creates an immutable description. */
    public PanelHostHeader {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(frame, "frame");
        actions = List.copyOf(actions);
    }

    /** A description the view draws as it does without a host frame. */
    public PanelHostHeader(String detail, boolean active, List<Action> actions) { this(detail, active, actions, Frame.STANDARD); }

    /** Immutable monochrome vector identity. A null path selects canonical engine artwork; custom paths retain their authored stroke.
     * @param name canonical or namespaced icon name
     * @param pathData constrained vector path, or null for canonical artwork
     */
    public record Artwork(String name, String pathData) {
        /** Creates an immutable artwork description; the receiving registry validates its path. */
        public Artwork { Objects.requireNonNull(name, "name"); }
    }

    /**
     * One icon-only action. The callback runs on the EDT, once per activation; failures are reported by the view.
     *
     * @param label accessible name and tooltip
     * @param icon host-supplied artwork
     * @param invoke callback
     * @param artwork optional immutable monochrome identity, validated and owned by the receiving view
     */
    public record Action(String label, Icon icon, Runnable invoke, Artwork artwork) {
        /** Preserves arbitrary host artwork without semantic recoloring. */
        public Action(String label, Icon icon, Runnable invoke) { this(label, icon, invoke, null); }
        /** Creates an action with a nonblank label. */
        public Action {
            if (Objects.requireNonNull(label, "label").isBlank()) throw new IllegalArgumentException("Blank action label");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(invoke, "invoke");
        }
    }
}
