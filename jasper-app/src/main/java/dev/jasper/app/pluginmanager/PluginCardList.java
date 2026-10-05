package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The plugins as kit rows under group headings (Development, User-installed, Bundled), filtered by a search. One visible
 * row is selected. The list is one focus stop: Up and Down move, Space operates the row's switch, Shift+F10 or the menu
 * key opens the row's menu (a right click opens it on any row without selecting it). With nothing to show it is an empty
 * state saying why. EDT only; the panel closes its view.
 */
final class PluginCardList {
    /** A header's origin and title and its plugins, sorted by name. */
    record Group(String origin, String title, List<PluginRuntime.Row> rows) { }

    static final String LIST = "plugins";
    private final KitView view;
    private final Runnable selectionChanged;
    private List<PluginRuntime.Row> rows = List.of();
    private List<PluginRuntime.Row> shown = List.of();
    private List<String> titles = List.of();
    private String query = "";
    private String selected;
    private boolean busy;

    PluginCardList(KitContext kit, Consumer<UiEvent> events, Runnable selectionChanged) {
        this.selectionChanged = selectionChanged;
        view = new KitView(kit, spec(), event -> {
            if (event instanceof UiEvent.Selected chosen && chosen.id().equals(LIST)) {
                chosen.ids().stream().findFirst().filter(id -> row(id) != null).ifPresent(id -> { selected = id; selectionChanged.run(); });
                return;
            }
            events.accept(event);
        });
        view.getAccessibleContext().setAccessibleName("Plugins");
        int pixels = UIScale.scale(kit.metrics().value(DesignMetrics.Role.PLUGIN_LIST_MIN_WIDTH));
        view.setMinimumSize(new Dimension(pixels, UIScale.scale(kit.metrics().value(DesignMetrics.Role.PLUGIN_LIST_MIN_HEIGHT))));
    }

    KitView view() { return view; }

    /** Replaces the plugins, keeping the selection and the search. */
    void show(List<PluginRuntime.Row> next) { rows = List.copyOf(next); rebuild(); }

    /** Shows only plugins whose name, vendor, description or id contains {@code text}, ignoring case. */
    void search(String text) {
        String next = text == null ? "" : text.strip();
        if (next.equals(query)) return;
        query = next;
        rebuild();
    }

    void busy(boolean value) { if (busy == value) return; busy = value; rebuild(); }

    /** Selects the visible row for {@code id}; does nothing when it is not shown. */
    void select(String id) {
        if (row(id) == null) return;
        selected = id;
        render();
        selectionChanged.run();
    }

    String selected() { return selected; }

    PluginRuntime.Row selectedRow() { return row(selected); }

    /** The shown row for {@code id}, or null. */
    PluginRuntime.Row row(String id) {
        if (id == null) return null;
        for (PluginRuntime.Row row : shown) if (row.id().equals(id)) return row;
        return null;
    }

    List<String> listed() { return shown.stream().map(PluginCard::text).toList(); }

    List<String> groupTitles() { return titles; }

    /** The text shown when no row is, or empty. */
    String emptyText() { return shown.isEmpty() ? empty() : ""; }

    private String empty() { return rows.isEmpty() ? "No plugins installed" : "No plugins match"; }

    void close() { view.close(); }

    private void rebuild() {
        var visible = new ArrayList<PluginRuntime.Row>();
        var headings = new ArrayList<String>();
        for (Group group : groups(rows)) {
            List<PluginRuntime.Row> matching = group.rows().stream().filter(row -> matches(row, query)).toList();
            if (matching.isEmpty()) continue;
            headings.add(group.title());
            visible.addAll(matching);
        }
        shown = List.copyOf(visible);
        titles = List.copyOf(headings);
        if (row(selected) == null) selected = shown.isEmpty() ? null : shown.getFirst().id();
        render();
        selectionChanged.run();
    }

    private void render() { if (!view.isClosed()) view.update(spec()); }

    private ViewSpec spec() {
        if (shown.isEmpty()) return new ViewSpec(new Nodes.Empty("plugins-empty", ControlState.READY,
            new Nodes.Icon("plugins-empty-icon", ControlState.READY, "plugin", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Plugins"),
            empty(), "", List.of()));
        var items = new ArrayList<ListItem>();
        for (Group group : groups(rows)) {
            List<PluginRuntime.Row> matching = group.rows().stream().filter(row -> matches(row, query)).toList();
            if (matching.isEmpty()) continue;
            String heading = "group:" + group.origin();
            items.add(new ListItem(heading, Optional.empty(), List.of(new Nodes.Text(heading + ":title", ControlState.READY, group.title(),
                Nodes.TextRole.CAPTION)), List.of(), List.of(), true));
            for (PluginRuntime.Row row : matching) items.add(PluginCard.item(row, busy));
        }
        return new ViewSpec(new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.DOUBLE, items,
            selected == null ? Set.of() : Set.of(selected), "", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty()));
    }

    /** The groups in page order, each sorted by name ignoring case; empty groups are left out. */
    static List<Group> groups(List<PluginRuntime.Row> rows) {
        var result = new ArrayList<Group>();
        for (String origin : List.of("Development", "Installed", "Bundled")) {
            List<PluginRuntime.Row> members = rows.stream().filter(row -> row.origin().equals(origin))
                .sorted(Comparator.comparing(PluginRuntime.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
            if (members.isEmpty()) continue;
            long on = members.stream().filter(PluginCard::checked).count();
            String title = switch (origin) {
                case "Development" -> "Development";
                case "Installed" -> "User-installed (" + on + " of " + members.size() + " enabled)";
                default -> "Bundled (" + on + " of " + members.size() + " enabled)";
            };
            result.add(new Group(origin, title, members));
        }
        return result;
    }

    static boolean matches(PluginRuntime.Row row, String query) {
        if (query.isBlank()) return true;
        String haystack = String.join("\n", row.name(), row.vendor(), row.description(), row.id()).toLowerCase(Locale.ROOT);
        return haystack.contains(query.toLowerCase(Locale.ROOT));
    }
}
