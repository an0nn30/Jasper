package dev.jasper.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryScopeTest {
    @TempDir Path dir;
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

    @Test void theContextCapsBothTheRecentListAndSearchResults() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(
                    ShellHistoryEntry.of("test one", 1, "zsh"), ShellHistoryEntry.of("test two", 2, "zsh"),
                    ShellHistoryEntry.of("test three", 3, "zsh"), ShellHistoryEntry.of("test four", 4, "zsh"),
                    ShellHistoryEntry.of("test five", 5, "zsh"), ShellHistoryEntry.of("test six", 6, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                var three = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger(), null, true), 3);
                assertThat(scope.search("", three).rows()).extracting(PaletteRow::title).containsExactly("test six", "test five", "test four");
                assertThat(scope.search("test", three).rows()).hasSize(3);
                var five = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger(), null, true));
                assertThat(scope.search("", five).rows()).hasSize(5);
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
                // Which row it lands on is not this test's subject — "ls" is trivial and gets
                // de-ranked — so find the multi-line row and check the tag it carries.
                assertThat(scope.search("", context).rows()).filteredOn(r -> r.title().contains("echo a"))
                    .singleElement().extracting(PaletteRow::tag).isEqualTo("zsh");
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
                assertThat(scope.available(row, ShellHistoryScope.PASTE, live)).isTrue();
                scope.execute(row, ShellHistoryScope.PASTE, live);
                assertThat(pasted).containsExactly("ls -la");
                assertThat(returns.get()).isZero();
                scope.execute(row, ShellHistoryScope.PASTE_RUN, live);
                assertThat(pasted).containsExactly("ls -la", "ls -la");
                assertThat(returns.get()).isEqualTo(1);
                var dead = new PaletteContext(true, target(pasted, returns, null, false));
                assertThat(scope.available(row, ShellHistoryScope.PASTE, dead)).isFalse();
                assertThat(scope.available(PaletteRow.of("x", "x"), ShellHistoryScope.PASTE, live)).isFalse();
                var changes = new AtomicInteger();
                var subscription = scope.onChanged(changes::incrementAndGet);
                scope.activated(live);
                index.record(ShellHistoryEntry.of("pwd", 2, "zsh"));
                assertThat(changes.get()).isGreaterThanOrEqualTo(1);
                subscription.close();
            }
        });
    }

    @Test void saveAsSnippetIsAThirdVerbOnlyWithAStoreAndAppendsThroughANameStep() throws Exception {
        Path file = dir.resolve("snippets.toml");
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(ShellHistoryEntry.of("git rebase -i origin/main", 1, "zsh"));
                 var store = new SnippetStore(file, path -> {}, SnippetStoreTest.inlineWorker(), Runnable::run)) {
                assertThat(new ShellHistoryScope(index, null).verbs()).containsExactly(ShellHistoryScope.PASTE, ShellHistoryScope.PASTE_RUN);
                var scope = new ShellHistoryScope(index, store, null);
                assertThat(scope.verbs()).containsExactly(ShellHistoryScope.PASTE, ShellHistoryScope.PASTE_RUN, ShellHistoryScope.SAVE);
                assertThat(ShellHistoryScope.suggestedName("git rebase -i origin/main")).isEqualTo("git rebase");
                assertThat(ShellHistoryScope.suggestedName("  ls  ")).isEqualTo("ls");
                assertThat(ShellHistoryScope.suggestedName("echo a\necho b")).isEqualTo("echo a");
                assertThat(ShellHistoryScope.suggestedName("x".repeat(200) + " y")).hasSize(128);
                var context = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger(), null, true));
                var row = scope.search("", context).rows().getFirst();
                var dead = new PaletteContext(true, target(new ArrayList<>(), new AtomicInteger(), null, false));
                assertThat(scope.available(row, ShellHistoryScope.PASTE, dead)).isFalse();
                assertThat(scope.available(row, ShellHistoryScope.SAVE, dead)).isTrue();
                assertThat(scope.available(row, ShellHistoryScope.PASTE, context)).isTrue();
                assertThat(scope.step(row, ShellHistoryScope.PASTE, context)).isNull();
                var step = scope.step(row, ShellHistoryScope.SAVE, context);
                assertThat(step.title()).isEqualTo("Save as snippet: git rebase -i origin/main");
                assertThat(step.fields()).singleElement().satisfies(field -> {
                    assertThat(field.name()).isEqualTo("name");
                    assertThat(field.prefill()).isEqualTo("git rebase");
                });
                var result = new AtomicReference<PaletteStep.Result>();
                step.complete().accept(Map.of("name", "Interactive rebase"), result::set);
                assertThat(result.get().error()).isNull();
                assertThat(result.get().reopenScopeId()).isEqualTo(PaletteScope.SNIPPETS_ID);
                assertThat(result.get().reopenRowId()).isEqualTo(SnippetsScope.rowId("Interactive rebase"));
                assertThat(Files.readString(file)).contains("name = \"Interactive rebase\"\ncommand = \"git rebase -i origin/main\"");
                step.complete().accept(Map.of("name", "interactive REBASE"), result::set);
                assertThat(result.get().error()).isEqualTo("A snippet named Interactive rebase exists");
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
    }

    private static PaletteContext context(boolean deprioritizeTrivial) {
        return new PaletteContext(true,
            new PaletteTarget(s -> {}, () -> {}, Optional::empty, () -> "zsh", () -> true),
            PaletteContext.DEFAULT_MAX_RESULTS, deprioritizeTrivial);
    }

    /** exit and clear are genuinely the most recent commands; they are still not what you are looking for. */
    @Test void trivialCommandsSortBelowRealWorkWhenTheSettingIsOn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(
                    ShellHistoryEntry.of("./gradlew build", 100, "zsh"),
                    ShellHistoryEntry.of("clear", 200, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                assertThat(scope.search("", context(true)).rows()).extracting(PaletteRow::title)
                    .containsExactly("./gradlew build", "clear");
                assertThat(scope.search("", context(false)).rows()).extracting(PaletteRow::title)
                    .containsExactly("clear", "./gradlew build");
            }
        });
    }

    @Test void trivialCommandsKeepTheirOrderAmongThemselves() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(
                    ShellHistoryEntry.of("./gradlew build", 100, "zsh"),
                    ShellHistoryEntry.of("clear", 200, "zsh"),
                    ShellHistoryEntry.of("exit", 300, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                assertThat(scope.search("", context(true)).rows()).extracting(PaletteRow::title)
                    .containsExactly("./gradlew build", "exit", "clear");
            }
        });
    }

    /** De-ranking never removes a row: searching for a trivial command still finds it. */
    @Test void trivialCommandsAreStillFoundWhenSearchedFor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var index = indexOf(
                    ShellHistoryEntry.of("./gradlew build", 100, "zsh"),
                    ShellHistoryEntry.of("clear", 200, "zsh"))) {
                var scope = new ShellHistoryScope(index, null);
                assertThat(scope.search("clear", context(true)).rows()).extracting(PaletteRow::title)
                    .contains("clear");
            }
        });
    }

    @Test void onlyAShortCommandWhoseFirstWordIsTrivialCounts() {
        assertThat(ShellHistoryScope.trivial("clear")).isTrue();
        assertThat(ShellHistoryScope.trivial("cd ..")).isTrue();
        assertThat(ShellHistoryScope.trivial("ls -la")).isTrue();
        assertThat(ShellHistoryScope.trivial("exit")).isTrue();
        // A real command that merely starts with a trivial word is not trivial.
        assertThat(ShellHistoryScope.trivial("cd /very/deep/path && ./gradlew build")).isFalse();
        assertThat(ShellHistoryScope.trivial("clearcache --all")).isFalse();
        assertThat(ShellHistoryScope.trivial("./gradlew build")).isFalse();
    }
}
