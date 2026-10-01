package dev.jasper.fileserver.definition;

import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The saved servers, read from and written to servers.toml. Reads and writes run on {@code background}, one at a
 * time; the list, the error and the listeners live on the UI thread. A file that fails to parse keeps the last good
 * servers and refuses saves until it parses again.
 */
public final class DefinitionStore {
    private final Path file;
    private final Executor background, ui;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private List<ServerDefinition> definitions = List.of();
    private List<String> warnings = List.of();
    private String error;
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    private record Read(List<ServerDefinition> definitions, List<String> warnings, String error) { }

    public DefinitionStore(Path file, Executor background, Executor ui) { this.file = file; this.background = background; this.ui = ui; }

    public Path file() { return file; }
    public List<ServerDefinition> definitions() { return definitions; }
    public Optional<ServerDefinition> definition(UUID id) { return definitions.stream().filter(d -> d.id().equals(id)).findFirst(); }
    /** The last read failure, present while the file is broken. */
    public Optional<String> error() { return Optional.ofNullable(error); }
    /** Entries the last read skipped, one message each. */
    public List<String> warnings() { return warnings; }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Reads the file; completes normally even when it is broken ({@link #error()} says why). */
    public CompletableFuture<Void> load() {
        var result = new CompletableFuture<Void>();
        enqueue(() -> onBackground(this::read).handle((read, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else { apply(read); result.complete(null); }
            return null;
        }));
        return result;
    }

    /** Adds or replaces a server by id; refuses a name another server uses, ignoring case. */
    public CompletableFuture<Void> put(ServerDefinition definition) {
        return mutate(current -> {
            for (ServerDefinition other : current)
                if (!other.id().equals(definition.id()) && other.name().equalsIgnoreCase(definition.name()))
                    throw new IllegalArgumentException("A server named " + other.name() + " exists");
            var next = new ArrayList<ServerDefinition>();
            boolean replaced = false;
            for (ServerDefinition existing : current) {
                if (existing.id().equals(definition.id())) { next.add(definition); replaced = true; } else next.add(existing);
            }
            if (!replaced) next.add(definition);
            return next;
        });
    }

    public CompletableFuture<Void> remove(UUID id) {
        return mutate(current -> current.stream().filter(d -> !d.id().equals(id)).toList());
    }

    private CompletableFuture<Void> mutate(Function<List<ServerDefinition>, List<ServerDefinition>> mutation) {
        var result = new CompletableFuture<Void>();
        enqueue(() -> onBackground(() -> {
            Read current = read();
            if (current.error() != null) return current;
            write(DefinitionFile.format(List.copyOf(mutation.apply(current.definitions()))));
            return read();
        }).handle((read, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else {
                apply(read);
                if (read.error() != null) result.completeExceptionally(new IOException("servers.toml has errors; fix it before saving: " + read.error()));
                else result.complete(null);
            }
            return null;
        }));
        return result;
    }

    private void apply(Read read) {
        warnings = read.warnings();
        if (read.error() == null) { definitions = read.definitions(); error = null; }
        else error = read.error();
        listeners.forEach(Runnable::run);
    }

    private Read read() {
        try {
            if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return new Read(List.of(), List.of(), null);
            if (!Files.isRegularFile(file)) return new Read(List.of(), List.of(), "servers.toml is not a regular file");
            if (Files.size(file) > DefinitionFile.MAX_BYTES) return new Read(List.of(), List.of(), "servers.toml is larger than " + DefinitionFile.MAX_BYTES + " bytes");
            String text = Files.readString(file, StandardCharsets.UTF_8);
            try {
                var parsed = DefinitionFile.parse(text);
                return new Read(parsed.definitions(), parsed.warnings(), null);
            } catch (IOException bad) {
                return new Read(List.of(), List.of(), bad.getMessage());
            }
        } catch (IOException unreadable) {
            return new Read(List.of(), List.of(), "servers.toml is unreadable: " + unreadable.getMessage());
        }
    }

    private void write(String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    /** Serialises operations: each starts after the previous one's future settles. */
    private void enqueue(Supplier<CompletableFuture<Void>> operation) {
        queue = queue.handle((ignored, failure) -> null).thenCompose(ignored -> operation.get()).handle((ignored, failure) -> null);
    }

    private <T> CompletableFuture<T> onBackground(Callable<T> work) {
        var result = new CompletableFuture<T>();
        background.execute(() -> {
            T value;
            try { value = work.call(); } catch (Throwable failure) { ui.execute(() -> result.completeExceptionally(failure)); return; }
            ui.execute(() -> result.complete(value));
        });
        return result;
    }
}
