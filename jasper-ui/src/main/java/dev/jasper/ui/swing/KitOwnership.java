package dev.jasper.ui.swing;

import javax.swing.JComponent;

/** Kit provenance for diagnostics; metadata does not transfer ownership to callers. */
public final class KitOwnership {
    private static final String KEY = "Jasper.kit.node";
    private KitOwnership() {}
    static void mark(JComponent component, String id) { component.putClientProperty(KEY, id); component.setName(id); }
    /** True for a component created and owned by the kit. */
    public static boolean owns(JComponent component) { return component.getClientProperty(KEY) instanceof String; }
}
