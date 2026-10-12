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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Indexed projects with immediate project actions and host-owned draft rows for declared settings. UI thread. */
public final class IndexerSettingsPanel implements SettingsPanel {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final String TABLE = "indexer.projects", ADD = "indexer.add", REINDEX = "indexer.reindex", REMOVE = "indexer.remove";
    static final String PAGE = "indexer.settings", PROJECTS_SECTION = "indexer.projects.section", PROJECTS_BODY = "indexer.projects.body",
        ACTIONS = "indexer.actions", EMPTY = "indexer.projects.empty", DETECTION = "indexer.detection", INDEXING = "indexer.indexing";
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
    private long pageGeneration;

    /** Project actions take effect immediately; declared settings still commit only on Apply. */
    public IndexerSettingsPanel(UiKit kit, IndexerSettings settings, Projects projects, Indexer indexer,
                                Function<ProjectKey, String> location, Predicate<ProjectKey> reachable,
                                Predicate<ProjectKey> connected, Runnable addFolder, BiConsumer<String, Runnable> confirm) {
        this.kit = kit; this.settings = settings; this.projects = projects; this.indexer = indexer; this.location = location;
        this.reachable = reachable; this.connected = connected; this.addFolder = addFolder; this.confirm = confirm;
    }

    @Override public UiView create(SettingsPanelHost host) {
        pageGeneration++;
        this.host = host;
        selected = null;
        view = kit.create(new ViewSpec(node()), this::event);
        listening = projects.onChanged(() -> { selected = null; render(); });
        return view;
    }

    @Override public List<String> keywords() { return List.of("project", "index", "files", "reindex", "remove", "add folder"); }

    @Override public void dispose() {
        pageGeneration++;
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
                    var registration = entry.project();
                    var requestingView = view;
                    long generation = pageGeneration;
                    var completed = new AtomicBoolean();
                    confirm.accept("Remove " + entry.project().name() + " from Project Index? Its saved index is deleted.", () -> {
                        if (completed.compareAndSet(false, true) && view == requestingView && pageGeneration == generation
                            && Objects.equals(selected, key.id())
                            && projects.get(key).map(current -> current.project() == registration).orElse(false)) indexer.remove(key);
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

    /**
     * A Settings page in the canonical composition: section labels above their bodies. Projects is the indexed projects'
     * table with its actions, or, with none, the standard empty state with Add Folder…; the declared settings follow
     * as settings cards grouped by what they decide.
     */
    private Nodes.Stack node() {
        var sections = new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Section(PROJECTS_SECTION, ControlState.READY, "Projects", projects.entries().isEmpty() ? empty() : projectsBody()));
        sections.add(card(DETECTION, "Detection", List.of(host.row(settings.prompt), host.row(settings.markers), host.row(settings.ignored))));
        sections.add(card(INDEXING, "Indexing", List.of(host.row(settings.excludes), host.row(settings.maxFiles), host.row(settings.refreshMinutes))));
        return new Nodes.Stack(PAGE, ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, sections);
    }

    private Nodes.Stack projectsBody() {
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
        // The right-aligned count is the last column, so no left-aligned heading runs into it.
        var table = new Nodes.Table(TABLE, ControlState.READY, List.of(
            new Column("name", "Name", Nodes.TextRole.BODY, false, 1.2, Optional.empty()),
            new Column("location", "Location", Nodes.TextRole.BODY, false, 2.2, Optional.empty()),
            new Column("state", "State", Nodes.TextRole.CAPTION, false, 1, Optional.empty()),
            new Column("when", "Last indexed", Nodes.TextRole.CAPTION, false, 1.2, Optional.empty()),
            new Column("files", "Files", Nodes.TextRole.MONO_SMALL, true, .7, Optional.empty())),
            rows, selected == null ? Set.of() : Set.of(selected), "", List.of(), Nodes.SelectionMode.SINGLE);
        var buttons = new Nodes.Stack(ACTIONS, ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
            add(),
            new Nodes.Button(REINDEX, enabled(selected != null), "Reindex", "refresh", Nodes.ButtonKind.SECONDARY, REINDEX, ""),
            new Nodes.Button(REMOVE, enabled(selected != null), "Remove", "remove", Nodes.ButtonKind.DESTRUCTIVE_GHOST, REMOVE, "")));
        return new Nodes.Stack(PROJECTS_BODY, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(table, buttons));
    }

    private static Nodes.Empty empty() {
        return new Nodes.Empty(EMPTY, ControlState.READY, new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "folder", Nodes.Hue.NONE,
            Nodes.IconSize.EMPTY, "Projects"), "No Projects", "Add a folder to index its files for the command palette.", List.of(add()));
    }

    private static Nodes.Button add() { return new Nodes.Button(ADD, ControlState.READY, "Add Folder…", "add", Nodes.ButtonKind.SECONDARY, ADD, ""); }

    private static Nodes.Section card(String id, String title, List<Nodes.Node> rows) {
        return new Nodes.Section(id, ControlState.READY, title, new Nodes.Card(id + ".card", ControlState.READY, Nodes.CardKind.SETTINGS, rows));
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
