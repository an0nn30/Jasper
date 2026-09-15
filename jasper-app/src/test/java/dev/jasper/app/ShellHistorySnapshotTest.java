package dev.jasper.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistorySnapshotTest {
    @Test void newestOccurrenceWinsShellsMergeAndTheCapKeepsTheNewest() {
        var zsh = List.of(ShellHistoryEntry.of("ls", 100, "zsh"), ShellHistoryEntry.of("git status", 300, "zsh"));
        var bash = List.of(ShellHistoryEntry.of("ls", 0, "bash"), ShellHistoryEntry.of("make", 0, "bash"));
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
        var bash = List.of(ShellHistoryEntry.of("one", 0, "bash"), ShellHistoryEntry.of("two", 0, "bash"),
            ShellHistoryEntry.of("one", 0, "bash"));
        var snapshot = ShellHistorySnapshot.build(List.of(bash), List.of(), 50);
        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("one", "two");
    }
}
