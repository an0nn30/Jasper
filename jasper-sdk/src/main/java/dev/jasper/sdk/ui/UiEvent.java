package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Set;

/**
 * Typed user intent. Action ids are configured action keys. All payload-bearing events identify
 * the owning node; consumers resolve its configured action through the current ViewSpec.
 * Choice option/item ids are local to that node. Secret edits carry only the secret node id.
 * Events arrive on the UI thread through the handler given to {@link UiKit#create(ViewSpec, java.util.function.Consumer)},
 * and stop once the view is closed.
 */
public sealed interface UiEvent {
    /**
     * The node that reports the event; for an {@link Action}, the configured action key instead.
     *
     * @return the owning node id, or an action's key
     */
    String id();
    /**
     * A configured action: a button, a menu item, a row activation, Enter, Escape or another trigger reports the action
     * key its node names for it.
     *
     * @param id the configured action key, such as a {@link Nodes.Button}'s or a {@link MenuItem}'s action; not blank
     */
    record Action(String id) implements UiEvent { public Action { ModelChecks.id(id); } }
    /**
     * Text the user changed, reported as it changes: a {@link Nodes.Field}, a SEARCHABLE {@link Nodes.Choice} with free
     * entry, a {@link Nodes.Breadcrumbs} field while editing, or a {@link Nodes.PickerMenu}'s query.
     *
     * @param id the edited node
     * @param text the node's whole current text
     */
    record Edited(String id, String text) implements UiEvent {
        public Edited { ModelChecks.id(id); Objects.requireNonNull(text); }
    }
    /**
     * A new selection: the option of a {@link Nodes.Choice}, the keyboard-active row of a {@link Nodes.PickerMenu}, the
     * clicked crumb of {@link Nodes.Breadcrumbs}, or the selected rows of a {@link Nodes.Table}, {@link Nodes.Tree} or
     * {@link Nodes.ItemList}.
     *
     * @param id the owning node
     * @param ids the node's whole selection after the change, as option, row, item or crumb ids local to that node; it
     *     may be empty, and holds several rows under multiple selection
     */
    record Selected(String id, Set<String> ids) implements UiEvent {
        public Selected { ModelChecks.id(id); ids = Set.copyOf(ids); ids.forEach(ModelChecks::id); }
    }
    /**
     * A {@link Nodes.Toggle} the user switched; Space on a list row whose trailing slot holds exactly one switch switches
     * it too. The switch shows the new state only once an update confirms it.
     *
     * @param id the toggle node
     * @param checked the state the user asked for
     */
    record Toggled(String id, boolean checked) implements UiEvent { public Toggled { ModelChecks.id(id); } }
    /**
     * Expansion intent from a branch's disclosure or the Right and Left arrow keys: id is the tree owner, itemId is local
     * to that tree. The item opens or closes only when an update adds it to or removes it from the tree's
     * {@code expandedIds}.
     *
     * @param id the {@link Nodes.Tree} node
     * @param itemId the branch item, local to that tree
     * @param expanded true to expand the item, false to collapse it
     */
    record Expanded(String id, String itemId, boolean expanded) implements UiEvent { public Expanded { ModelChecks.id(id); ModelChecks.id(itemId); } }
    /**
     * Ordered-list removal intent from an ORDERED {@link Nodes.ItemList}'s Remove command: a row's own or, in a look that
     * gives the list a side column of commands, the column's Remove acting on the selected row (a list whose selection
     * mode is NONE keeps the commands on its rows). The domain owner confirms removal through an updated view.
     *
     * @param id the list node
     * @param itemId the item to remove, local to that list
     */
    record Removed(String id, String itemId) implements UiEvent { public Removed { ModelChecks.id(id); ModelChecks.id(itemId); } }
    /**
     * Ordered-list move intent from an ORDERED {@link Nodes.ItemList}'s Move up or Move down command: a row's own or,
     * in a look that gives the list a side column of commands, the column's Move Up or Move Down acting on the selected
     * row. The domain owner applies the move through an updated view.
     *
     * @param id the list node
     * @param itemId the item to move, local to that list
     * @param destination the index the item moves to among the list's current items, counted from 0; never negative
     */
    record Reordered(String id, String itemId, int destination) implements UiEvent {
        public Reordered { ModelChecks.id(id); ModelChecks.id(itemId); if (destination < 0) throw new IllegalArgumentException("Negative destination"); }
    }
    /**
     * The content of a {@link Nodes.Secret} changed. The event carries no text: read the value with
     * {@link UiView#withSecret(String, java.util.function.Consumer)}.
     *
     * @param id the secret node
     */
    record SecretChanged(String id) implements UiEvent { public SecretChanged { ModelChecks.id(id); } }
    /**
     * A plain left single click on a table, tree or item-list row (not a RESULTS list, a list or table whose selection
     * mode is NONE, or a heading), reported after its {@link Selected}: id is the owner, itemId the row. Keys, right
     * clicks, clicks on a row's own buttons or switches and the second click of a double click never report it; Enter
     * and double clicks report the owner's activate (or a list's Enter) action instead.
     *
     * @param id the table, tree or list node
     * @param itemId the clicked row, local to that node
     */
    record Clicked(String id, String itemId) implements UiEvent { public Clicked { ModelChecks.id(id); ModelChecks.id(itemId); } }
    /**
     * Sort intent from a left click on a column header: id is the table or tree owner, columnId the clicked column. The
     * view's owner orders the rows and marks the column with {@link Column#sorted()}.
     *
     * @param id the {@link Nodes.Table} or {@link Nodes.Tree} node
     * @param columnId the clicked column's id
     */
    record Sorted(String id, String columnId) implements UiEvent { public Sorted { ModelChecks.id(id); ModelChecks.id(columnId); } }
}
