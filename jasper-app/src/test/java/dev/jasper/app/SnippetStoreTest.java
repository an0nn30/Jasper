package dev.jasper.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetStoreTest {
    @TempDir Path dir;

    static java.util.concurrent.ExecutorService inlineWorker() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        };
    }

    private SnippetStore inline(Path file, List<Path> opened) {
        return new SnippetStore(file, opened::add, inlineWorker(), Runnable::run);
    }

    @Test void reloadReadsRefreshSkipsUnchangedFilesAndAMalformedFileKeepsTheLastGoodSnapshot() throws Exception {
        Path file = dir.resolve("snippets.toml");
        Files.writeString(file, "[[snippet]]\nname = \"One\"\ncommand = \"echo 1\"\n");
        SwingUtilities.invokeAndWait(() -> {
            try (var store = inline(file, new ArrayList<>())) {
                var changes = new AtomicInteger();
                store.onChanged(changes::incrementAndGet);
                store.reload();
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("One");
                assertThat(store.snapshot().byName(" one ")).isPresent();
                assertThat(changes.get()).isEqualTo(1);
                store.refresh();
                assertThat(changes.get()).isEqualTo(1);
                try {
                    Files.writeString(file, "[[snippet]\nbroken");
                    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
                } catch (IOException e) { throw new UncheckedIOException(e); }
                store.refresh();
                assertThat(store.snapshot().erroneous()).isTrue();
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("One");
                assertThat(changes.get()).isEqualTo(2);
                try {
                    Files.writeString(file, "[[snippet]]\nname = \"Two\"\ncommand = \"echo 2\"\n");
                    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
                } catch (IOException e) { throw new UncheckedIOException(e); }
                store.reload();
                assertThat(store.snapshot().erroneous()).isFalse();
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("Two");
            }
        });
    }

    @Test void appendCreatesTheFileWithItsHeaderRefusesDuplicatesAndReportsOnTheEdt() throws Exception {
        Path file = dir.resolve("nested/snippets.toml");
        SwingUtilities.invokeAndWait(() -> {
            try (var store = inline(file, new ArrayList<>())) {
                store.reload();
                var saved = new AtomicReference<Snippet>(); var message = new AtomicReference<String>();
                store.append(" Deploy ", "make deploy {{env}}", (snippet, error) -> { saved.set(snippet); message.set(error); });
                assertThat(message.get()).isNull();
                assertThat(saved.get().name()).isEqualTo("Deploy");
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("Deploy");
                try {
                    String text = Files.readString(file);
                    assertThat(text).startsWith(SnippetFile.HEADER);
                    assertThat(text).contains("command = \"make deploy {{env}}\"");
                    Files.writeString(file, text + "# keep me\n");
                } catch (IOException e) { throw new UncheckedIOException(e); }
                store.append("deploy", "other", (snippet, error) -> { saved.set(snippet); message.set(error); });
                assertThat(saved.get()).isNull();
                assertThat(message.get()).isEqualTo("A snippet named Deploy exists");
                store.append("", "other", (snippet, error) -> message.set(error));
                assertThat(message.get()).startsWith("Could not save snippet");
                store.append("Second", "ls", (snippet, error) -> message.set(error));
                assertThat(message.get()).isNull();
                try {
                    assertThat(Files.readString(file)).contains("# keep me\n\n[[snippet]]\nname = \"Second\"");
                } catch (IOException e) { throw new UncheckedIOException(e); }
                assertThat(store.snapshot().snippets()).extracting(Snippet::name).containsExactly("Deploy", "Second");
                store.lastValues().put("env", "staging");
                assertThat(store.lastValues()).containsEntry("env", "staging");
            }
        });
    }

    @Test void openInEditorCreatesAMissingFileAndReportsEditorFailures() throws Exception {
        Path file = dir.resolve("snippets.toml");
        var opened = new ArrayList<Path>();
        SwingUtilities.invokeAndWait(() -> {
            try (var store = inline(file, opened)) {
                var errors = new ArrayList<String>();
                store.openInEditor(errors::add);
                assertThat(opened).containsExactly(file);
                assertThat(errors).isEmpty();
                try { assertThat(Files.readString(file)).isEqualTo(SnippetFile.HEADER); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            }
            try (var failing = new SnippetStore(file, path -> { throw new IllegalStateException("no editor"); }, inlineWorker(), Runnable::run)) {
                var errors = new ArrayList<String>();
                failing.openInEditor(errors::add);
                assertThat(errors).singleElement().asString().contains("no editor");
            }
        });
        assertThat(AppDirs.resolve("Mac OS X", java.util.Map.of(), Path.of("/Users/j")).snippets())
            .isEqualTo(Path.of("/Users/j/.config/jasper/snippets.toml"));
    }
}
