package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;
import javax.swing.Icon;

/** Per-window panel header metadata. Set through {@link PanelHost#setHeader} on the UI thread.
 * @param detail count or connection name beside the title; empty to omit
 * @param active whether to show a connected status dot beside the detail
 * @param actions icon-only actions before the host's options and hide controls
 * @since 0.14.2
 */
public record PanelHeader(String detail, boolean active, List<Action> actions) {
    /** A header without contributed metadata or actions. */
    public static final PanelHeader EMPTY = new PanelHeader("", false, List.of());
    /** Creates an immutable header description. */
    public PanelHeader {
        Objects.requireNonNull(detail, "detail");
        actions = List.copyOf(actions);
    }
    /** One accessible icon action. Callbacks run on the UI thread and are contained by the host.
     * @param label accessible name and tooltip
     * @param icon action artwork
     * @param invoke action callback
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
