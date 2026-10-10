package dev.jasper.indexer.ui;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.Objects;
import java.util.function.Predicate;

/** Indexed projects with immediate project actions and host-owned draft rows for declared settings. UI thread. */
public final class IndexerSettingsPanel implements SettingsPanel.View {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final String TABLE = "indexer.projects", ADD = "indexer.add", REINDEX = "indexer.reindex", REMOVE = "indexer.remove";
    private final UiKit kit;
    private final IndexerSettings settings;
    private final Projects projects;
    private final Indexer indexer;
    private final Function<ProjectKey, String> location;
    private final Predicate<ProjectKey> reachable;
    private final Predicate<ProjectKey> connected;
    private final Runnable addFolder;
    private final BiConsumer<String, Runnable> confirm;
    private Subscription listening = () -> { };
    private UiView view;
    private SettingsPanelHost host;
    private String selected;

    /** Project actions take effect immediately; declared settings still commit only on Apply. */
    public IndexerSettingsPanel(UiKit kit, IndexerSettings settings, Projects projects, Indexer indexer,
                                Function<ProjectKey, String> location, Predicate<ProjectKey> reachable,
                                Predicate<ProjectKey> connected, Runnable addFolder, BiConsumer<String, Runnable> confirm) {
        this.kit = kit; this.settings = settings; this.projects = projects; this.indexer = indexer; this.location = location;
        this.reachable = reachable; this.connected = connected; this.addFolder = addFolder; this.confirm = confirm;
    }

    @Override public UiView createView(SettingsPanelHost host) {
        this.host = host;
        selected = null;
        view = kit.create(new ViewSpec(node()), this::event);
        listening = projects.onChanged(() -> { selected = null; render(); });
        return view;
    }

    @Override public List<String> keywords() { return List.of("project", "index", "files", "reindex", "remove", "add folder"); }

    @Override public void dispose() {
        listening.close();
        listening = () -> { };
        view = null;
        host = null;
        selected = null;
    }

    private void event(UiEvent event) {
        if (view == null) return;
        if (event instanceof UiEvent.Selected choice && choice.id().equals(TABLE)) {
            selected = choice.ids().stream().findFirst().orElse(null);
            render();
        } else if (event instanceof UiEvent.Action action) {
            switch (action.id()) {
                case ADD -> addFolder.run();
                case REINDEX -> selected().ifPresent(entry -> indexer.request(entry.project().key(), true, Optional.empty()));
                case REMOVE -> selected().ifPresent(entry -> {
                    var key = entry.project().key();
                    confirm.accept("Remove " + entry.project().name() + " from Project Index? Its saved index is deleted.", () -> {
                        if (view != null && Objects.equals(selected, key.id())
                            && projects.entries().stream().anyMatch(current -> current.project().key().equals(key))) indexer.remove(key);
                    });
                });
                default -> { }
            }
        }
    }

    private Optional<Projects.Entry> selected() {
        return projects.entries().stream().filter(entry -> entry.project().key().id().equals(selected)).findFirst();
    }

    private void render() { if (view != null) view.update(new ViewSpec(node())); }

    private Nodes.Form node() {
        var rows = new ArrayList<TableRow>();
        for (Projects.Entry entry : projects.entries()) {
            var key = entry.project().key();
            rows.add(new TableRow(key.id(), Map.of(
                "name", entry.project().name(),
                "location", location.apply(key),
                "files", entry.index().map(index -> String.format(Locale.ROOT, "%,d", index.size())).orElse(""),
                "when", entry.index().map(index -> WHEN.format(index.indexedAt())).orElse(""),
                "state", state(entry)), Optional.of("folder")));
        }
        var table = new Nodes.Table(TABLE, ControlState.READY, List.of(
            new Column("name", "Name", Nodes.TextRole.BODY, false, 1.2, Optional.empty()),
            new Column("location", "Location", Nodes.TextRole.BODY, false, 2.4, Optional.empty()),
            new Column("files", "Files", Nodes.TextRole.MONO_SMALL, true, .6, Optional.empty()),
            new Column("when", "Last indexed", Nodes.TextRole.CAPTION, false, 1.2, Optional.empty()),
            new Column("state", "State", Nodes.TextRole.CAPTION, false, 1, Optional.empty())),
            rows, selected == null ? Set.of() : Set.of(selected), "", List.of(), Nodes.SelectionMode.SINGLE);
        var buttons = new Nodes.Stack("indexer.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
            new Nodes.Button(ADD, ControlState.READY, "Add Folder…", "add", Nodes.ButtonKind.SECONDARY, ADD, ""),
            new Nodes.Button(REINDEX, enabled(selected != null), "Reindex", "refresh", Nodes.ButtonKind.SECONDARY, REINDEX, ""),
            new Nodes.Button(REMOVE, enabled(selected != null), "Remove", "remove", Nodes.ButtonKind.DESTRUCTIVE_GHOST, REMOVE, "")));
        var sections = new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Section("indexer.projects.section", ControlState.READY, "Projects",
            new Nodes.Stack("indexer.projects.body", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(table, buttons))));
        sections.add(host.rowNode(settings.prompt));
        sections.add(host.rowNode(settings.markers));
        sections.add(host.rowNode(settings.excludes));
        sections.add(host.rowNode(settings.ignored));
        sections.add(host.rowNode(settings.maxFiles));
        sections.add(host.rowNode(settings.refreshMinutes));
        return new Nodes.Form("indexer.settings", ControlState.READY, Nodes.FormKind.SETTINGS,
            "Project Index", Optional.empty(), sections, Optional.empty(), List.of());
    }

    private static ControlState enabled(boolean value) { return new ControlState(true, value, false, ""); }

    private String state(Projects.Entry entry) {
        ProjectKey key = entry.project().key();
        if (!reachable.test(key)) return "Unavailable";
        Projects.State state = entry.state();
        if (!connected.test(key) && (state == Projects.State.FRESH || state == Projects.State.TRUNCATED)) return "Stale";
        return switch (state) {
            case FRESH -> "Fresh";
            case STALE -> "Stale";
            case TRUNCATED -> "Truncated";
            case MISSING -> "Missing";
            case NEEDS_REINDEX -> "Needs reindex";
            case INDEXING -> "Indexing…";
        };
    }
}
