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
                assertThat(view.spec().root()).isInstanceOf(Nodes.Form.class);
                host.highlightSettingsPanel(IndexerTesting.INFO.id(), "excluded folders");
                assertThat(((Nodes.SettingRow) ((Nodes.Form) view.spec().root()).sections().get(3)).highlighted()).isTrue();
                view.emit(new UiEvent.Edited("settings:excludes", ".git\nnew-hidden"));
                projects.indexing(APP, false);
                assertThat(((Nodes.SettingRow) ((Nodes.Form) view.spec().root()).sections().get(3)).highlighted()).isTrue();
                host.highlightSettingsPanel(IndexerTesting.INFO.id(), "");
                assertThat(((Nodes.SettingRow) ((Nodes.Form) view.spec().root()).sections().get(3)).highlighted()).isFalse();
                assertThat(row(view, 0).cells()).containsEntry("name", "app").containsEntry("location", "at /w/app")
                    .containsEntry("files", "2").containsEntry("state", "Fresh");
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
                assertThat(table(view).rows()).isEmpty();
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

    private static Nodes.Table table(FakeKitView view) {
        var form = (Nodes.Form) view.spec().root();
        var section = (Nodes.Section) form.sections().getFirst();
        return (Nodes.Table) ((Nodes.Stack) section.body()).children().getFirst();
    }

    private static dev.jasper.sdk.ui.TableRow row(FakeKitView view, int index) { return table(view).rows().get(index); }

    private static Nodes.Button action(FakeKitView view, String id) {
        var form = (Nodes.Form) view.spec().root();
        var section = (Nodes.Section) form.sections().getFirst();
        var buttons = (Nodes.Stack) ((Nodes.Stack) section.body()).children().get(1);
        return buttons.children().stream().map(Nodes.Button.class::cast).filter(button -> button.id().equals(id)).findFirst().orElseThrow();
    }
}
