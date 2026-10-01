package dev.jasper.remote.tunnels;

import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
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
 * The saved tunnels, read from and written to tunnels.toml the way {@code hosts.HostStore} keeps hosts: reads and writes
 * run on {@code background}; the list, the error and the listeners live on the UI thread. A file that fails to parse
 * keeps the last good tunnels and refuses saves until it parses again.
 */
public final class TunnelStore {
    private final Path file;
    private final Executor background, ui;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private List<Tunnel> tunnels = List.of();
    private String error;
    private List<String> warnings = List.of();
    private long size = -1;
    private FileTime modified;
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    private record Read(List<Tunnel> tunnels, List<String> warnings, String error, long size, FileTime modified) { }

    public TunnelStore(Path file, Executor background, Executor ui) { this.file = file; this.background = background; this.ui = ui; }

    public Path file() { return file; }
    public List<Tunnel> tunnels() { return tunnels; }
    public Optional<Tunnel> tunnel(UUID id) { return tunnels.stream().filter(tunnel -> tunnel.id().equals(id)).findFirst(); }
    /** The last parse failure, present while the file is broken. */
    public Optional<String> error() { return Optional.ofNullable(error); }
    /** The entries the last good read skipped. */
    public List<String> warnings() { return warnings; }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Reads the file unconditionally. */
    public void load() { enqueue(() -> onBackground(() -> read(true)).thenAccept(this::apply)); }

    /** Reads the file when its size or modification time changed; the plugin calls this once a second. */
    public void poll() { enqueue(() -> onBackground(() -> read(false)).thenAccept(read -> { if (read != null) apply(read); })); }

    public CompletableFuture<Void> put(Tunnel tunnel) {
        return mutate(current -> {
            var next = new ArrayList<Tunnel>();
            boolean replaced = false;
            for (Tunnel existing : current) {
                if (existing.id().equals(tunnel.id())) { next.add(tunnel); replaced = true; } else next.add(existing);
            }
            if (!replaced) next.add(tunnel);
            return next;
        });
    }

    public CompletableFuture<Void> remove(UUID id) { return mutate(current -> current.stream().filter(tunnel -> !tunnel.id().equals(id)).toList()); }

    private CompletableFuture<Void> mutate(Function<List<Tunnel>, List<Tunnel>> mutation) {
        var result = new CompletableFuture<Void>();
        enqueue(() -> onBackground(() -> {
            Read current = read(true);
            if (current.error() != null) return current;
            List<Tunnel> next = List.copyOf(mutation.apply(current.tunnels()));
            TunnelFile.validate(next);
            write(TunnelFile.format(next));
            return read(true);
        }).handle((read, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else {
                apply(read);
                if (read.error() != null) result.completeExceptionally(new IOException("tunnels.toml has errors; fix it before saving: " + read.error()));
                else result.complete(null);
            }
            return null;
        }));
        return result;
    }

    private void apply(Read read) {
        size = read.size(); modified = read.modified(); warnings = read.warnings();
        if (read.error() == null) { tunnels = read.tunnels(); error = null; }
        else error = read.error();
        listeners.forEach(Runnable::run);
    }

    // Worker side. Returns null when nothing changed and the read was conditional.
    private Read read(boolean force) {
        try { return readFile(force); }
        catch (IOException failure) { return new Read(List.of(), List.of(), "tunnels.toml is unreadable: " + failure.getMessage(), -2, null); }
    }

    private Read readFile(boolean force) throws IOException {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return force || size != -1 ? new Read(List.of(), List.of(), null, -1, null) : null;
        if (!Files.isRegularFile(file)) throw new IOException("not a readable regular file");
        long currentSize = Files.size(file);
        FileTime currentModified = Files.getLastModifiedTime(file);
        if (!force && currentSize == size && currentModified.equals(modified)) return null;
        if (currentSize > TunnelFile.MAX_BYTES)
            return new Read(List.of(), List.of(), "tunnels.toml is larger than " + TunnelFile.MAX_BYTES + " bytes", currentSize, currentModified);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            TunnelFile.Parsed parsed = TunnelFile.parse(text);
            return new Read(parsed.tunnels(), parsed.warnings(), null, currentSize, currentModified);
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
