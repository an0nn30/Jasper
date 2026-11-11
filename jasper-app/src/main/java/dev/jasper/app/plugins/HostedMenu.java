package dev.jasper.app.plugins;

import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.MenuEntries;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.PluginMenu;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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

    /**
     * An {@code addEntries} group (browser plan 7 Task 1b): its items as last set, snapshotted as commands, separators and
     * keyed submenus. Each key is the plugin's id, the group's number and the item's id ({@link #entryId}), so no two
     * groups, of this plugin or another, share one in a window. Identity, not value, decides removal.
     */
    private final class Entries implements MenuEntries {
        private final String prefix;
        private final Consumer<String> chosen;
        private final Subscription removal;
        private List<MenuItem> items;
        private boolean closed;

        Entries(List<MenuItem> items, Consumer<String> chosen) {
            this.items = items;
            this.chosen = chosen;
            prefix = ui.pluginId() + "/" + ui.nextEntries() + "/";
            removal = ui.subscription(() -> { closed = true; if (children.remove(this)) push(); });
        }

        @Override public void set(List<MenuItem> next) {
            ui.guard("set");
            List<MenuItem> checked = checked(next);
            if (closed) return;
            items = checked;
            // Once cleared out of its menu the group shows nothing.
            if (children.contains(this)) push();
        }

        @Override public void close() { removal.close(); }

        void snapshot(List<MenuItem> level, List<MenuEntry> into) {
            for (MenuItem item : level) {
                if (item.separator()) into.add(new MenuEntry.Separator());
                else if (!item.children().isEmpty()) {
                    List<MenuEntry> nested = new ArrayList<>();
                    snapshot(item.children(), nested);
                    into.add(new MenuEntry.Submenu(prefix + item.id(), item.label(), item.enabled(), nested));
                } else {
                    String id = item.id();
                    into.add(new MenuEntry.Command(prefix + id, item.label(), item.enabled(),
                        () -> ui.contain("menu entry " + id, () -> chosen.accept(id))));
                }
            }
        }
    }

    private final HostedUi ui;
    private final HostedMenu parent;
    private final MenuSection section;
    private final String title;
    private final List<Object> children = new ArrayList<>();
    private final Subscription removal;
    private boolean closed;

    static HostedMenu root(HostedUi ui, MenuSection section) { return new HostedMenu(ui, null, section, ""); }

    /** The id of the menu entry whose command or submenu has {@code key}: the part after the plugin's id and the group's number. */
    static String entryId(String key) { return key.substring(key.indexOf('/', key.indexOf('/') + 1) + 1); }

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
            if (child instanceof HostedMenu menu) entries.add(new MenuEntry.Submenu("", menu.title, true, menu.snapshot()));
            else if (child instanceof Entries group) group.snapshot(group.items, entries);
            else if (((Leaf) child).actionId == null) entries.add(new MenuEntry.Separator());
            else if (((Leaf) child).webAddress) entries.add(new MenuEntry.WebAddressItem(((Leaf) child).actionId));
            else entries.add(new MenuEntry.Item(((Leaf) child).actionId));
        }
        return entries;
    }

    /** Menu entries' checks (SDK 0.31.0): the items copied, their ids unique across the whole tree and none with a shortcut. */
    private static List<MenuItem> checked(List<MenuItem> items) {
        List<MenuItem> copy = List.copyOf(items);
        var seen = new HashSet<String>();
        var pending = new ArrayDeque<MenuItem>(copy);
        while (!pending.isEmpty()) {
            MenuItem item = pending.pop();
            if (!seen.add(item.id())) throw new IllegalArgumentException("Duplicate id: " + item.id());
            if (!item.shortcut().isEmpty()) throw new IllegalArgumentException("A menu entry shows no shortcut: " + item.id());
            pending.addAll(item.children());
        }
        return copy;
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

    /** A submenu shares its root's section, so the terminal section's submenus refuse entries too. */
    @Override public MenuEntries addEntries(List<MenuItem> items, Consumer<String> chosen) {
        ui.guard("addEntries");
        Objects.requireNonNull(chosen, "chosen");
        if (section.target().type() == MenuTarget.Type.CONTEXT)
            throw new IllegalStateException("Menu entries do not belong to the terminal's right-click menu");
        var group = new Entries(checked(items), chosen);
        children.add(group);
        push();
        return group;
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
