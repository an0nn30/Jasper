package dev.jasper.indexer.ui;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class IndexerSettingsPanelTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    @TempDir Path data;

    @Test void listsProjectsAndReindexesOrRemovesTheSelectedOne() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var projects = new Projects();
            var io = new ManualExecutor();
            var ui = new ManualExecutor();
            var listed = new ArrayList<Indexer.Job>();
            var notifications = new FakeNotifications();
            var indexer = new Indexer(projects, new IndexStore(data.resolve("store")), job -> { listed.add(job); return new CompletableFuture<Listing>(); },
                io, ui, Instant::now, text -> { }, notifications.group("r"), notifications.group("f"), System.getLogger("test"));
            projects.loaded(new Project(APP, "app", false), Optional.of(new ProjectIndex(APP, List.of("a", "b"), Strategy.GIT, Instant.EPOCH, false)));
            var adds = new AtomicInteger();
            var allow = new AtomicBoolean(false);
            var questions = new ArrayList<String>();
            var pendingRemoval = new java.util.concurrent.atomic.AtomicReference<Runnable>();
            try (var host = new FakePluginHost(data)) {
                host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> context.settings().page(new IndexerSettingsPanel(
                    context.kit(), new IndexerSettings(context.settings()), projects, indexer, key -> "at " + key.root(), key -> true, key -> true,
                    adds::incrementAndGet, (question, accepted) -> { questions.add(question); pendingRemoval.set(accepted); if (allow.get()) accepted.run(); })));
                host.settingsPanel(IndexerTesting.INFO.id()).orElseThrow();
                FakeKitView view = host.settingsPanelView(IndexerTesting.INFO.id()).orElseThrow();
                assertThat(view.spec().root()).isInstanceOfSatisfying(Nodes.Stack.class, page -> assertThat(page.role()).isEqualTo(Nodes.LayoutRole.SETTINGS_PAGE));
                host.highlightSettingsPanel(IndexerTesting.INFO.id(), "excluded folders");
                assertThat(node(view, "settings:excludes:row", Nodes.SettingRow.class).highlighted()).isTrue();
                view.emit(new UiEvent.Edited("settings:excludes:new", "new-hidden"));
                view.emit(new UiEvent.Action("settings:excludes:add"));
                projects.indexing(APP, false);
                assertThat(node(view, "settings:excludes:row", Nodes.SettingRow.class).highlighted()).isTrue();
                host.highlightSettingsPanel(IndexerTesting.INFO.id(), "");
                assertThat(node(view, "settings:excludes:row", Nodes.SettingRow.class).highlighted()).isFalse();
                assertThat(row(view, 0).cells()).containsEntry("name", "app").containsEntry("location", "at /w/app")
                    .containsEntry("files", "2").containsEntry("state", "Fresh");
                assertThat(table(view).columns()).filteredOn(column -> column.id().equals("location")).singleElement()
                    .satisfies(column -> assertThat(column.role()).as("spec 13: the data face").isEqualTo(Nodes.TextRole.MONO_SMALL));
                assertThat(action(view, "indexer.reindex").state().enabled()).isFalse();
                view.emit(new UiEvent.Action(action(view, "indexer.add").action()));
                assertThat(adds).hasValue(1);
                view.emit(new UiEvent.Selected("indexer.projects", Set.of(APP.id())));
                view.emit(new UiEvent.Action(action(view, "indexer.reindex").action()));
                assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isTrue());
                assertThat(row(view, 0).cells()).containsEntry("state", "Indexing…");
                assertThat(action(view, "indexer.remove").state().enabled()).as("model change clears selection").isFalse();
                view.emit(new UiEvent.Selected("indexer.projects", Set.of(APP.id())));
                view.emit(new UiEvent.Action(action(view, "indexer.remove").action()));
                assertThat(questions).containsExactly("Remove app from Project Index? Its saved index is deleted.");
                assertThat(projects.entries()).hasSize(1);
                view.emit(new UiEvent.Selected("indexer.projects", Set.of()));
                pendingRemoval.get().run();
                assertThat(projects.entries()).as("late confirmation cannot remove after selection changed").hasSize(1);
                view.emit(new UiEvent.Selected("indexer.projects", Set.of(APP.id())));
                allow.set(true);
                view.emit(new UiEvent.Action(action(view, "indexer.remove").action()));
                assertThat(projects.entries()).isEmpty();
                assertThat(find(view.spec().root(), "indexer.projects")).as("no table without projects").isNull();
                var empty = node(view, IndexerSettingsPanel.EMPTY, Nodes.Empty.class);
                assertThat(empty.title()).isEqualTo("No Projects");
                view.emit(new UiEvent.Action(action(view, "indexer.add").action()));
                assertThat(adds).as("the empty state's Add Folder… is the same action").hasValue(2);
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /** Plan 3 Task 8: the canonical Settings page: labelled sections; no projects is the standard empty state. */
    @Test void composesLabelledSectionsAndTheStandardEmptyState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var projects = new Projects();
            var notifications = new FakeNotifications();
            var indexer = new Indexer(projects, new IndexStore(data.resolve("empty-store")), job -> new CompletableFuture<Listing>(),
                new ManualExecutor(), new ManualExecutor(), Instant::now, text -> { }, notifications.group("r"), notifications.group("f"), System.getLogger("test"));
            var adds = new AtomicInteger();
            try (var host = new FakePluginHost(data)) {
                host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> context.settings().page(new IndexerSettingsPanel(context.kit(),
                    new IndexerSettings(context.settings()), projects, indexer, key -> key.root(), key -> true, key -> true, adds::incrementAndGet, (question, accepted) -> { })));
                host.settingsPanel(IndexerTesting.INFO.id()).orElseThrow();
                FakeKitView view = host.settingsPanelView(IndexerTesting.INFO.id()).orElseThrow();
                var page = (Nodes.Stack) view.spec().root();
                assertThat(page.role()).isEqualTo(Nodes.LayoutRole.SETTINGS_PAGE);
                assertThat(page.children()).extracting(node -> ((Nodes.Section) node).title()).containsExactly("Projects", "Detection", "Indexing");
                for (String card : List.of(IndexerSettingsPanel.DETECTION + ".card", IndexerSettingsPanel.INDEXING + ".card")) {
                    var settings = node(view, card, Nodes.Card.class);
                    assertThat(settings.kind()).isEqualTo(Nodes.CardKind.SETTINGS);
                    assertThat(settings.children()).hasSize(3).allSatisfy(row -> assertThat(((Nodes.Stack) ((Nodes.SettingRow) row).control()).role())
                        .as("the generated pages' trailing control slot").isEqualTo(Nodes.LayoutRole.SETTINGS_TRAILING));
                }
                var empty = node(view, IndexerSettingsPanel.EMPTY, Nodes.Empty.class);
                assertThat(empty.title()).isEqualTo("No Projects");
                assertThat(empty.actions()).singleElement().satisfies(add -> assertThat(((Nodes.Button) add).label()).isEqualTo("Add Folder…"));
                assertThat(find(view.spec().root(), "indexer.projects")).as("no column headers without projects").isNull();
                view.emit(new UiEvent.Action(action(view, "indexer.add").action()));
                assertThat(adds).hasValue(1);
                projects.loaded(new Project(APP, "app", false), Optional.of(new ProjectIndex(APP, List.of("a"), Strategy.GIT, Instant.EPOCH, false)));
                assertThat(find(view.spec().root(), IndexerSettingsPanel.EMPTY)).isNull();
                assertThat(table(view).columns()).extracting(dev.jasper.sdk.ui.Column::id).as("the right-aligned count last")
                    .containsExactly("name", "location", "state", "when", "files");
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void remoteProjectsShowWhetherTheyCanBeReached() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var host = java.util.UUID.randomUUID();
            var reachable = ProjectKey.remote(host, "/srv/app");
            var gone = ProjectKey.remote(java.util.UUID.randomUUID(), "/srv/old");
            var projects = new Projects();
            var notifications = new FakeNotifications();
            var indexer = new Indexer(projects, new IndexStore(data.resolve("store")), job -> new CompletableFuture<Listing>(),
                new ManualExecutor(), new ManualExecutor(), Instant::now, text -> { }, notifications.group("r"), notifications.group("f"),
                System.getLogger("test"));
            projects.loaded(new Project(reachable, "app", false), Optional.of(new ProjectIndex(reachable, List.of("a"), Strategy.GIT, Instant.EPOCH, false)));
            projects.loaded(new Project(gone, "old", false), Optional.of(new ProjectIndex(gone, List.of("a"), Strategy.GIT, Instant.EPOCH, false)));
            try (var fake = new FakePluginHost(data)) {
                fake.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> context.settings().page(new IndexerSettingsPanel(context.kit(),
                    new IndexerSettings(context.settings()), projects, indexer, key -> "build-01: " + key.root(),
                    key -> key.equals(reachable), key -> false, () -> { }, (question, accepted) -> accepted.run())));
                fake.settingsPanel(IndexerTesting.INFO.id()).orElseThrow();
                FakeKitView view = fake.settingsPanelView(IndexerTesting.INFO.id()).orElseThrow();
                assertThat(row(view, 0).cells()).containsEntry("location", "build-01: /srv/app").containsEntry("state", "Stale");
                assertThat(row(view, 1).cells()).containsEntry("state", "Unavailable");
            }
        });
    }

    @Test void oldConfirmationsCannotCrossPageGenerationOrProjectReplacement() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var projects = new Projects();
            var registration = new Project(APP, "app", false);
            projects.add(registration);
            var notifications = new FakeNotifications();
            var indexer = new Indexer(projects, new IndexStore(data.resolve("lifetime-store")),
                job -> new CompletableFuture<Listing>(), new ManualExecutor(), new ManualExecutor(), Instant::now,
                text -> { }, notifications.group("r"), notifications.group("f"), System.getLogger("test"));
            var answers = new ArrayList<Runnable>();
            try (var host = new FakePluginHost(data)) {
                host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> context.settings().page(new IndexerSettingsPanel(
                    context.kit(), new IndexerSettings(context.settings()), projects, indexer, key -> key.root(),
                    key -> true, key -> true, () -> { }, (question, accepted) -> answers.add(accepted))));
                host.settingsPanel(IndexerTesting.INFO.id()).orElseThrow();
                var first = host.settingsPanelView(IndexerTesting.INFO.id()).orElseThrow();
                first.emit(new UiEvent.Selected("indexer.projects", Set.of(APP.id())));
                first.emit(new UiEvent.Action(action(first, "indexer.remove").action()));
                host.closeSettingsPanel(IndexerTesting.INFO.id());
                host.settingsPanel(IndexerTesting.INFO.id()).orElseThrow();
                var second = host.settingsPanelView(IndexerTesting.INFO.id()).orElseThrow();
                second.emit(new UiEvent.Selected("indexer.projects", Set.of(APP.id())));
                answers.getFirst().run();
                assertThat(projects.get(APP)).isPresent();
                second.emit(new UiEvent.Action(action(second, "indexer.remove").action()));
                projects.remove(APP);
                projects.add(new Project(APP, "app", false));
                second.emit(new UiEvent.Selected("indexer.projects", Set.of(APP.id())));
                answers.getLast().run();
                answers.getLast().run();
                assertThat(projects.get(APP).orElseThrow().project()).isNotSameAs(registration);
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    private static Nodes.Table table(FakeKitView view) { return node(view, "indexer.projects", Nodes.Table.class); }

    private static dev.jasper.sdk.ui.TableRow row(FakeKitView view, int index) { return table(view).rows().get(index); }

    private static Nodes.Button action(FakeKitView view, String id) { return node(view, id, Nodes.Button.class); }

    static <T extends Nodes.Node> T node(FakeKitView view, String id, Class<T> type) {
        var found = find(view.spec().root(), id);
        assertThat(found).as(id).isInstanceOf(type);
        return type.cast(found);
    }

    static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<Nodes.Node> children = switch (node) {
            case Nodes.Stack stack -> stack.children();
            case Nodes.Section section -> List.of(section.body());
            case Nodes.Card card -> card.children();
            case Nodes.SettingRow row -> List.of(row.control());
            case Nodes.Empty empty -> empty.actions();
            case Nodes.Form form -> form.sections();
            default -> List.of();
        };
        for (var child : children) { var found = find(child, id); if (found != null) return found; }
        return null;
    }
}
