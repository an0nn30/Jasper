package dev.jasper.snippets;

import dev.jasper.sdk.Subscription;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * The plugin's snippets. One serial worker reads and appends {@code snippets.toml}; immutable snapshots
 * publish on the EDT. Existing bytes are never rewritten, so hand edits and comments survive.
 */
public final class SnippetStore implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(SnippetStore.class.getName());

    /** The snippets last read successfully, and whether the file currently fails to parse. */
    public record Snapshot(List<Snippet> snippets, boolean erroneous) {
        static final Snapshot EMPTY = new Snapshot(List.of(), false);

        public Snapshot { snippets = List.copyOf(snippets); }

        public Optional<Snippet> byName(String name) {
            String key = name.strip().toLowerCase(Locale.ROOT);
            return snippets.stream().filter(snippet -> snippet.key().equals(key)).findFirst();
        }
    }

    private final Path file;
    private final Consumer<Path> editor;
    private final ExecutorService worker;
    private final Executor deliver;
    // Worker-only state.
    private long size = -1;
    private FileTime modified;
    private List<Snippet> lastGood = List.of();
    private boolean erroneous, warned;
    // EDT-only state.
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<String, String> lastValues = new HashMap<>();
    private Snapshot snapshot = Snapshot.EMPTY;
    private volatile boolean closed;

    public SnippetStore(Path file, Consumer<Path> editor) {
        this(file, editor, Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-snippets").factory()), SwingUtilities::invokeLater);
    }

    public SnippetStore(Path file, Consumer<Path> editor, ExecutorService worker, Executor deliver) {
        this.file = Objects.requireNonNull(file);
        this.editor = Objects.requireNonNull(editor);
        this.worker = Objects.requireNonNull(worker);
        this.deliver = Objects.requireNonNull(deliver);
    }

    public Path file() { return file; }
    public Snapshot snapshot() { return snapshot; }
    /** Values last used for each placeholder name in this process. EDT only. */
    public Map<String, String> lastValues() { return lastValues; }

    public Subscription onChanged(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Re-reads unconditionally: startup and Reload Config. */
    public void reload() { submit(() -> read(true)); }

    /** Re-reads only when the file's size or modification time changed: scope activation. */
    public void refresh() { submit(() -> read(false)); }

    /** Appends one snippet. The completion runs on the EDT with the saved snippet, or null and a message. */
    public void append(String name, String command, BiConsumer<Snippet, String> completion) {
        submit(() -> {
            Snippet saved = null;
            String message;
            try {
                var snippet = new Snippet(name, command, List.of());
                if (read(true)) message = "snippets.toml has errors; fix it and Reload Config before saving";
                else {
                    var duplicate = lastGood.stream().filter(existing -> existing.key().equals(snippet.key())).findFirst();
                    if (duplicate.isPresent()) message = "A snippet named " + duplicate.get().name() + " exists";
                    else {
                        String existing = StateFile.readBounded(file, SnippetFile.MAX_BYTES, "Snippets").orElse("");
                        StateFile.writeAtomically(file, ".snippets-", SnippetFile.append(existing, snippet));
                        read(true);
                        saved = snippet;
                        message = null;
                    }
                }
            } catch (IllegalArgumentException | IOException failure) {
                message = "Could not save snippet: " + failure.getMessage();
            }
            Snippet result = saved;
            String outcome = message;
            deliver.execute(() -> { if (!closed) completion.accept(result, outcome); });
        });
    }

        /** Creates the file with its header when missing, then hands it to the editor (which reports its own failures). */
        public void openInEditor(Consumer<String> onError) {
            submit(() -> {
                try {
                    if (!Files.exists(file)) StateFile.writeAtomically(file, ".snippets-", SnippetFile.HEADER);
                    editor.accept(file);
                } catch (IOException | RuntimeException failure) {
                    deliver.execute(() -> { if (!closed) onError.accept("Could not open " + file + ": " + failure.getMessage()); });
                }
            });
        }

    private void submit(Runnable task) {
        if (closed) return;
        try { worker.execute(task); } catch (RejectedExecutionException ignored) { /* closing */ }
    }

    /** Worker only. Returns whether the file is currently erroneous; publishes when something was read. */
    private boolean read(boolean force) {
        try {
            if (!Files.isRegularFile(file)) {
                if (!force && size == -1 && !erroneous) return false;
                lastGood = List.of(); size = -1; modified = null; erroneous = false;
            } else {
                long currentSize = Files.size(file);
                FileTime currentModified = Files.getLastModifiedTime(file);
                if (!force && currentSize == size && currentModified.equals(modified)) return erroneous;
                String text = StateFile.readBounded(file, SnippetFile.MAX_BYTES, "Snippets").orElse("");
                SnippetFile.Parsed parsed = SnippetFile.parse(text);
                for (String warning : parsed.warnings()) LOG.log(System.Logger.Level.WARNING, "snippets.toml: " + warning);
                lastGood = parsed.snippets(); size = currentSize; modified = currentModified; erroneous = false; warned = false;
            }
        } catch (IOException failure) {
            if (!warned) { LOG.log(System.Logger.Level.WARNING, "Could not read snippets", failure); warned = true; }
            erroneous = true;
            size = -1; modified = null;
        }
        Snapshot next = new Snapshot(lastGood, erroneous);
        deliver.execute(() -> {
            if (closed) return;
            snapshot = next;
            for (Runnable listener : List.copyOf(listeners)) listener.run();
        });
        return erroneous;
    }

    @Override public void close() {
        closed = true;
        listeners.clear();
        worker.shutdownNow();
    }
}
