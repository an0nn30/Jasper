package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import javax.swing.JComponent;

/** Stable node ids select mounted instances; incompatible kinds and removed nodes dispose once. */
final class ViewReconciler implements AutoCloseable {
    private final KitView view;
    private Map<String, ControlRegistry.Control> mounted = new LinkedHashMap<>();
    ViewReconciler(KitView view) { this.view = view; }
    void update(ViewSpec spec) {
        ControlRegistry.validate(spec.root());
        var nodes = new LinkedHashMap<String, Nodes.Node>(); collect(spec.root(), nodes);
        var next = new LinkedHashMap<String, ControlRegistry.Control>(); var created = new ArrayList<ControlRegistry.Control>();
        try {
            for (var node : nodes.values()) {
                var old = mounted.get(node.id());
                if (old != null && ControlRegistry.kind(old.node).equals(ControlRegistry.kind(node))) next.put(node.id(), old);
                else { var control = ControlRegistry.create(view, node); created.add(control); next.put(node.id(), control); }
            }
        } catch (Throwable failure) { created.forEach(ControlRegistry.Control::close); throw failure; }
        inherit(spec.root(), next, true);
        for (var node : nodes.values()) next.get(node.id()).update(node);
        detachMoves(spec.root(), nodes, next);
        for (var node : nodes.values()) if (node instanceof Nodes.Stack stack) {
            ((Stacks.StackControl)next.get(node.id())).children(stack.children().stream().map(child -> next.get(child.id()).component).toList());
        }
        JComponent root = next.get(spec.root().id()).component;
        if (root.getParent() != view) { view.removeAll(); view.add(root, java.awt.BorderLayout.CENTER); }
        for (var old : mounted.values()) if (next.get(old.node.id()) != old) old.close();
        mounted = next; view.revalidate(); view.repaint();
    }
    private void detachMoves(Nodes.Node root, Map<String, Nodes.Node> nodes, Map<String, ControlRegistry.Control> next) {
        var parents = new LinkedHashMap<JComponent, java.awt.Container>();
        parents.put(next.get(root.id()).component, view);
        for (var node : nodes.values()) if (node instanceof Nodes.Stack stack) {
            var parent = next.get(stack.id()).component;
            for (var child : stack.children()) parents.put(next.get(child.id()).component, parent);
        }
        // Detach every move before attaching any new edge: a valid new tree may invert old ancestry.
        // Children staying under the same parent are untouched, preserving unaffected sibling component identity.
        for (var entry : parents.entrySet()) {
            var component = entry.getKey(); var parent = component.getParent();
            if (parent != null && parent != entry.getValue()) parent.remove(component);
        }
    }
    private void inherit(Nodes.Node node, Map<String, ControlRegistry.Control> next, boolean enabled) {
        next.get(node.id()).parentEnabled = enabled;
        if (node instanceof Nodes.Stack stack) {
            boolean childrenEnabled = enabled && node.state().enabled() && !node.state().busy() && node.state().visible();
            stack.children().forEach(child -> inherit(child, next, childrenEnabled));
        }
    }
    private void collect(Nodes.Node node, Map<String, Nodes.Node> nodes) {
        nodes.put(node.id(), node); if (node instanceof Nodes.Stack stack) stack.children().forEach(child -> collect(child, nodes));
    }
    ControlRegistry.Control control(String id) {
        var control = mounted.get(id); if (control == null) throw new IllegalArgumentException("Unknown node: " + id); return control;
    }
    void refresh() { mounted.values().forEach(ControlRegistry.Control::refresh); view.revalidate(); view.repaint(); }
    @Override public void close() { mounted.values().forEach(ControlRegistry.Control::close); mounted.clear(); view.removeAll(); }
}
