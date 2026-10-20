package dev.jasper.editor.local;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.RelativePaths;
import dev.jasper.editor.api.Revision;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** A file on this machine; its reads, stats and writes run on the plugin's background executor. */
public final class LocalDocument implements EditorDocument {
    private final Path file;
    private final Executor background;

    /**
     * @param file the file
     * @param background where its I/O runs
     */
    public LocalDocument(Path file, Executor background) {
        this.file = file.toAbsolutePath().normalize();
        this.background = Objects.requireNonNull(background);
    }

    /**
     * @param file a local file
     * @return the key its document has
     */
    public static String key(Path file) { return "local:" + file.toAbsolutePath().normalize(); }

    /** @return the absolute, normalized file */
    public Path file() { return file; }

    @Override public Optional<EditorDocument> resolveRelative(String path) {
        RelativePaths.requireValid(path);
        Path parent = file.getParent();
        if (parent == null) parent = file;
        return Optional.of(new LocalDocument(parent.resolve(path), background));
    }

    @Override public String key() { return key(file); }
    @Override public String name() { Path name = file.getFileName(); return name == null ? file.toString() : name.toString(); }
    @Override public String location() { return file.toString(); }
    @Override public Optional<String> host() { return Optional.empty(); }
    @Override public CompletableFuture<Content> read(long limitBytes) { return supply(() -> LocalFiles.read(file, limitBytes)); }
    @Override public CompletableFuture<Optional<Revision>> revision() { return supply(() -> LocalFiles.revision(file)); }
    @Override public CompletableFuture<Revision> write(byte[] content) { return supply(() -> LocalFiles.write(file, content)); }

    private <T> CompletableFuture<T> supply(Callable<T> work) {
        var result = new CompletableFuture<T>();
        try {
            background.execute(() -> {
                if (result.isDone()) return;
                try { result.complete(work.call()); }
                catch (Exception failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }
}
