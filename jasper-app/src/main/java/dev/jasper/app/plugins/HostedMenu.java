package dev.jasper.app.plugins;

import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.PluginMenu;
import java.util.ArrayList;
import java.util.List;

/**
 * A mutable menu tree whose root pushes an immutable snapshot to its model section after every change,
 * so windows never observe a half-built menu. UI thread only.
 */
final class HostedMenu implements PluginMenu {
    /** An action when {@code actionId} is set, otherwise a separator. Identity, not value, decides removal. */
    private static final class Leaf {
        final String actionId;
        Leaf(String actionId) { this.actionId = actionId; }
    }

    private final HostedUi ui;
    private final HostedMenu parent;
    private final MenuSection section;
    private final String title;
    private final List<Object> children = new ArrayList<>();
    private final Subscription removal;
    private boolean closed;

    static HostedMenu root(HostedUi ui, MenuSection section) { return new HostedMenu(ui, null, section, ""); }

    private HostedMenu(HostedUi ui, HostedMenu parent, MenuSection section, String title) {
        this.ui = ui; this.parent = parent; this.section = section; this.title = title;
        this.removal = parent == null
            ? ui.tracked(() -> { closed = true; section.close(); })
            : ui.subscription(() -> { closed = true; if (parent.children.remove(this)) parent.push(); });
    }

    private void push() {
        HostedMenu root = this;
        while (root.parent != null) root = root.parent;
        if (!root.closed) root.section.set(root.snapshot());
    }

    private List<MenuEntry> snapshot() {
        List<MenuEntry> entries = new ArrayList<>();
        for (Object child : children) {
            if (child instanceof HostedMenu menu) entries.add(new MenuEntry.Submenu(menu.title, menu.snapshot()));
            else entries.add(((Leaf) child).actionId == null ? new MenuEntry.Separator() : new MenuEntry.Item(((Leaf) child).actionId));
        }
        return entries;
    }

    private Subscription append(Leaf leaf) {
        children.add(leaf);
        push();
        return ui.subscription(() -> { if (children.remove(leaf)) push(); });
    }

    @Override public Subscription add(String actionId) {
        ui.guard("add");
        ui.requireOwn(actionId);
        return append(new Leaf(actionId));
    }

    @Override public Subscription addSeparator() {
        ui.guard("addSeparator");
        return append(new Leaf(null));
    }

    @Override public PluginMenu submenu(String submenuTitle) {
        ui.guard("submenu");
        if (submenuTitle == null || submenuTitle.isBlank()) throw new IllegalArgumentException("A submenu needs a title");
        var menu = new HostedMenu(ui, this, section, submenuTitle);
        children.add(menu);
        push();
        return menu;
    }

    @Override public void clear() {
        ui.guard("clear");
        children.clear();
        push();
    }

    @Override public void close() { removal.close(); }
}
