package dev.jasper.app.settingsui;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Settings category tree as a kit tree descriptor: page titles with their ancestors, search counts at the row ends, and
 * pages holding unsaved changes emphasized. Every group stays open, unless the look's navigation collapses (spec 12.1):
 * then a section the user closed stays closed for this opening, except while a search runs. EDT only.
 */
final class PageTree {
    static final String ID = "pages";
    private final Map<String, SettingsPage> shown = new LinkedHashMap<>();
    private List<SettingsPage> pages = List.of();
    private Map<String, Integer> counts = Map.of();
    private Predicate<String> modified = id -> false;
    private String selected;
    private boolean collapsible;
    private final Set<String> collapsed = new HashSet<>();

    /** Whether sections collapse (spec 12.1). */
    void collapsible(boolean value) { collapsible = value; if (!value) collapsed.clear(); }

    /**
     * The user opened or closed a section; the state lives as long as this tree (spec 12.1: it is not saved). While a
     * search runs every section shows, so a triangle's click there changes nothing.
     */
    void expanded(String id, boolean open) {
        if (!collapsible || !counts.isEmpty()) return;
        if (open) collapsed.remove(id); else collapsed.add(id);
    }

    /** Opens the sections above {@code id}, so a page navigated to is in view. */
    void reveal(String id) {
        for (var at = find(id).flatMap(SettingsPage::parent); at.isPresent(); at = find(at.get()).flatMap(SettingsPage::parent)) collapsed.remove(at.get());
    }

    boolean isCollapsed(String id) { return collapsed.contains(id); }

    /**
     * Shows the pages {@code visible} accepts, with their ancestors; a page that hides while childless never shows
     * without children.
     */
    void show(List<SettingsPage> pages, Predicate<SettingsPage> visible, Map<String, Integer> counts) {
        this.pages = List.copyOf(pages);
        this.counts = Map.copyOf(counts);
        var keep = new HashSet<String>();
        for (SettingsPage page : pages) {
            boolean childless = pages.stream().noneMatch(other -> other.parent().equals(Optional.of(page.id())));
            if (page.hiddenWhenChildless() && childless) continue;
            if (!visible.test(page)) continue;
            for (String id = page.id(); id != null; id = find(id).flatMap(SettingsPage::parent).orElse(null)) keep.add(id);
        }
        shown.clear();
        for (SettingsPage page : pages) if (keep.contains(page.id())) shown.put(page.id(), page);
    }

    private Optional<SettingsPage> find(String id) {
        return pages.stream().filter(page -> page.id().equals(id)).findFirst();
    }

    void select(String id) { selected = id; }

    Optional<String> selectedId() { return Optional.ofNullable(selected).filter(shown::containsKey); }

    void modifiedBy(Predicate<String> value) { modified = value; }

    boolean isModified(String id) { return modified.test(id); }

    int count(String id) { return counts.getOrDefault(id, 0); }

    /** Page ids in display order; for tests. */
    List<String> visibleIds() {
        var ids = new ArrayList<String>();
        for (var page : shown.values()) if (page.parent().isEmpty() || !shown.containsKey(page.parent().get())) walk(page, ids);
        return ids;
    }

    private void walk(SettingsPage page, List<String> ids) {
        ids.add(page.id());
        for (var child : children(page.id())) walk(child, ids);
    }

    private List<SettingsPage> children(String id) {
        return shown.values().stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
    }

    private TreeItem item(SettingsPage page) {
        var children = children(page.id()).stream().map(this::item).toList();
        int matches = count(page.id());
        return new TreeItem(page.id(), page.title(), Optional.empty(), children.isEmpty(), children,
            matches > 0 ? OptionalInt.of(matches) : OptionalInt.empty(), isModified(page.id()));
    }

    /** The kit tree: every branch open but the collapsed ones (all while searching), the selection, counts and emphasis. */
    Nodes.Tree node() {
        var roots = shown.values().stream().filter(page -> page.parent().isEmpty() || !shown.containsKey(page.parent().get()))
            .map(this::item).toList();
        var open = new HashSet<String>();
        for (var page : shown.values())
            if (!children(page.id()).isEmpty() && (!counts.isEmpty() || !collapsed.contains(page.id()))) open.add(page.id());
        return new Nodes.Tree(ID, ControlState.READY, roots, open, selectedId().map(Set::of).orElse(Set.of()), "");
    }
}
