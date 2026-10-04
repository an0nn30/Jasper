package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import javax.swing.JComponent;

/** Typed renderer dispatch. Each mounted control owns precisely its own registrations. */
final class ControlRegistry {
    private ControlRegistry() {}
    static void validate(Nodes.Node node) {
        switch (node) {
            case Nodes.Text ignored -> {} case Nodes.Icon ignored -> {} case Nodes.Button ignored -> {} case Nodes.Field ignored -> {}
            case Nodes.Stack stack -> stack.children().forEach(ControlRegistry::validate);
            default -> throw new UnsupportedOperationException("Renderer not implemented yet: " + node.getClass().getSimpleName());
        }
    }
    static Control create(KitView view, Nodes.Node node) {
        return switch (node) {
            case Nodes.Text text -> new Labels.TextControl(view, text);
            case Nodes.Icon icon -> new Labels.IconControl(view, icon);
            case Nodes.Button button -> new Buttons.ButtonControl(view, button);
            case Nodes.Field field -> new TextFields.FieldControl(view, field);
            case Nodes.Stack stack -> new Stacks.StackControl(view, stack);
            default -> throw new UnsupportedOperationException("Renderer not implemented yet: " + node.getClass().getSimpleName());
        };
    }
    static Object kind(Nodes.Node node) {
        return switch (node) {
            case Nodes.Field field -> field.kind();
            case Nodes.Button button -> button.kind();
            default -> node.getClass();
        };
    }
    abstract static class Control implements AutoCloseable {
        final KitView view;
        final JComponent component;
        final ArrayList<AutoCloseable> registrations = new ArrayList<>();
        Nodes.Node node;
        boolean closed;
        boolean parentEnabled = true;
        Control(KitView view, Nodes.Node node, JComponent component) {
            this.view = view; this.node = node; this.component = component;
            KitOwnership.mark(component, node.id());
        }
        final KitContext context() { return view.context(); }
        void update(Nodes.Node next) { node = next; refresh(); }
        abstract void refresh();
        JComponent focusTarget() { return component; }
        final boolean interactive() { return !closed && parentEnabled && node.state().enabled() && !node.state().busy() && node.state().visible(); }
        final void emit(UiEvent event) { if (interactive()) view.dispatch(event); }
        final void state() { component.setVisible(node.state().visible()); component.setEnabled(parentEnabled && node.state().enabled() && !node.state().busy()); }
        final void own(AutoCloseable lifetime) { registrations.add(lifetime); }
        @Override public void close() {
            if (closed) return; closed = true;
            for (int i = registrations.size() - 1; i >= 0; i--) {
                try { registrations.get(i).close(); } catch (Throwable failure) { view.report(failure); }
            }
            registrations.clear();
        }
    }
}
