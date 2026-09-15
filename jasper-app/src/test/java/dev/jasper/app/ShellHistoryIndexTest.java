package dev.jasper.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryIndexTest {
    @TempDir Path home;

    /** Worker and delivery run inline on the EDT so each refresh is complete when the call returns. */
    private ShellHistoryIndex inline(List<ShellHistorySource> sources) {
        return new ShellHistoryIndex(sources, new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
    }

    @Test void grownFilesAreTailReadRewrittenFilesAreRereadAndUnchangedFilesAreSkipped() throws Exception {
        Path zsh = home.resolve(".zsh_history");
        Path bash = home.resolve(".bash_history");
        Files.writeString(zsh, ": 100:0;ls\n: 200:0;git status\n");
        Files.writeString(bash, "make\n");
        var sources = List.of(new ShellHistorySource(HistoryShell.ZSH, zsh),
            new ShellHistorySource(HistoryShell.BASH, bash),
            new ShellHistorySource(HistoryShell.FISH, home.resolve("missing")));
        SwingUtilities.invokeAndWait(() -> {
            try {
                try (var index = inline(sources)) {
                    var changes = new AtomicInteger();
                    index.onChanged(changes::incrementAndGet);
                    index.refresh();
                    assertThat(changes.get()).isEqualTo(1);
                    assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command)
                        .containsExactly("git status", "ls", "make");
                    assertThat(index.stats(sources.get(0))).isEqualTo(new ShellHistoryIndex.SourceStats(Files.size(zsh), 1, 0));
                    index.refresh();
                    assertThat(index.stats(sources.get(0)).fullReads()).isEqualTo(1);
                    assertThat(index.stats(sources.get(0)).tailReads()).isZero();
                    Files.writeString(zsh, ": 300:0;cargo build\n", StandardOpenOption.APPEND);
                    Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                    index.refresh();
                    assertThat(index.stats(sources.get(0))).isEqualTo(new ShellHistoryIndex.SourceStats(Files.size(zsh), 1, 1));
                    assertThat(index.snapshot().entries().getFirst().command()).isEqualTo("cargo build");
                    Files.writeString(zsh, ": 500:0;only\n");
                    Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
                    index.refresh();
                    assertThat(index.stats(sources.get(0)).fullReads()).isEqualTo(2);
                    assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("only", "make");
                    Files.delete(bash);
                    index.refresh();
                    assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("only");
                    assertThat(changes.get()).isEqualTo(5);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test void liveEntriesArriveFromAnyThreadAndRefreshesCoalesce() throws Exception {
        Queue<Runnable> work = new ArrayDeque<>();
        var worker = new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { work.add(task); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        };
        SwingUtilities.invokeAndWait(() -> {
            try (var index = new ShellHistoryIndex(List.of(), worker, Runnable::run)) {
                index.refresh(); index.refresh(); index.refresh();
                assertThat(work).hasSize(1);
                work.remove().run();
                assertThat(work).hasSize(1);
                work.remove().run();
                assertThat(work).isEmpty();
                index.record(new ShellHistoryEntry("ls -la", 900, Set.of("sh"), Path.of("/tmp"), 0));
                work.remove().run();
                assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("ls -la");
                assertThat(index.snapshot().entries().getFirst().directory()).isEqualTo(Path.of("/tmp"));
                assertThat(index.snapshot().shells()).containsExactly("sh");
            }
        });
    }

    @Test void unreadableSourcesContributeNothingAndDoNotStopOthers() throws Exception {
        Path directoryNotFile = Files.createDirectory(home.resolve(".zsh_history"));
        Path bash = home.resolve(".bash_history");
        Files.write(bash, "ok\n".getBytes(StandardCharsets.UTF_8));
        var sources = List.of(new ShellHistorySource(HistoryShell.ZSH, directoryNotFile), new ShellHistorySource(HistoryShell.BASH, bash));
        SwingUtilities.invokeAndWait(() -> {
            try (var index = inline(sources)) {
                index.refresh();
                assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command).containsExactly("ok");
            }
        });
    }
}
