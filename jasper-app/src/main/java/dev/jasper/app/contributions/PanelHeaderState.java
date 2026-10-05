package dev.jasper.app.contributions;

import java.util.List;
import java.util.Objects;
import javax.swing.Icon;

/** Application-owned, per-window tool header presentation. EDT only. */
public record PanelHeaderState(String detail, boolean active, List<Action> actions) {
    public static final PanelHeaderState EMPTY = new PanelHeaderState("", false, List.of());
    public PanelHeaderState { Objects.requireNonNull(detail); actions = List.copyOf(actions); }
    public record Action(String label, Icon icon, Runnable invoke) {
        public Action { Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(invoke); }
    }
}
