package dev.jasper.ui.model;

import java.util.List;

/** One breadcrumb: a literal label and its own context menu (empty for none). Ids are local to the owning node. */
public record Crumb(String id, String label, List<MenuItem> contextActions) {
    public Crumb {
        ModelChecks.id(id); ModelChecks.label(label, "label");
        contextActions = List.copyOf(contextActions); ModelChecks.unique(contextActions, MenuItem::id);
    }
}
