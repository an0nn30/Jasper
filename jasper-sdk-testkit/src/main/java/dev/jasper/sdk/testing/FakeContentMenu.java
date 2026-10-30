package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The right-click menu a content tab shows ({@code ContentTab.showContextMenu}), as Jasper shows it: the plugin's
 * leading items, Jasper's pane items for the tab as they stood when it opened (Split with every Split choice, Move Pane
 * to Tab with the window's other tabs, Close Pane), then the trailing items, a separator between groups and none for
 * an empty group. It tells {@code chosen} once: a plugin item's id, or nothing when one of Jasper's items ran or the
 * menu was dismissed.
 */
final class FakeContentMenu {
    /** One of Jasper's pane items: a submenu holds children and runs nothing. */
    record Host(String label, boolean enabled, Runnable run, List<Host> children) {}

    final FakeContent content;
    final List<MenuItem> leading, trailing;
    final List<Host> host;
    private Consumer<Optional<String>> chosen;

    FakeContentMenu(FakeContent content, List<MenuItem> leading, List<Host> host, List<MenuItem> trailing, Consumer<Optional<String>> chosen) {
        this.content = content; this.leading = leading; this.host = host; this.trailing = trailing; this.chosen = chosen;
    }

    /** Refuses a menu two of whose items, in either list or any submenu, share an id, which its choice names. */
    static void uniqueIds(List<MenuItem> leading, List<MenuItem> trailing) {
        var seen = new java.util.HashSet<String>();
        var pending = new java.util.ArrayDeque<MenuItem>(leading);
        pending.addAll(trailing);
        while (!pending.isEmpty()) {
            var item = pending.pop();
            if (!seen.add(item.id())) throw new IllegalArgumentException("Duplicate id: " + item.id());
            pending.addAll(item.children());
        }
    }

    /** Checks every item's icon, children's included. */
    static void icons(List<MenuItem> items, Consumer<String> require) {
        for (var item : items) {
            if (!item.icon().isBlank()) require.accept(item.icon());
            icons(item.children(), require);
        }
    }

    /** The lines {@link FakePluginHost#contentMenu} gives. */
    List<String> lines() {
        var lines = new ArrayList<String>();
        if (!leading.isEmpty()) { plugin(leading, "", lines); lines.add("---"); }
        host(host, "", lines);
        if (!trailing.isEmpty()) { lines.add("---"); plugin(trailing, "", lines); }
        return lines;
    }

    private static void plugin(List<MenuItem> items, String indent, List<String> lines) {
        for (var item : items) {
            if (item.separator()) { lines.add(indent + "---"); continue; }
            lines.add(indent + (item.children().isEmpty() ? "item:" : "submenu:") + item.id() + (item.enabled() ? "" : " (disabled)"));
            plugin(item.children(), indent + "  ", lines);
        }
    }

    private static void host(List<Host> items, String indent, List<String> lines) {
        for (var item : items) {
            lines.add(indent + "host:" + item.label() + (item.enabled() ? "" : " (disabled)"));
            host(item.children(), indent + "  ", lines);
        }
    }

    /**
     * Chooses the item {@code path} names, a line of {@link #lines()} without its indent at each step down: a plugin's
     * enabled item tells {@code chosen} its id, one of Jasper's runs and tells it nothing; either closes the menu.
     *
     * @return whether an enabled item ran; false for a disabled one, one under a disabled submenu, a submenu itself, a
     *     separator or a path the menu does not show
     */
    boolean choose(List<String> path) {
        if (path.isEmpty() || chosen == null) return false;
        String first = path.getFirst();
        if (first.startsWith("host:")) {
            List<Host> items = host;
            Host item = null;
            for (String step : path) {
                if (item != null) { if (!item.enabled()) return false; items = item.children(); }
                item = items.stream().filter(candidate -> step.equals("host:" + candidate.label())).findFirst().orElse(null);
                if (item == null) return false;
            }
            if (!item.enabled() || !item.children().isEmpty()) return false;
            // As in Jasper, the item's action runs, then the plugin hears nothing.
            try { item.run().run(); } finally { report(Optional.empty()); }
            return true;
        }
        var groups = new ArrayList<MenuItem>(leading);
        groups.addAll(trailing);
        List<MenuItem> items = groups;
        MenuItem item = null;
        for (String step : path) {
            if (item != null) { if (!item.enabled()) return false; items = item.children(); }
            item = items.stream().filter(candidate -> !candidate.separator()
                && step.equals((candidate.children().isEmpty() ? "item:" : "submenu:") + candidate.id())).findFirst().orElse(null);
            if (item == null) return false;
        }
        if (!item.enabled() || !item.children().isEmpty()) return false;
        report(Optional.of(item.id()));
        return true;
    }

    /** Closes the menu without a choice, as Escape, a click elsewhere, another menu or the tab closing does. */
    void dismiss() { report(Optional.empty()); }

    /** Whether the menu still shows. */
    boolean open() { return chosen != null; }

    private void report(Optional<String> value) {
        var heard = chosen;
        chosen = null;
        if (content.menu == this) content.menu = null;
        if (heard != null) heard.accept(Objects.requireNonNull(value));
    }
}
