package dev.jasper.app.plugins;

import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.PluginMenu;
import java.util.ArrayList;
import java.util.List;

/**
 * A mutable menu tree whose root pushes an immutable snapshot to its model section after every change,
 * so windows never observe a half-built menu. UI thread only.
 */
final class HostedMenu implements PluginMenu {
    /**
     * An action when {@code actionId} is set, shown only for a selected web address when {@code webAddress} is, otherwise
     * a separator. Identity, not value, decides removal.
     */
    private static final class Leaf {
        final String actionId;
        final boolean webAddress;
        Leaf(String actionId, boolean webAddress) { this.actionId = actionId; this.webAddress = webAddress; }
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
            else if (((Leaf) child).actionId == null) entries.add(new MenuEntry.Separator());
            else if (((Leaf) child).webAddress) entries.add(new MenuEntry.WebAddressItem(((Leaf) child).actionId));
            else entries.add(new MenuEntry.Item(((Leaf) child).actionId));
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
        return append(new Leaf(actionId, false));
    }

    /** A submenu shares its root's section, so the check holds in the terminal section's submenus too. */
    @Override public Subscription addForWebAddress(String actionId) {
        ui.guard("addForWebAddress");
        if (section.target().type() != MenuTarget.Type.CONTEXT)
            throw new IllegalStateException("addForWebAddress belongs to the terminal's right-click menu");
        ui.requireOwn(actionId);
        return append(new Leaf(actionId, true));
    }

    @Override public Subscription addSeparator() {
        ui.guard("addSeparator");
        return append(new Leaf(null, false));
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
