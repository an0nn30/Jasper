package dev.jasper.ui.swing;

import java.util.List;
import java.util.Objects;
import javax.swing.Icon;

/**
 * Host-owned metadata for a borrowed kit panel header: a detail (a count or connection name) after the title and
 * count, an optional connected status dot before it, and icon-only actions placed after the panel's own header
 * actions and before the host's placement/collapse actions. Module/app integration only; not projected into the SDK.
 * The actions' artwork belongs to the host, which keeps it readable on the panel surface.
 *
 * @param detail text after the title and count; empty to omit
 * @param active whether a connected status dot precedes the detail
 * @param actions icon-only actions, in order
 */
public record PanelHostHeader(String detail, boolean active, List<Action> actions) {
    /** No detail, no dot and no actions. */
    public static final PanelHostHeader EMPTY = new PanelHostHeader("", false, List.of());

    /** Creates an immutable description. */
    public PanelHostHeader {
        Objects.requireNonNull(detail, "detail");
        actions = List.copyOf(actions);
    }

    /**
     * One icon-only action. The callback runs on the EDT, once per activation; failures are reported by the view.
     *
     * @param label accessible name and tooltip
     * @param icon host-supplied artwork
     * @param invoke callback
     */
    public record Action(String label, Icon icon, Runnable invoke) {
        /** Creates an action with a nonblank label. */
        public Action {
            if (Objects.requireNonNull(label, "label").isBlank()) throw new IllegalArgumentException("Blank action label");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(invoke, "invoke");
        }
    }
}
