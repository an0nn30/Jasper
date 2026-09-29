package dev.jasper.editor.recent;

import dev.jasper.editor.testsupport.StubDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class RecentFilesTest {
    private static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");
    @TempDir Path dir;
    private int days = 30;

    private Path file() { return dir.resolve("recent-files.toml"); }
    private RecentFiles load() { return RecentFiles.load(file(), Runnable::run, () -> days, T0); }
    private static StubDocument remote(String host, String path) {
        return new StubDocument("stub:" + host + ":" + path, path.substring(path.lastIndexOf('/') + 1), "dustin@" + host + ":" + path, host);
    }
    private static List<String> keys(List<RecentFile> entries) { return entries.stream().map(RecentFile::key).toList(); }

    @Test void newestFirstAndOpeningAgainMovesAnEntryToTheTop() {
        var recent = load();
        var a = remote("prod", "/etc/a.conf");
        var b = remote("prod", "/etc/b.conf");
        recent.record(a, T0);
        recent.record(b, T0.plusSeconds(1));
        recent.record(a, T0.plusSeconds(2));
        assertThat(keys(recent.entries(T0.plusSeconds(3)))).containsExactly(a.key(), b.key());
        assertThat(recent.entries(T0.plusSeconds(3)).getFirst().opened()).isEqualTo(T0.plusSeconds(2));
    }

    @Test void atMostFiftyAreKept() {
        var recent = load();
        for (int i = 0; i < 55; i++) recent.record(remote("h", "/f" + i), T0.plusSeconds(i));
        List<RecentFile> entries = recent.entries(T0.plusSeconds(60));
        assertThat(entries).hasSize(RecentFiles.MAX);
        assertThat(entries.getFirst().key()).isEqualTo("stub:h:/f54");
        assertThat(entries.getLast().key()).isEqualTo("stub:h:/f5");
    }

    @Test void entriesOlderThanTheRetentionAreHiddenThenForgotten() {
        var recent = load();
        var old = remote("prod", "/old");
        var fresh = remote("prod", "/fresh");
        recent.record(old, T0);
        recent.record(fresh, T0.plus(Duration.ofDays(29)));
        Instant boundary = T0.plus(Duration.ofDays(30));
        assertThat(keys(recent.entries(boundary))).containsExactly(fresh.key(), old.key());
        Instant later = boundary.plusSeconds(1);
        assertThat(keys(recent.entries(later))).containsExactly(fresh.key());
        recent.prune(later);
        assertThat(keys(RecentFiles.load(file(), Runnable::run, () -> days, T0).entries(T0))).containsExactly(fresh.key());
    }

    @Test void zeroDaysRecordsNothingAndEmptiesTheList() {
        var recent = load();
        recent.record(remote("prod", "/a"), T0);
        days = 0;
        recent.prune(T0);
        assertThat(recent.entries(T0)).isEmpty();
        recent.record(remote("prod", "/b"), T0);
        assertThat(recent.entries(T0)).isEmpty();
        days = 30;
        assertThat(load().entries(T0)).isEmpty();
    }

    @Test void entriesSurviveARoundTripThroughTheFile() {
        var recent = load();
        var local = new StubDocument("local:/tmp/a:b,c \"q\".txt", "a:b,c \"q\".txt", "/tmp/a:b,c \"q\".txt", null);
        var remote = new StubDocument("remote:1/deploy:/srv/na\u00efve r\u00e9sum\u00e9.txt", "na\u00efve r\u00e9sum\u00e9.txt",
            "deploy@prod:/srv/na\u00efve r\u00e9sum\u00e9.txt", "prod");
        recent.record(local, T0);
        recent.record(remote, T0.plusMillis(1500));
        assertThat(load().entries(T0.plusSeconds(5))).containsExactly(
            new RecentFile(remote.key(), remote.name(), remote.location(), Optional.of("prod"), T0.plusMillis(1500)),
            new RecentFile(local.key(), local.name(), local.location(), Optional.empty(), T0));
    }

    @Test void aMissingFileIsAnEmptyList() {
        assertThat(load().entries(T0)).isEmpty();
        assertThat(file()).doesNotExist();
    }

    @Test void aMalformedFileIsSetAsideAsBad() throws Exception {
        Files.writeString(file(), "not = [toml");
        assertThat(load().entries(T0)).isEmpty();
        assertThat(dir.resolve("recent-files.toml.bad")).hasContent("not = [toml");
        assertThat(file()).doesNotExist();
    }

    @Test void aMalformedEntryIsSkipped() throws Exception {
        Files.writeString(file(), """
            [[file]]
            key = "local:/a"
            name = "a"
            location = "/a"
            opened = "2026-09-01T09:00:00Z"

            [[file]]
            key = "local:/b"
            name = "b"
            location = "/b"
            """);
        assertThat(keys(load().entries(T0))).containsExactly("local:/a");
    }

    @Test void aFailedWriteKeepsThePreviousFile() throws Exception {
        var recent = load();
        var a = remote("prod", "/a");
        recent.record(a, T0);
        Files.createDirectory(dir.resolve("recent-files.toml.tmp"));
        recent.record(remote("prod", "/b"), T0.plusSeconds(1));
        assertThat(keys(recent.entries(T0.plusSeconds(2)))).hasSize(2);
        assertThat(keys(load().entries(T0.plusSeconds(2)))).containsExactly(a.key());
    }

    @Test void theLastChangeWinsWhenWritesRunOutOfOrder() {
        var queued = new ArrayList<Runnable>();
        var recent = RecentFiles.load(file(), queued::add, () -> days, T0);
        var a = remote("prod", "/a");
        var b = remote("prod", "/b");
        recent.record(a, T0);
        recent.record(b, T0.plusSeconds(1));
        queued.reversed().forEach(Runnable::run);
        assertThat(keys(load().entries(T0.plusSeconds(2)))).containsExactly(b.key(), a.key());
    }

    @Test void listenersHearRecordsAndRemovals() {
        var recent = load();
        int[] changes = {0};
        recent.onChanged(() -> changes[0]++);
        var a = remote("prod", "/a");
        recent.record(a, T0);
        recent.remove(a.key());
        recent.remove(a.key());
        assertThat(changes[0]).isEqualTo(2);
        assertThat(recent.entries(T0)).isEmpty();
    }
}
