package dev.jasper.remote.hosts;

import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The saved hosts, read from and written to hosts.toml. Reads and writes run on {@code background};
 * the list, the error and the listeners live on the UI thread. A file that fails to parse keeps the
 * last good hosts and refuses saves until it parses again.
 */
public final class HostStore {
    private final Path file;
    private final Executor background, ui;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private List<RemoteHost> hosts = List.of();
    private String error;
    private List<String> warnings = List.of();
    private long size = -1;
    private FileTime modified;
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    private record Read(List<RemoteHost> hosts, List<String> warnings, String error, long size, FileTime modified) { }

    public HostStore(Path file, Executor background, Executor ui) { this.file = file; this.background = background; this.ui = ui; }

    public List<String> warnings() { return warnings; }

    public Path file() { return file; }
    public List<RemoteHost> hosts() { return hosts; }
    public Optional<RemoteHost> host(UUID id) { return hosts.stream().filter(host -> host.id().equals(id)).findFirst(); }
    /** The last parse failure, present while the file is broken. */
    public Optional<String> error() { return Optional.ofNullable(error); }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Reads the file unconditionally. */
    public void load() { enqueue(() -> onBackground(() -> read(true)).thenAccept(this::apply)); }

    /** Reads the file when its size or modification time changed; the plugin calls this once a second. */
    public void poll() { enqueue(() -> onBackground(() -> read(false)).thenAccept(read -> { if (read != null) apply(read); })); }

    public CompletableFuture<Void> put(RemoteHost host) {
        return mutate(current -> {
            var next = new ArrayList<RemoteHost>();
            boolean replaced = false;
            for (RemoteHost existing : current) {
                if (existing.id().equals(host.id())) { next.add(host); replaced = true; } else next.add(existing);
            }
            if (!replaced) next.add(host);
            return next;
        });
    }

    public CompletableFuture<Void> remove(UUID id) { return mutate(current -> current.stream().filter(host -> !host.id().equals(id)).toList()); }

    /** Writes an explicitly supplied replacement after checking the current disk file. */
    public CompletableFuture<Void> save(List<RemoteHost> next) {
        List<RemoteHost> copy = List.copyOf(next);
        return mutate(current -> copy);
    }

    private CompletableFuture<Void> mutate(java.util.function.Function<List<RemoteHost>, List<RemoteHost>> mutation) {
        var result = new CompletableFuture<Void>();
        enqueue(() -> onBackground(() -> {
            Read current = read(true);
            if (current.error() != null) return current;
            List<RemoteHost> next = List.copyOf(mutation.apply(current.hosts()));
            HostFile.validate(next);
            write(HostFile.format(next));
            return read(true);
        }).handle((read, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else {
                apply(read);
                if (read.error() != null) result.completeExceptionally(new IOException("hosts.toml has errors; fix it before saving: " + read.error()));
                else result.complete(null);
            }
            return null;
        }));
        return result;
    }

    private void apply(Read read) {
        size = read.size(); modified = read.modified(); warnings = read.warnings();
        if (read.error() == null) { hosts = read.hosts(); error = null; }
        else error = read.error();
        listeners.forEach(Runnable::run);
    }

    // Worker side. Returns null when nothing changed and the read was conditional.
    private Read read(boolean force) {
        try { return readFile(force); }
        catch (IOException failure) { return new Read(List.of(), List.of(), "hosts.toml is unreadable: " + failure.getMessage(), -2, null); }
    }

    private Read readFile(boolean force) throws IOException {
        if (Files.notExists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return force || size != -1 ? new Read(List.of(), List.of(), null, -1, null) : null;
        if (!Files.isRegularFile(file)) throw new IOException("not a readable regular file");
        long currentSize = Files.size(file);
        FileTime currentModified = Files.getLastModifiedTime(file);
        if (!force && currentSize == size && currentModified.equals(modified)) return null;
        if (currentSize > HostFile.MAX_BYTES) return new Read(List.of(), List.of(), "hosts.toml is larger than " + HostFile.MAX_BYTES + " bytes", currentSize, currentModified);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            HostFile.Parsed parsed = HostFile.parse(text);
            return new Read(parsed.hosts(), parsed.warnings(), null, currentSize, currentModified);
        } catch (IOException bad) {
            return new Read(List.of(), List.of(), bad.getMessage(), currentSize, currentModified);
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

    private <T> CompletableFuture<T> onBackground(java.util.concurrent.Callable<T> work) {
        var result = new CompletableFuture<T>();
        background.execute(() -> {
            T value;
            try { value = work.call(); } catch (Throwable failure) { ui.execute(() -> result.completeExceptionally(failure)); return; }
            ui.execute(() -> result.complete(value));
        });
        return result;
    }
}
