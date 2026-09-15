package dev.jasper.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryScopeTest {
    private static ShellHistoryIndex indexOf(ShellHistoryEntry... entries) {
        var index = new ShellHistoryIndex(List.of(), new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
        for (ShellHistoryEntry entry : entries) index.record(entry);
        return index;
    }

    private static PaletteTarget target(List<String> pasted, AtomicInteger returns, Path cwd, boolean live) {
        return new PaletteTarget(pasted::add, returns::incrementAndGet, () -> Optional.ofNullable(cwd), () -> "zsh", () -> live);
    }

    @Test void emptyQueryShowsMostRecentAndTiersOrderPrefixWordThenSubstringWithADirectoryBoost() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(
                    ShellHistoryEntry.of("make test", 100, "zsh"),
                    ShellHistoryEntry.of("git commit -m test", 200, "zsh"),
                    new ShellHistoryEntry("test-runner --fast", 300, Set.of("bash"), Path.of("/work"), 0),
                    ShellHistoryEntry.of("attest now", 400, "zsh"),
                    ShellHistoryEntry.of("testing 1 2", 500, "fish"))) {
                var scope = new ShellHistoryScope(index, null);
                var context = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger(), Path.of("/work"), true));
                assertThat(scope.id()).isEqualTo(PaletteScope.HISTORY_ID);
                assertThat(scope.verbs()).containsExactly(ShellHistoryScope.PASTE, ShellHistoryScope.PASTE_RUN);
                assertThat(scope.preferredRows()).isEqualTo(12);
                assertThat(scope.monospaceRows()).isTrue();
                var recent = scope.search("  ", context);
                assertThat(recent.sectionLabel()).isEqualTo("Most recent");
                assertThat(recent.rows()).extracting(PaletteRow::title)
                    .containsExactly("testing 1 2", "attest now", "test-runner --fast", "git commit -m test", "make test");
                assertThat(recent.rows().get(2).detail()).isEqualTo("/work");
                assertThat(recent.rows().get(2).tag()).isEqualTo("bash");
                var matches = scope.search("test", context);
                assertThat(matches.sectionLabel()).isNull();
                assertThat(matches.rows()).extracting(PaletteRow::title).containsExactly(
                    "test-runner --fast", "testing 1 2", "git commit -m test", "make test", "attest now");
                assertThat(scope.search("commit test", context).rows()).extracting(PaletteRow::title).containsExactly("git commit -m test");
                assertThat(scope.search("nothing here", context).rows()).isEmpty();
            }
        });
    }

    @Test void tagsAppearOnlyWhenMoreThanOneShellContributedAndMultiLineTitlesCollapse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(ShellHistoryEntry.of("echo a\necho b", 1, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                var context = new PaletteContext(false, PaletteTarget.none());
                var rows = scope.search("", context).rows();
                assertThat(rows.getFirst().tag()).isNull();
                assertThat(rows.getFirst().title()).isEqualTo("echo a ↵ echo b");
                assertThat(rows.getFirst().id()).isEqualTo(ShellHistoryScope.rowId("echo a\necho b"));
                index.record(ShellHistoryEntry.of("ls", 2, "bash"));
                assertThat(scope.search("", context).rows().get(1).tag()).isEqualTo("zsh");
            }
        });
    }

    @Test void pasteAndPasteAndRunReachTheTargetOnlyWhileItIsLive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(ShellHistoryEntry.of("ls -la", 1, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                var pasted = new ArrayList<String>(); var returns = new AtomicInteger();
                var live = new PaletteContext(true, target(pasted, returns, null, true));
                var row = scope.search("", live).rows().getFirst();
                assertThat(scope.available(row, live)).isTrue();
                scope.execute(row, ShellHistoryScope.PASTE, live);
                assertThat(pasted).containsExactly("ls -la");
                assertThat(returns.get()).isZero();
                scope.execute(row, ShellHistoryScope.PASTE_RUN, live);
                assertThat(pasted).containsExactly("ls -la", "ls -la");
                assertThat(returns.get()).isEqualTo(1);
                var dead = new PaletteContext(true, target(pasted, returns, null, false));
                assertThat(scope.available(row, dead)).isFalse();
                assertThat(scope.available(PaletteRow.of("x", "x"), live)).isFalse();
                var changes = new AtomicInteger();
                var subscription = scope.onChanged(changes::incrementAndGet);
                scope.activated(live);
                index.record(ShellHistoryEntry.of("pwd", 2, "zsh"));
                assertThat(changes.get()).isGreaterThanOrEqualTo(1);
                subscription.close();
            }
        });
    }
}
