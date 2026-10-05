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
        var revealGroups = new LinkedHashMap<String, Boolean>();
        for (var control : mounted.values()) if (control instanceof SecretFields.SecretControl secret) {
            String group = ((Nodes.Secret)secret.node).revealGroup();
            if (!group.isEmpty()) revealGroups.put(group, secret.revealed);
        }
        next.values().forEach(control -> { if(control instanceof SettingRows.RowControl row)row.panelFooter=false; if(control instanceof Buttons.ButtonControl button)button.button.panel=false; if(control instanceof Menus.MenuControl menu){menu.main.panel=false;menu.arrow.panel=false;} if (control instanceof Labels.IconControl icon) icon.tile(0, false); if (control instanceof Labels.TextControl && control.component instanceof Labels.LiteralLabel text) text.wrap = false; control.embeddedLabel(false); control.hero(false); });
        inherit(spec.root(), next, true);
        for (var node : nodes.values()) next.get(node.id()).update(node);
        applyHero(spec.root(), next, false);
        applyTiles(spec.root(), next, 0);
        for (var control : next.values()) if (control instanceof SecretFields.SecretControl secret) {
            String group = ((Nodes.Secret)secret.node).revealGroup();
            if (!group.isEmpty()) secret.reveal(revealGroups.getOrDefault(group, false));
        }
        detachMoves(spec.root(), nodes, next);
        for (var node : nodes.values()) next.get(node.id()).children(ControlRegistry.children(node).stream().map(child -> next.get(child.id())).toList());
        JComponent root = next.get(spec.root().id()).component;
        if (root.getParent() != view) { view.removeAll(); view.add(root, java.awt.BorderLayout.CENTER); }
        // Publish ownership before invoking disposal callbacks: a callback may close this view.
        var previous = mounted; mounted = next;
        for (var old : previous.values()) if (next.get(old.node.id()) != old) old.close();
        view.revalidate(); view.repaint();
    }
    private void detachMoves(Nodes.Node root, Map<String, Nodes.Node> nodes, Map<String, ControlRegistry.Control> next) {
        var parents = new LinkedHashMap<JComponent, java.awt.Container>();
        parents.put(next.get(root.id()).component, view);
        for (var node : nodes.values()) {
            var owner = next.get(node.id());
            for (var child : ControlRegistry.children(node)) parents.put(next.get(child.id()).component, owner.childParent(child));
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
        boolean childrenEnabled = enabled && node.state().enabled() && !node.state().busy() && node.state().visible();
        ControlRegistry.children(node).forEach(child -> inherit(child, next, childrenEnabled));
    }
    private void applyHero(Nodes.Node node, Map<String, ControlRegistry.Control> next, boolean inherited) {
        boolean hero = node instanceof Nodes.Form form ? form.kind() == Nodes.FormKind.HERO : inherited;
        next.get(node.id()).hero(hero);
        ControlRegistry.children(node).forEach(child -> applyHero(child, next, hero));
    }
    private void applyTiles(Nodes.Node node, Map<String, ControlRegistry.Control> next, int inherited) {
        int size = node instanceof Nodes.Form form && form.kind() == Nodes.FormKind.SHEET
                ? view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.CATEGORY_SHEET_TILE)
                : node instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.DETAIL
                ? view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.CATEGORY_DETAIL_TILE) : inherited;
        if (size > 0 && node instanceof Nodes.Icon icon && icon.size() == Nodes.IconSize.HERO && next.get(icon.id()) instanceof Labels.IconControl control) control.tile(size, false);
        ControlRegistry.children(node).forEach(child -> applyTiles(child, next, size));
    }
    private void collect(Nodes.Node node, Map<String, Nodes.Node> nodes) {
        nodes.put(node.id(), node); ControlRegistry.children(node).forEach(child -> collect(child, nodes));
    }
    void registerContent(String id, JComponent component, AutoCloseable lifetime) {
        for(var control:mounted.values()) if(control instanceof CustomContents.CustomControl custom && ((Nodes.Custom)custom.node).contentId().equals(id)) {custom.register(component,lifetime);return;}
        throw new IllegalArgumentException("No matching Custom content");
    }
    boolean dismissPopup() {
        boolean dismissed = false;
        for(var owner:mounted.values()) if(owner instanceof PanelHeaders.HeaderControl header) dismissed |= header.dismiss();
        for(var owner:mounted.values()) if(owner instanceof InteractiveRows.RowsControl rows) dismissed |= rows.dismiss();
        for (var owner : mounted.values()) if (owner instanceof Menus.MenuControl menu) dismissed |= menu.dismiss();
        for (var owner : mounted.values()) if (owner instanceof BreadcrumbControls.BreadcrumbsControl crumbs) dismissed |= crumbs.dismiss();
        for (var owner : mounted.values()) if (owner instanceof Choices.ChoiceControl choice && choice.input instanceof javax.swing.JComboBox<?> combo && combo.isPopupVisible()) { combo.hidePopup(); dismissed = true; }
        return dismissed;
    }
    boolean popupOpen() {
        if(mounted.values().stream().anyMatch(owner -> owner instanceof PanelHeaders.HeaderControl header && header.popupOpen()))return true;
        if(mounted.values().stream().anyMatch(owner -> owner instanceof InteractiveRows.RowsControl rows && rows.popupOpen()))return true;
        if(mounted.values().stream().anyMatch(owner -> owner instanceof BreadcrumbControls.BreadcrumbsControl crumbs && crumbs.popup.isVisible()))return true;
        return mounted.values().stream().anyMatch(owner -> owner instanceof Menus.MenuControl menu && menu.popup.isVisible()
            || owner instanceof Choices.ChoiceControl choice && choice.input instanceof javax.swing.JComboBox<?> combo && combo.isPopupVisible());
    }
    ControlRegistry.Control control(String id) {
        var control = mounted.get(id);
        if(control == null) for(var owner:mounted.values()) if(owner instanceof InteractiveRows.RowsControl rows) { control=rows.inline(id); if(control!=null)break; }
        if (control == null) throw new IllegalArgumentException("Unknown node: " + id); return control;
    }
    void revealSecrets(SecretFields.SecretControl source, boolean revealed) {
        String group = ((Nodes.Secret)source.node).revealGroup();
        for (var control : mounted.values()) if (control instanceof SecretFields.SecretControl secret
                && (secret == source || !group.isEmpty() && group.equals(((Nodes.Secret)secret.node).revealGroup()))) secret.reveal(revealed);
    }
    void refresh() { mounted.values().forEach(ControlRegistry.Control::refresh); mounted.values().forEach(c -> {if(c instanceof Panels.PanelControl panel)panel.presentation();}); view.revalidate(); view.repaint(); }
    @Override public void close() { mounted.values().forEach(ControlRegistry.Control::close); mounted.clear(); view.removeAll(); }
}
