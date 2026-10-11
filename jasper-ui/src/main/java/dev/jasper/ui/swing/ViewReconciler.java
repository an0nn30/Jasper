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
        next.values().forEach(this::resetComposition);
        inherit(spec.root(), next, true);
        applyFlow(spec.root(), next);
        applyColumns(spec.root(), next, -1, false);
        applyPanelDetail(spec.root(), next, false);
        applyFullForm(spec.root(), next, false);
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
    private void resetComposition(ControlRegistry.Control control) {
        if (control instanceof TextFields.FieldControl field) field.panelSearch = false;
        if (control instanceof SecretFields.SecretControl secret) {
            secret.detailValueTop = -1;
            secret.detailActionsBelow = false;
        }
        if (control instanceof SettingRows.RowControl row) row.panelFooter = false;
        if (control instanceof InteractiveRows.RowsControl rows) rows.panelSingle = false;
        if (control instanceof Buttons.ButtonControl button) {
            button.button.panel = false;
            button.button.panelRow = false;
            button.button.fullFooter = false;
            button.button.orderedAction = false;
        }
        if (control instanceof Menus.MenuControl menu) {
            menu.main.panel = false;
            menu.arrow.panel = false;
        }
        if (control instanceof Labels.IconControl icon) icon.tile(0, false);
        if (control instanceof Labels.TextControl && control.component instanceof Labels.LiteralLabel text) {
            text.wrap = false;
            text.flow = false;
        }
        if (control instanceof Stacks.StackControl stack) stack.sheetHeading = false;
        control.embeddedLabel(false);
        control.hero(false);
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
        if(node instanceof Nodes.Form form && form.kind()==Nodes.FormKind.SHEET)form.hero().ifPresent(hero->{if(next.get(hero.id()) instanceof Stacks.StackControl stack)stack.sheetHeading=true;});
        boolean childrenEnabled = enabled && node.state().enabled() && !node.state().busy() && node.state().visible();
        ControlRegistry.children(node).forEach(child -> inherit(child, next, childrenEnabled));
    }
    private void applyHero(Nodes.Node node, Map<String, ControlRegistry.Control> next, boolean inherited) {
        boolean hero = node instanceof Nodes.Form form ? (form.kind() == Nodes.FormKind.HERO || form.kind() == Nodes.FormKind.HERO_COMPACT) : inherited;
        next.get(node.id()).hero(hero);
        ControlRegistry.children(node).forEach(child -> applyHero(child, next, hero));
    }
    private void applyFullForm(Nodes.Node node, Map<String, ControlRegistry.Control> next, boolean inherited) {
        boolean full = node instanceof Nodes.Form form ? form.kind() == Nodes.FormKind.FULL : inherited;
        if (next.get(node.id()) instanceof SettingRows.RowControl row) row.fullForm = full;
        ControlRegistry.children(node).forEach(child -> applyFullForm(child, next, full));
    }
    /** THREE_COLUMN assigns reusable rail/list/detail presentation; identities remain producer data. */
    private void applyColumns(Nodes.Node node, Map<String, ControlRegistry.Control> next, int column, boolean inheritedDetail) {
        boolean detail = inheritedDetail || node instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.DETAIL;
        var control = next.get(node.id());
        if (control instanceof Panels.PanelControl panel) panel.column = column;
        if (control instanceof InteractiveRows.RowsControl rows) rows.column = column;
        if (control instanceof SettingRows.RowControl row) row.detail = detail;
        if (control instanceof Stacks.StackControl stack) stack.detail = detail;
        var children = ControlRegistry.children(node);
        for (int i = 0; i < children.size(); i++) applyColumns(children.get(i), next,
            node instanceof Nodes.Split split && split.kind() == Nodes.SplitKind.THREE_COLUMN ? i : column, detail);
    }
    /** Compact details are a panel-footer context; ordinary manager details retain their own scale. */
    private void applyPanelDetail(Nodes.Node node, Map<String, ControlRegistry.Control> next, boolean inherited) {
        var control = next.get(node.id());
        if (control instanceof Stacks.StackControl stack) stack.panelDetail = inherited;
        if (control instanceof Buttons.ButtonControl button) button.button.panelDetail = inherited;
        if (control instanceof Labels.TextControl text) text.panelDetail = inherited;
        if (control instanceof Labels.IconControl icon) icon.panelDetail = inherited;
        if (node instanceof Nodes.Panel panel) panel.searchOrNavigation()
            .filter(n -> n instanceof Nodes.Field field && field.kind() == Nodes.FieldKind.SEARCH)
            .ifPresent(n -> { if (next.get(n.id()) instanceof TextFields.FieldControl field) field.panelSearch = true; });
        if (node instanceof Nodes.Panel panel && panel.body() instanceof Nodes.ItemList list && list.kind()==Nodes.ListKind.SINGLE
            && next.get(list.id()) instanceof InteractiveRows.RowsControl rows) rows.panelSingle=true;
        for (var child : ControlRegistry.children(node)) {
            boolean compact = inherited || node instanceof Nodes.Panel panel && panel.footer().filter(n -> n == child).isPresent()
                && child instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.DETAIL;
            applyPanelDetail(child, next, compact);
        }
    }
    private void applyTiles(Nodes.Node node, Map<String, ControlRegistry.Control> next, int inherited) {
        int size = node instanceof Nodes.Form form && form.kind() == Nodes.FormKind.SHEET
                ? view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.CATEGORY_SHEET_TILE)
                : node instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.DETAIL
                ? view.context().metrics().value(next.get(node.id()) instanceof Stacks.StackControl control && control.panelDetail ? dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_DETAIL_TILE : dev.jasper.ui.metrics.DesignMetrics.Role.CATEGORY_DETAIL_TILE) : inherited;
        if (size > 0 && node instanceof Nodes.Icon icon && icon.size() == Nodes.IconSize.HERO && next.get(icon.id()) instanceof Labels.IconControl control)
            control.tile(size, control.panelDetail ? view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_DETAIL_TILE_HEIGHT) : size,
                control.panelDetail ? view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_DETAIL_GLYPH) : 0, false);
        ControlRegistry.children(node).forEach(child -> applyTiles(child, next, size));
    }
    /**
     * Body, description and caption text flows (wraps, keeping its line breaks) where its parent stacks content vertically: VERTICAL and DETAIL
     * stacks, form bodies, cards and sections. SUPPORTING flows beside natural adornments. Inline stacks, list rows and titles (BODY_STRONG included) keep one truncated line.
     */
    private void applyFlow(Nodes.Node node, Map<String, ControlRegistry.Control> next) {
        boolean vertical = node instanceof Nodes.Form || node instanceof Nodes.Card || node instanceof Nodes.Section
                || node instanceof Nodes.Stack stack && (stack.role() == Nodes.LayoutRole.VERTICAL || stack.role() == Nodes.LayoutRole.DETAIL || stack.role() == Nodes.LayoutRole.HERO || stack.role() == Nodes.LayoutRole.SUPPORTING);
        for (var child : ControlRegistry.children(node)) {
            if (vertical && child instanceof Nodes.Text text && switch (text.role()) {
                        case BODY, DESCRIPTION, SUPPORTING, CAPTION -> true;
                        default -> false;
                    } && next.get(child.id()).component instanceof Labels.LiteralLabel label) {
                label.flow = true;
                label.wrapped.centered = node instanceof Nodes.Stack parent && parent.role() == Nodes.LayoutRole.HERO;
                label.flowColumns = view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.TEXT_FLOW_COLUMNS);
            }
            applyFlow(child, next);
        }
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
        for(var owner:mounted.values())if(owner instanceof Choices.ChoiceControl choice && choice.input.getClientProperty("Jasper.pickerDismiss") instanceof Runnable dismiss){dismiss.run();dismissed=true;}
        return dismissed;
    }
    boolean popupOpen() {
        if(mounted.values().stream().anyMatch(owner -> owner instanceof Choices.ChoiceControl choice && Boolean.TRUE.equals(choice.input.getClientProperty("Jasper.pickerExpanded"))))return true;
        if(mounted.values().stream().anyMatch(owner -> owner instanceof PanelHeaders.HeaderControl header && header.popupOpen()))return true;
        if(mounted.values().stream().anyMatch(owner -> owner instanceof InteractiveRows.RowsControl rows && rows.popupOpen()))return true;
        if(mounted.values().stream().anyMatch(owner -> owner instanceof BreadcrumbControls.BreadcrumbsControl crumbs && crumbs.popup.isVisible()))return true;
        return mounted.values().stream().anyMatch(owner -> owner instanceof Menus.MenuControl menu && menu.popup.isVisible()
            || owner instanceof Choices.ChoiceControl choice && choice.input instanceof javax.swing.JComboBox<?> combo && combo.isPopupVisible());
    }
    ControlRegistry.Control mounted(String id) { return mounted.get(id); }
    boolean revealInline(String id) {
        for (var owner : mounted.values())
            if (owner instanceof InteractiveRows.RowsControl rows && rows.revealInline(id)) return true;
        return false;
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
