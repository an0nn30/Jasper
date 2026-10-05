package dev.jasper.indexer.index;

import dev.jasper.indexer.testsupport.ManualExecutor;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.testing.FakeNotifications;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    static final ProjectKey LIB = ProjectKey.local(Path.of("/w/lib"));
    @TempDir Path data;

    final ManualExecutor io = new ManualExecutor();
    final ManualExecutor ui = new ManualExecutor();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-05T10:00:00Z"));
    final List<Optional<String>> progress = new ArrayList<>();
    final List<Indexer.Job> calls = new ArrayList<>();
    final Map<ProjectKey, CompletableFuture<Listing>> pending = new HashMap<>();
    final FakeNotifications notifications = new FakeNotifications();
    Projects projects;
    IndexStore store;
    Indexer indexer;

    @BeforeEach void create() {
        projects = new Projects();
        store = new IndexStore(data);
        indexer = indexer();
    }

    Indexer indexer() {
        return new Indexer(projects, store, job -> {
            calls.add(job);
            return pending.computeIfAbsent(job.key(), key -> new CompletableFuture<>());
        }, io, ui, now::get, progress::add, notifications.group("dev.jasper.indexer.results"),
            notifications.group("dev.jasper.indexer"), System.getLogger("test"));
    }

    void settle() { while (io.drain() + ui.drain() > 0) { } }

    void finish(ProjectKey key, Listing listing) {
        pending.remove(key).complete(listing);
        settle();
    }

    void fail(ProjectKey key, Throwable failure) {
        pending.remove(key).completeExceptionally(failure);
        settle();
    }

    @Test void aForegroundJobIndexesSavesReportsAndShowsProgress() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, true, Optional.empty());
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.INDEXING);
        assertThat(progress).last().isEqualTo(Optional.of("Indexing app…"));
        finish(APP, new Listing(List.of("a.txt", "b.txt"), Strategy.WALK, false));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.FRESH);
        assertThat(projects.indexes()).singleElement().extracting(ProjectIndex::files).isEqualTo(List.of("a.txt", "b.txt"));
        assertThat(projects.indexes().get(0).indexedAt()).isEqualTo(now.get());
        assertThat(progress).last().isEqualTo(Optional.empty());
        assertThat(notifications.lines()).containsExactly("dev.jasper.indexer.results|INFO|Indexed app|2 files");
        assertThat(store.loadIndex(APP)).isPresent();
        assertThat(projectsFile()).contains("key = \"local:" + APP.root() + "\"");
    }

    @Test void aBackgroundJobIsSilent() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        assertThat(progress).doesNotContain(Optional.of("Indexing app…"));
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(notifications.lines()).isEmpty();
    }

    @Test void requestsForARunningProjectMergeIntoOneFollowUp() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        indexer.request(APP, false, Optional.empty());
        indexer.request(APP, true, Optional.empty());
        assertThat(calls).hasSize(1);
        assertThat(indexer.running(APP)).isTrue();
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).foreground()).as("foreground wins the merge").isTrue();
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(calls).hasSize(2);
        assertThat(indexer.running(APP)).isFalse();
    }

    @Test void projectsOnOneHostRunOneAtATime() {
        indexer.add(new Project(APP, "app", false));
        indexer.add(new Project(LIB, "lib", false));
        indexer.request(APP, false, Optional.empty());
        indexer.request(LIB, false, Optional.empty());
        settle();
        assertThat(calls).extracting(Indexer.Job::key).containsExactly(APP);
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        assertThat(calls).extracting(Indexer.Job::key).containsExactly(APP, LIB);
    }

    @Test void aFailureKeepsTheSnapshotAndOffersRetry() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        indexer.request(APP, false, Optional.empty());
        fail(APP, new IOException("disk on fire"));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.STALE);
        assertThat(projects.indexes()).hasSize(1);
        assertThat(notifications.posts()).singleElement().satisfies(post -> {
            assertThat(post.spec().severity()).isEqualTo(Severity.ERROR);
            assertThat(post.spec().title()).isEqualTo("Couldn't index app");
            assertThat(post.spec().content()).isEqualTo("disk on fire");
        });
        assertThat(notifications.runAction("Couldn't index app", "Retry")).isTrue();
        assertThat(calls).hasSize(3);
        assertThat(calls.get(2).foreground()).isTrue();
    }

    @Test void aMissingRootLeavesSearchAndOnlyAForegroundJobSaysSo() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, false, Optional.empty());
        fail(APP, new NoSuchFileException("/w/app"));
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.MISSING);
        assertThat(notifications.posts()).isEmpty();
        indexer.request(APP, true, Optional.empty());
        fail(APP, new NoSuchFileException("/w/app"));
        assertThat(notifications.lines()).singleElement().asString().startsWith("dev.jasper.indexer|ERROR|Couldn't index app|");
    }

    @Test void cancellingKeepsTheSnapshotAndPostsNothing() {
        indexer.add(new Project(APP, "app", false));
        indexer.request(APP, true, Optional.empty());
        indexer.cancelForeground();
        assertThat(calls.get(0).cancelled()).isTrue();
        fail(APP, new CancellationException());
        assertThat(notifications.posts()).isEmpty();
        assertThat(projects.get(APP).orElseThrow().state()).isEqualTo(Projects.State.NEEDS_REINDEX);
        assertThat(progress).last().isEqualTo(Optional.empty());
    }

    @Test void aTruncatedSnapshotWarnsOncePerSession() {
        indexer.add(new Project(APP, "app", false));
        for (int i = 0; i < 2; i++) {
            indexer.request(APP, false, Optional.empty());
            finish(APP, new Listing(List.of("a"), Strategy.WALK, true));
        }
        assertThat(notifications.posts()).singleElement().satisfies(post -> {
            assertThat(post.spec().severity()).isEqualTo(Severity.WARNING);
            assertThat(post.spec().title()).isEqualTo("app has more than 1 file");
        });
    }

    @Test void refreshesWaitForTheirMinimumAge() {
        indexer.add(new Project(APP, "app", false));
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        finish(APP, new Listing(List.of("a"), Strategy.WALK, false));
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        assertThat(calls).hasSize(1);
        now.set(now.get().plus(Duration.ofMinutes(6)));
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        assertThat(calls).hasSize(2);
        indexer.refreshIfDue(ProjectKey.local(Path.of("/w/unknown")), Duration.ZERO);
        assertThat(calls).hasSize(2);
    }

    @Test void loadRestoresProjectsAndSnapshotsAndRemoveForgetsThem() {
        indexer.add(new Project(APP, "app", true));
        indexer.request(APP, false, Optional.empty());
        finish(APP, new Listing(List.of("a"), Strategy.GIT, false));
        projects = new Projects();
        indexer = indexer();
        indexer.load();
        settle();
        assertThat(projects.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.project()).isEqualTo(new Project(APP, "app", true));
            assertThat(entry.state()).isEqualTo(Projects.State.FRESH);
        });
        indexer.refreshIfDue(APP, Duration.ofMinutes(5));
        assertThat(calls).as("a loaded snapshot counts as the last attempt").hasSize(1);
        indexer.remove(APP);
        settle();
        assertThat(projects.entries()).isEmpty();
        assertThat(store.loadIndex(APP)).isEmpty();
        assertThat(projectsFile()).doesNotContain("[[project]]");
    }

    String projectsFile() {
        try { return java.nio.file.Files.readString(data.resolve("projects.toml")); }
        catch (IOException missing) { throw new AssertionError(missing); }
    }
}
