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
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ShellHistoryIndexTest {
    /** Each generated line is exactly this many characters (excluding the trailing '\n'). */
    private static final int LINE_WIDTH = 1999;

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
        // bash records no timestamps, so its entries rank from the file's mtime. Pin it below the zsh
        // stamps above, or the wall clock would make this stale fixture the newest thing in the index.
        Files.setLastModifiedTime(bash, FileTime.fromMillis(50_000));
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

    private static String historyLine(String prefix, int index) {
        StringBuilder line = new StringBuilder(prefix).append(String.format("%06d", index));
        while (line.length() < LINE_WIDTH) line.append('.');
        return line.toString();
    }

    private static String block(String prefix, int count) {
        var block = new StringBuilder(count * (LINE_WIDTH + 1));
        for (int i = 0; i < count; i++) block.append(historyLine(prefix, i)).append('\n');
        return block.toString();
    }

    /**
     * A clipped read window (the 16 MiB {@code MAX_READ} cap) must be trimmed forward to the next
     * newline whether it comes from a full read or a tail read, or the read starts mid-line and the
     * first fragment parses as a garbled, truncated entry. 2000-byte lines are used deliberately: the
     * 16 MiB window is not a multiple of 2000, so the clip point never happens to land on a line
     * boundary by coincidence, and the test would pass vacuously with 64-byte (or any power-of-two)
     * lines because 16 MiB is itself a power of two.
     */
    @Test void aClippedTailReadIsTrimmedToTheNextLineSoNoPartialEntrySurvives() throws Exception {
        Path bash = home.resolve(".bash_history");
        int initCount = 8_450;   // 16,900,000 bytes: just over the 16,777,216-byte MAX_READ window.
        int appendCount = 8_500; // 17,000,000 bytes: also over MAX_READ, so the tail read is clipped too.
        Files.writeString(bash, block("init", initCount));
        long initSize = Files.size(bash);
        assertThat(initSize).isGreaterThan(16L * 1024 * 1024);
        var sources = List.of(new ShellHistorySource(HistoryShell.BASH, bash));
        SwingUtilities.invokeAndWait(() -> {
            try {
                try (var index = inline(sources)) {
                    index.refresh(); // Full read, itself clipped; the pre-fix code already trimmed full reads.
                    Files.writeString(bash, block("appd", appendCount), StandardOpenOption.APPEND);
                    assertThat(Files.size(bash) - initSize).isGreaterThan(16L * 1024 * 1024);
                    Files.setLastModifiedTime(bash, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                    index.refresh(); // Tail read, clipped: this is the path the fix corrects.
                    assertThat(index.stats(sources.get(0)).tailReads()).isEqualTo(1);

                    Pattern fullLine = Pattern.compile("^(init|appd)\\d{6}\\.{" + (LINE_WIDTH - 10) + "}$");
                    var entries = index.snapshot().entries();
                    assertThat(entries).isNotEmpty();
                    assertThat(entries).extracting(ShellHistoryEntry::command).allMatch(fullLine.asMatchPredicate(),
                        "is a complete, unclipped history line");
                    assertThat(entries.getFirst().command()).isEqualTo(historyLine("appd", appendCount - 1));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * bash without {@code histappend} overwrites {@code ~/.bash_history} on exit, and zsh rewrites
     * {@code $HISTFILE} when trimming to {@code SAVEHIST}; either can leave the file the same size or
     * larger than before, so a tail read starting from the old offset would land mid-line in unrelated
     * content. The rewrite here is deliberately larger than the original so the old
     * {@code size >= state.size} check alone would (wrongly) choose a tail read.
     */
    @Test void aRewrittenFileThatGrewIsReReadInFullAndYieldsNoFragment() throws Exception {
        Path zsh = home.resolve(".zsh_history");
        Files.writeString(zsh, ": 100:0;ls\n: 200:0;git status\n");
        var sources = List.of(new ShellHistorySource(HistoryShell.ZSH, zsh));
        SwingUtilities.invokeAndWait(() -> {
            try {
                try (var index = inline(sources)) {
                    index.refresh();
                    assertThat(index.stats(sources.get(0))).isEqualTo(new ShellHistoryIndex.SourceStats(Files.size(zsh), 1, 0));

                    Files.writeString(zsh, ": 300:0;cargo build\n: 400:0;cargo test\n: 500:0;docker ps\n");
                    assertThat(Files.size(zsh)).isGreaterThan(30L);
                    Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                    index.refresh();

                    assertThat(index.stats(sources.get(0)).fullReads()).isEqualTo(2);
                    assertThat(index.stats(sources.get(0)).tailReads()).isZero();
                    assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command)
                        .containsExactly("docker ps", "cargo test", "cargo build");
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test void theFingerprintKeepsSixtyFourBytesAcrossSmallTailReads() throws Exception {
        Path zsh = home.resolve(".zsh_history");
        String original = ": 100:0;ls -la --color=always\n: 200:0;git status --short\n: 300:0;cargo build --release\n";
        Files.writeString(zsh, original);
        var sources = List.of(new ShellHistorySource(HistoryShell.ZSH, zsh));
        SwingUtilities.invokeAndWait(() -> {
            try {
                try (var index = inline(sources)) {
                    index.refresh();
                    Files.writeString(zsh, ": 400:0;cd /tmp\n", StandardOpenOption.APPEND);
                    Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                    index.refresh();
                    assertThat(index.stats(sources.get(0)).tailReads()).isEqualTo(1);
                    assertThat(index.fingerprintLength(sources.get(0))).isEqualTo(64);

                    // A rewrite that keeps the last appended line in place but changes everything before it.
                    long offset = index.stats(sources.get(0)).offset();
                    String tail = ": 400:0;cd /tmp\n";
                    String prefix = "x".repeat((int) offset - tail.length() - 1) + "\n";
                    Files.writeString(zsh, prefix + tail + ": 500:0;docker ps\n");
                    Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
                    index.refresh();
                    assertThat(index.stats(sources.get(0)).fullReads()).isEqualTo(2);
                    // The rewritten prefix carries no timestamp, so it ranks from the file's mtime —
                    // which this fixture just set to now, ahead of the 1970-era stamps on the rest.
                    assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command)
                        .containsExactly("x".repeat((int) offset - tail.length() - 1), "docker ps", "cd /tmp");
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test void aZeroProgressReadKeepsTheFingerprintSoALaterRewriteIsStillDetected() throws Exception {
        Path zsh = home.resolve(".zsh_history");
        Files.writeString(zsh, ": 100:0;ls -la --color=always\n: 200:0;git status --short\n: 300:0;cargo build --release\n");
        var sources = List.of(new ShellHistorySource(HistoryShell.ZSH, zsh));
        SwingUtilities.invokeAndWait(() -> {
            try {
                try (var index = inline(sources)) {
                    index.refresh();
                    long offset = index.stats(sources.get(0)).offset();
                    Files.writeString(zsh, ": 400:0;partial", StandardOpenOption.APPEND);
                    Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                    index.refresh();
                    assertThat(index.stats(sources.get(0))).isEqualTo(new ShellHistoryIndex.SourceStats(offset, 1, 1));
                    assertThat(index.fingerprintLength(sources.get(0))).isEqualTo(64);

                    Files.writeString(zsh, ": 500:0;" + "y".repeat(80) + "\n: 600:0;docker ps\n");
                    Files.setLastModifiedTime(zsh, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
                    index.refresh();
                    assertThat(index.stats(sources.get(0)).fullReads()).isEqualTo(2);
                    assertThat(index.snapshot().entries()).extracting(ShellHistoryEntry::command)
                        .containsExactly("docker ps", "y".repeat(80));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
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
