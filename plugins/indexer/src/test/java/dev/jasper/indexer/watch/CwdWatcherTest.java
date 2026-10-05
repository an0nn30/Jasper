package dev.jasper.indexer.watch;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.index.IndexStore;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class CwdWatcherTest {
    @TempDir Path data;
    final Path home = Path.of("/Users/me");
    final Path app = Path.of("/Users/me/src/app");
    final ProjectKey appKey = ProjectKey.local(app);
    final ManualExecutor io = new ManualExecutor();
    final ManualExecutor ui = new ManualExecutor();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-05T10:00:00Z"));
    final List<Indexer.Job> listed = new ArrayList<>();
    final List<Path> detected = new ArrayList<>();
    final Map<Path, Detection<Path>> roots = new HashMap<>();
    final List<Runnable> scheduled = new ArrayList<>();
    final List<NotificationHandle> handles = new ArrayList<>();
    final FakeNotifications notifications = new FakeNotifications();
    FakePluginHost host;
    IndexerSettings settings;
    Projects projects;
    Indexer indexer;
    CwdWatcher watcher;

    @BeforeEach void create() {
        host = new FakePluginHost(data);
        var holder = new AtomicReference<IndexerSettings>();
        host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> holder.set(new IndexerSettings(context.settings())));
        settings = holder.get();
        projects = new Projects();
        indexer = new Indexer(projects, new IndexStore(data.resolve("store")), job -> { listed.add(job); return new CompletableFuture<Listing>(); },
            io, ui, now::get, text -> { }, notifications.group("dev.jasper.indexer.results"), notifications.group("dev.jasper.indexer"),
            System.getLogger("test"));
        roots.put(app.resolve("lib"), new Detection<>(app, new Markers.Match(".git", ".git")));
        roots.put(app, new Detection<>(app, new Markers.Match(".git", ".git")));
        watcher = watcher();
    }

    @AfterEach void close() { host.close(); }

    CwdWatcher watcher() {
        NotificationGroup recording = notifications.group("dev.jasper.indexer.prompts");
        NotificationGroup capturing = new NotificationGroup() {
            @Override public String id() { return recording.id(); }
            @Override public NotificationHandle post(NotificationSpec spec) {
                NotificationHandle handle = recording.post(spec);
                handles.add(handle);
                return handle;
            }
        };
        return new CwdWatcher(projects, indexer, settings, capturing, new CwdWatcher.Hooks(folder -> {
            detected.add(folder);
            return CompletableFuture.completedFuture(Optional.ofNullable(roots.get(folder)));
        }, pane -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, home));
    }

    void cd(Path folder) {
        watcher.cwd(new TerminalEvents.CwdChanged(UUID.randomUUID(), Optional.of(folder), Optional.empty()));
        while (io.drain() + ui.drain() > 0) { }
    }

    List<NotificationSpec> cards() {
        return notifications.posts().stream().filter(post -> post.group().equals("dev.jasper.indexer.prompts")).map(FakeNotifications.Post::spec).toList();
    }

    @Test void enteringAProjectAsksOnceAndYesIndexesIt() {
        cd(app.resolve("lib"));
        cd(app);
        assertThat(cards()).singleElement().satisfies(card -> {
            assertThat(card.title()).isEqualTo("Index project?");
            assertThat(card.content()).isEqualTo("app · ~/src/app\nGit repository");
            assertThat(card.actions()).extracting(action -> action.label()).containsExactly("Yes", "No", "Ignore this path");
        });
        assertThat(notifications.runAction("Index project?", "Yes")).isTrue();
        assertThat(projects.get(appKey).orElseThrow().project()).isEqualTo(new Project(appKey, "app", false));
        assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isTrue());
        cd(app.resolve("lib"));
        assertThat(cards()).as("an indexed project is not offered again").hasSize(1);
    }

    @Test void noLastsForTheSession() {
        cd(app);
        assertThat(notifications.runAction("Index project?", "No")).isTrue();
        cd(app.resolve("lib"));
        assertThat(cards()).hasSize(1);
        assertThat(detected).as("a folder inside a declined root is not probed").containsExactly(app);
        watcher = watcher();
        cd(app);
        assertThat(cards()).as("a new session asks again").hasSize(2);
    }

    @Test void ignoreThisPathIsSaved() {
        cd(app);
        assertThat(notifications.runAction("Index project?", "Ignore this path")).isTrue();
        assertThat(settings.ignored.get()).containsExactly(app.toString());
        watcher = watcher();
        cd(app.resolve("lib"));
        assertThat(cards()).hasSize(1);
        assertThat(detected).containsExactly(app);
    }

    @Test void aCardClosedWithoutAnAnswerCountsAsNo() {
        cd(app);
        handles.get(0).expire();
        cd(app);
        cd(app);
        assertThat(cards()).hasSize(1);
    }

    @Test void promptsCanBeTurnedOff() {
        host.setSetting("dev.jasper.indexer", "prompt", false);
        cd(app);
        assertThat(cards()).isEmpty();
        assertThat(detected).isEmpty();
    }

    @Test void enteringAnIndexedProjectRefreshesItAtMostOncePerThrottle() {
        indexer.add(new Project(appKey, "app", false));
        cd(app.resolve("lib"));
        cd(app);
        assertThat(listed).singleElement().satisfies(job -> assertThat(job.foreground()).isFalse());
        assertThat(cards()).isEmpty();
    }

    @Test void aFinishedCommandRefreshesAfterTheDebounce() {
        indexer.add(new Project(appKey, "app", false));
        var finished = new TerminalEvents.CommandFinished(UUID.randomUUID(), "git checkout main", OptionalInt.of(0), Duration.ofSeconds(1),
            Optional.of(app), Optional.empty());
        watcher.finished(finished);
        watcher.finished(finished);
        assertThat(scheduled).as("the second command restarts the delay").hasSize(1);
        assertThat(listed).isEmpty();
        scheduled.get(0).run();
        assertThat(listed).hasSize(1);
        watcher.finished(new TerminalEvents.CommandFinished(UUID.randomUUID(), "ls", OptionalInt.of(0), Duration.ZERO,
            Optional.of(Path.of("/tmp")), Optional.empty()));
        assertThat(scheduled).hasSize(1);
    }

    @Test void helpersShortenHomeAndMatchIgnoredFolders() {
        assertThat(CwdWatcher.display(app, home)).isEqualTo("~/src/app".replace('/', java.io.File.separatorChar));
        assertThat(CwdWatcher.display(Path.of("/opt/x"), home)).isEqualTo(Path.of("/opt/x").toString());
        assertThat(CwdWatcher.ignored(List.of(app.toString()), app.resolve("lib"))).isTrue();
        assertThat(CwdWatcher.ignored(List.of("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e:/Users/me/src/app"), app)).as("a remote entry").isFalse();
        assertThat(CwdWatcher.ignored(List.of("relative"), app)).isFalse();
    }
}
