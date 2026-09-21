package dev.jasper.app.history;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistorySnapshotTest {
    private static final long NOW = 1_700_000_000L;
    private static final long WEEK = 604_800L;

    private static ShellHistorySnapshot.Source source(long modified, ShellHistoryEntry... entries) {
        return new ShellHistorySnapshot.Source(List.of(entries), modified);
    }

    @Test void newestOccurrenceWinsShellsMergeAndTheCapKeepsTheNewest() {
        var zsh = source(NOW, ShellHistoryEntry.of("ls", 100, "zsh"), ShellHistoryEntry.of("git status", 300, "zsh"));
        var bash = source(0, ShellHistoryEntry.of("ls", 0, "bash"), ShellHistoryEntry.of("make", 0, "bash"));
        var live = List.of(new ShellHistoryEntry("ls", 400, Set.of("zsh"), Path.of("/tmp"), 0));
        var snapshot = ShellHistorySnapshot.build(List.of(zsh, bash), live, ShellHistorySnapshot.MAX_ENTRIES);
        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("ls", "git status", "make");
        assertThat(snapshot.entries().getFirst().shells()).containsExactlyInAnyOrder("zsh", "bash");
        assertThat(snapshot.entries().getFirst().directory()).isEqualTo(Path.of("/tmp"));
        assertThat(snapshot.entries().getFirst().timestamp()).isEqualTo(400);
        assertThat(snapshot.shells()).containsExactlyInAnyOrder("zsh", "bash");
        var capped = ShellHistorySnapshot.build(List.of(zsh, bash), List.of(), 2);
        assertThat(capped.entries()).extracting(ShellHistoryEntry::command).containsExactly("git status", "ls");
        assertThat(ShellHistorySnapshot.EMPTY.entries()).isEmpty();
    }

    @Test void entriesWithoutTimestampsKeepFileOrderNewestLast() {
        var bash = source(0, ShellHistoryEntry.of("one", 0, "bash"), ShellHistoryEntry.of("two", 0, "bash"),
            ShellHistoryEntry.of("one", 0, "bash"));
        var snapshot = ShellHistorySnapshot.build(List.of(bash), List.of(), 50);
        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("one", "two");
    }

    /**
     * bash writes no timestamps without HISTTIMEFORMAT, so every entry arrives with 0. Ranking on that
     * directly buries a command run minutes ago beneath a zsh command from last week.
     */
    @Test void anUntimestampedEntryRanksNearWhenItsFileWasWritten() {
        var bash = source(NOW, ShellHistoryEntry.of("bash-recent", 0, "bash"));
        var zsh = source(NOW, ShellHistoryEntry.of("zsh-last-week", NOW - WEEK, "zsh"));

        var snapshot = ShellHistorySnapshot.build(List.of(bash, zsh), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("bash-recent", "zsh-last-week");
    }

    /** And the other direction: a file untouched for weeks must not float to the top. */
    @Test void aStaleUntimestampedFileSinksBelowRecentTimestampedEntries() {
        var bash = source(NOW - WEEK, ShellHistoryEntry.of("bash-old", 0, "bash"));
        var zsh = source(NOW, ShellHistoryEntry.of("zsh-today", NOW, "zsh"));

        var snapshot = ShellHistorySnapshot.build(List.of(bash, zsh), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("zsh-today", "bash-old");
    }

    @Test void untimestampedEntriesInOneFileStepBackASecondEach() {
        var bash = source(NOW, ShellHistoryEntry.of("first", 0, "bash"), ShellHistoryEntry.of("second", 0, "bash"),
            ShellHistoryEntry.of("third", 0, "bash"));

        var snapshot = ShellHistorySnapshot.build(List.of(bash), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("third", "second", "first");
    }

    @Test void aSourceWithNoModificationTimeKeepsTodaysBehaviour() {
        var unknown = source(0, ShellHistoryEntry.of("no-mtime", 0, "bash"));
        var zsh = source(NOW, ShellHistoryEntry.of("zsh", NOW, "zsh"));

        var snapshot = ShellHistorySnapshot.build(List.of(unknown, zsh), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("zsh", "no-mtime");
    }

    /** A file with more entries than its mtime has seconds must not rank below zero and outsort nothing. */
    @Test void anImplausiblyLargeFileClampsAtZeroRatherThanGoingNegative() {
        var many = new ShellHistoryEntry[] {ShellHistoryEntry.of("a", 0, "bash"), ShellHistoryEntry.of("b", 0, "bash")};
        var snapshot = ShellHistorySnapshot.build(List.of(source(1, many)), List.of(), 50);
        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("b", "a");
    }

    @Test void liveEntriesStillWinTiesAgainstFileEntries() {
        var file = source(NOW, ShellHistoryEntry.of("shared", NOW, "zsh"));
        var live = List.of(ShellHistoryEntry.of("shared", NOW, "live"));

        var snapshot = ShellHistorySnapshot.build(List.of(file), live, 50);

        assertThat(snapshot.entries()).hasSize(1);
        assertThat(snapshot.entries().getFirst().shells()).contains("live", "zsh");
    }
}
