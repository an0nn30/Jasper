package dev.jasper.app.settingsui;

import dev.jasper.ui.model.Nodes;

/** Owner-package access to ResetMenuTest's descriptor search for other packages' tests (plan 9b Task 4). */
public final class ResetMenuTestAccess {
    private ResetMenuTestAccess() { }

    /** The node {@code id} under {@code node}, or null. */
    public static Nodes.Node find(Nodes.Node node, String id) { return ResetMenuTest.find(node, id); }
}
