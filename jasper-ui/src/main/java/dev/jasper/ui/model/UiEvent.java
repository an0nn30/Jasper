package dev.jasper.ui.model;

import java.util.Objects;
import java.util.Set;

/**
 * Typed user intent. Action ids are configured action keys. All payload-bearing events identify
 * the owning node; consumers resolve its configured action through the current ViewSpec.
 * Choice option/item ids are local to that node. Secret edits carry only the secret node id.
 */
public sealed interface UiEvent {
    String id();
    record Action(String id) implements UiEvent { public Action { ModelChecks.id(id); } }
    record Edited(String id, String text) implements UiEvent {
        public Edited { ModelChecks.id(id); Objects.requireNonNull(text); }
    }
    record Selected(String id, Set<String> ids) implements UiEvent {
        public Selected { ModelChecks.id(id); ids = Set.copyOf(ids); ids.forEach(ModelChecks::id); }
    }
    record Toggled(String id, boolean checked) implements UiEvent { public Toggled { ModelChecks.id(id); } }
    record Expanded(String id, boolean expanded) implements UiEvent { public Expanded { ModelChecks.id(id); } }
    record Reordered(String id, String itemId, int destination) implements UiEvent {
        public Reordered { ModelChecks.id(id); ModelChecks.id(itemId); if (destination < 0) throw new IllegalArgumentException("Negative destination"); }
    }
    record SecretChanged(String id) implements UiEvent { public SecretChanged { ModelChecks.id(id); } }
}
