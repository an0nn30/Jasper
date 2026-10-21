package dev.jasper.sdk.ui;

import java.util.List;

/**
 * One breadcrumb: a literal label and its own context menu (empty for none). Ids are local to the owning node.
 *
 * @param id the crumb's id, unique among the {@link Nodes.Breadcrumbs}' crumbs; a click reports it as
 *     {@link UiEvent.Selected}
 * @param label the crumb's literal text and accessible name; not blank
 * @param contextActions the menu a right click on the crumb opens, with unique ids; choosing an item reports only its
 *     {@link UiEvent.Action} and does not select the crumb
 */
public record Crumb(String id, String label, List<MenuItem> contextActions) {
    public Crumb {
        ModelChecks.id(id); ModelChecks.label(label, "label");
        contextActions = List.copyOf(contextActions); ModelChecks.unique(contextActions, MenuItem::id);
    }
}
