package dev.jasper.app.plugins;

import dev.jasper.ui.swing.KitOwnership;
import javax.swing.*;

/** Provenance-aware boundary diagnostics. Never records descriptor/input/exception contents. */
final class KitDiagnostics {
    private KitDiagnostics() {}
    static void failure(Containment containment,String owner) { containment.record(owner,"kit callback",new IllegalStateException("Plugin kit callback failed")); }
    static void validate(dev.jasper.sdk.ui.CustomContent content) {
        java.awt.Component root=content.component();
        if(root instanceof JScrollPane scroll)root=scroll.getViewport().getView();
        if(root instanceof JComponent component && KitOwnership.owns(component)) throw new IllegalArgumentException("Kit-owned components cannot be registered as plugin content");
        boolean editor=root instanceof JTextArea || root instanceof JTextPane || root instanceof JEditorPane;
        boolean canvas=root instanceof JComponent && !(root instanceof JPanel) && !(root instanceof javax.swing.text.JTextComponent) && !(root instanceof AbstractButton) && !(root instanceof JLabel) && !(root instanceof JList) && !(root instanceof JTable) && !(root instanceof JTree) && !(root instanceof JComboBox) && !(root instanceof JSpinner) && !(root instanceof JSlider);
        if(content.kind()==dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR ? !(editor || canvas) : !canvas)
            throw new IllegalArgumentException("Ordinary plugin controls require kit descriptors");
    }
}
