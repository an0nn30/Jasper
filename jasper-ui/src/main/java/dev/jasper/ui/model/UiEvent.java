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
    /** Expansion intent: id is the tree owner, itemId is local to that tree. */
    record Expanded(String id, String itemId, boolean expanded) implements UiEvent { public Expanded { ModelChecks.id(id); ModelChecks.id(itemId); } }
    /** Ordered-list removal intent; the domain owner confirms removal through an updated view. */
    record Removed(String id, String itemId) implements UiEvent { public Removed { ModelChecks.id(id); ModelChecks.id(itemId); } }
    record Reordered(String id, String itemId, int destination) implements UiEvent {
        public Reordered { ModelChecks.id(id); ModelChecks.id(itemId); if (destination < 0) throw new IllegalArgumentException("Negative destination"); }
    }
    record SecretChanged(String id) implements UiEvent { public SecretChanged { ModelChecks.id(id); } }
    /**
     * A plain left single click on a table or tree row, reported after its {@link Selected}: id is the owner, itemId
     * the row. Keys, right clicks and the second click of a double click never report it; Enter and double clicks
     * report the owner's activate action instead.
     */
    record Clicked(String id, String itemId) implements UiEvent { public Clicked { ModelChecks.id(id); ModelChecks.id(itemId); } }
    /** Sort intent: id is the table owner, columnId the clicked column header; the host owns the order. */
    record Sorted(String id, String columnId) implements UiEvent { public Sorted { ModelChecks.id(id); ModelChecks.id(columnId); } }
}
