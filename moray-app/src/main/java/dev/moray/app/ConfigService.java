package dev.moray.app;

import dev.moray.terminal.Palette;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Application-owned config I/O. Construct off EDT; subsequent work is serialized on one worker. */
final class ConfigService implements AutoCloseable {
    record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                 Palette palette) {
        State {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(palette, "palette");
            diagnostics = List.copyOf(diagnostics);
        }

        State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present) {
            this(snapshot, diagnostics, file, present, seed(snapshot));
        }

        private static Palette seed(ConfigSnapshot snapshot) {
            return snapshot.colors().theme().equals("moray-light")
                ? Palette.morayLight() : Palette.morayDark();
        }
    }

    private record Fingerprint(boolean present, FileTime modified, long size) { }
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Fingerprint MISSING = new Fingerprint(false, null, 0);

    private final Path file;
    private final ThemeFiles themeFiles;
    private final boolean macOs;
    private final ScheduledExecutorService worker;
    private final Consumer<Runnable> publisher;
    private final State initialState;
    private final Object lifecycle = new Object();
    private final Set<CompletableFuture<?>> pending = new HashSet<>();
    // The fingerprint is confined to construction/the worker; lifecycle guards all other mutable state.
    private Fingerprint fingerprint;
    private State state;
    private State lastConfig;
    private Consumer<State> listener;
    private long revision;
    private boolean closed;

    ConfigService(Path file, boolean macOs) {
        this(file, file.toAbsolutePath().getParent().resolve("themes"), macOs);
    }

    ConfigService(Path file, Path themes, boolean macOs) {
        this(file, themes, macOs, newWorker(), SwingUtilities::invokeLater);
    }

    /** The supplied single-thread scheduled worker is owned by this service, including shutdown. */
    ConfigService(Path file, boolean macOs, ScheduledExecutorService worker, Consumer<Runnable> publisher) {
        this(file, file.toAbsolutePath().getParent().resolve("themes"), macOs, worker, publisher);
    }

    /** The supplied single-thread scheduled worker is owned by this service, including shutdown. */
    ConfigService(Path file, Path themes, boolean macOs, ScheduledExecutorService worker,
                  Consumer<Runnable> publisher) {
        requireOffEdt();
        this.file = Objects.requireNonNull(file, "file");
        this.themeFiles = new ThemeFiles(Objects.requireNonNull(themes, "themes"));
        this.macOs = macOs;
        this.worker = Objects.requireNonNull(worker, "worker");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        lastConfig = readState(ConfigSnapshot.defaults(), true);
        initialState = join(lastConfig, true);
        state = initialState;
    }

    State initialState() { return initialState; }
    boolean macOs() { return macOs; }

    /** Registers once, publishing the current state before watching for subsequent changes. */
    void start(Consumer<State> listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lifecycle) {
            if (closed) throw closedException();
            if (this.listener != null) throw new IllegalStateException("Configuration listener already started.");
            this.listener = listener;
            publish();
            worker.scheduleWithFixedDelay(() -> refresh(false, false), 1, 1, TimeUnit.SECONDS);
        }
    }

    CompletableFuture<State> reload() {
        // An explicit retry must reach consumers even after I/O accepted an unchanged state.
        return submit(() -> refresh(true, true));
    }

    CompletableFuture<Path> openSettings(Consumer<Path> opener) {
        Objects.requireNonNull(opener, "opener");
        return submit(() -> {
            ConfigTemplate.ensureExists(file, macOs);
            refresh(true, false);
            synchronized (lifecycle) {
                if (closed) throw closedException();
            }
            opener.accept(file);
            return file;
        });
    }

    private State refresh(boolean force, boolean publishUnchanged) {
        synchronized (lifecycle) {
            if (closed) return state;
        }
        lastConfig = readState(lastConfig.snapshot(), force);
        State next = join(lastConfig, force);
        synchronized (lifecycle) {
            if (!closed && (publishUnchanged || !next.equals(state))) {
                state = next;
                revision++;
                publish();
            }
            return state;
        }
    }

    private State readState(ConfigSnapshot lastGood, boolean force) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            Fingerprint next = new Fingerprint(true, attributes.lastModifiedTime(), attributes.size());
            if (!force && next.equals(fingerprint)) return lastConfig;
            fingerprint = next;
            if (!attributes.isRegularFile()) {
                return readError(lastGood, "Configuration must be a readable regular file; check the configured path.");
            }
            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) {
                return readError(lastGood, "Configuration exceeds 1 MiB; reduce the file size and reload.");
            }
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            var parsed = ConfigLoader.parse(file, text, macOs);
            return new State(parsed.rejected() ? lastGood : parsed.snapshot(), parsed.diagnostics(), file, true);
        } catch (NoSuchFileException ignored) {
            return missingState(lastGood);
        } catch (CharacterCodingException ignored) {
            return readError(lastGood, "Configuration is not valid UTF-8; save it as UTF-8 and reload.");
        } catch (IOException | SecurityException ignored) {
            // Retry access failures on the next poll even when metadata did not change.
            fingerprint = null;
            return readError(lastGood, "Cannot read configuration; check the file and its permissions, then reload.");
        }
    }

    private State missingState(ConfigSnapshot lastGood) {
        // Some providers report ENOTDIR as NoSuchFileException. A file obstructing the
        // parent path is an access error, not deletion of a usable config location.
        for (Path parent = file.toAbsolutePath().getParent(); parent != null; parent = parent.getParent()) {
            try {
                if (!Files.readAttributes(parent, BasicFileAttributes.class).isDirectory()) {
                    fingerprint = null;
                    return readError(lastGood, "Configuration parent must be a directory; check the configured path.");
                }
                break;
            } catch (NoSuchFileException ignored) {
                // Missing parents are normal until Settings is explicitly opened.
            } catch (IOException | SecurityException ignored) {
                fingerprint = null;
                return readError(lastGood, "Cannot access configuration parent; check the path and its permissions.");
            }
        }
        fingerprint = MISSING;
        return new State(ConfigSnapshot.defaults(), List.of(), file, false);
    }

    private State join(State config, boolean force) {
        ThemeFiles.Result selected = themeFiles.refresh(config.snapshot().colors(), force);
        var diagnostics = new ArrayList<>(config.diagnostics());
        diagnostics.addAll(selected.diagnostics());
        return new State(config.snapshot(), diagnostics, config.file(), config.present(), selected.palette());
    }

    private State readError(ConfigSnapshot lastGood, String message) {
        return new State(lastGood, List.of(new ConfigDiagnostic(
            ConfigDiagnostic.Severity.ERROR, file, 0, 0, "", message)), file, true);
    }

    /** Called under lifecycle: the publication must still be current when it reaches the EDT. */
    private void publish() {
        if (listener == null) return;
        long expectedRevision = revision;
        State published = state;
        publisher.accept(() -> {
            synchronized (lifecycle) {
                if (!closed && expectedRevision == revision) listener.accept(published);
            }
        });
    }

    private <T> CompletableFuture<T> submit(Callable<T> operation) {
        var result = new CompletableFuture<T>();
        synchronized (lifecycle) {
            if (closed) return CompletableFuture.failedFuture(closedException());
            pending.add(result);
            try {
                worker.execute(() -> {
                    try {
                        synchronized (lifecycle) {
                            if (closed) throw closedException();
                        }
                        T value = operation.call();
                        synchronized (lifecycle) {
                            if (closed) result.completeExceptionally(closedException());
                            else result.complete(value);
                        }
                    } catch (Exception exception) {
                        result.completeExceptionally(exception);
                    } finally {
                        synchronized (lifecycle) {
                            pending.remove(result);
                        }
                    }
                });
            } catch (RejectedExecutionException exception) {
                pending.remove(result);
                result.completeExceptionally(exception);
            }
        }
        return result;
    }

    @Override public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            worker.shutdownNow();
            // shutdownNow removes queued tasks without running their finally/completion blocks.
            for (var result : List.copyOf(pending)) result.completeExceptionally(closedException());
            pending.clear();
        }
    }

    private static ScheduledExecutorService newWorker() {
        requireOffEdt();
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "moray-config");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void requireOffEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Initial configuration must be read off the Event Dispatch Thread.");
        }
    }

    private static IllegalStateException closedException() {
        return new IllegalStateException("Configuration service is closed.");
    }
}
