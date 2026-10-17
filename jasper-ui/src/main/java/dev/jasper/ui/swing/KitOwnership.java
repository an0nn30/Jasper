package dev.jasper.ui.swing;

import javax.swing.JComponent;

/** Kit provenance for diagnostics; metadata does not transfer ownership to callers. */
public final class KitOwnership {
    private static final String KEY = "Jasper.kit.node";
    /**
     * Client property on a row collection's or breadcrumbs' mounted component, or on a setting row's while it has a
     * menu, holding its context menu, so a host (or a headless test) can find the items a right click built. The menu
     * stays kit-owned.
     */
    public static final String CONTEXT_MENU = "Jasper.kit.contextMenu";
    private KitOwnership() {}
    static void mark(JComponent component, String id) { component.putClientProperty(KEY, id); component.setName(id); }
    static void clear(JComponent component) { component.putClientProperty(KEY,null); component.setName(null); }
    /** True for a component created and owned by the kit. */
    public static boolean owns(JComponent component) { return component.getClientProperty(KEY) instanceof String; }
}
