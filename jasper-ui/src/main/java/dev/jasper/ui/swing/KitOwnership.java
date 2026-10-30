package dev.jasper.ui.swing;

import javax.swing.JComponent;

/** Kit provenance for diagnostics; metadata does not transfer ownership to callers. */
public final class KitOwnership {
    private static final String KEY = "Jasper.kit.node";
    /**
     * Client property on a row collection's or breadcrumbs' mounted component, or on a setting row's while it has a
     * menu, holding its context menu, so a host (or a headless test) can find the items a right click built; and on a
     * button while it has a hold menu, holding that menu. The menu stays kit-owned.
     */
    public static final String CONTEXT_MENU = "Jasper.kit.contextMenu";
    /**
     * Client property on a field's input while its list of suggestions shows, holding that list (a {@code JList} whose
     * rows are the field's {@code Nodes.Suggestion} values, then the loading line's text while more load), so a host (or
     * a headless test) can read what the list shows. The list stays kit-owned.
     */
    public static final String SUGGESTIONS = "Jasper.kit.suggestions";
    private KitOwnership() {}
    static void mark(JComponent component, String id) { component.putClientProperty(KEY, id); component.setName(id); }
    static void clear(JComponent component) { component.putClientProperty(KEY,null); component.setName(null); }
    /** True for a component created and owned by the kit. */
    public static boolean owns(JComponent component) { return component.getClientProperty(KEY) instanceof String; }
}
