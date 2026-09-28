package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.persistence.TomlStateFile;
import dev.jasper.app.settings.LegacyKey;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingWriter;
import dev.jasper.app.settings.TomlDocument;
import java.io.UncheckedIOException;
import java.util.function.Function;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

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
public final class ConfigService implements AutoCloseable {
    /**
     * The published configuration.
     *
     * @param legacyKeys old keys the file still uses
     * @param deadTables old tables Migrate Config may remove
     */
    public record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                        List<LegacyKey> legacyKeys, List<List<String>> deadTables) {
        public State {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(file, "file");
            diagnostics = List.copyOf(diagnostics);
            legacyKeys = List.copyOf(legacyKeys);
            deadTables = deadTables.stream().map(List::copyOf).toList();
        }

        /** A state with nothing to migrate. */
        public State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present) {
            this(snapshot, diagnostics, file, present, List.of(), List.of());
        }

        /** Whether Migrate Config has old keys or dead tables to rewrite. */
        public boolean migratable() { return !legacyKeys.isEmpty() || !deadTables.isEmpty(); }
    }

    /**
     * What Migrate Config did.
     *
     * @param changes old keys and tables rewritten; zero when there was nothing to migrate
     * @param backup the copy of the original file
     * @param state the state published afterwards
     */
    public record Migration(int changes, Optional<Path> backup, State state) { }

    private record Fingerprint(boolean present, FileTime modified, long size) { }
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Fingerprint MISSING = new Fingerprint(false, null, 0);

    private static final System.Logger LOG = System.getLogger(ConfigService.class.getName());

    private final Path file;
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
    /** Runs on the worker after each read of the file; see {@link #watchWith}. */
    private volatile Runnable companion;

    public ConfigService(Path file, boolean macOs) {
        this(file, macOs, newWorker(), SwingUtilities::invokeLater);
    }

    /** The supplied single-thread scheduled worker is owned by this service, including shutdown. */
    public ConfigService(Path file, boolean macOs, ScheduledExecutorService worker, Consumer<Runnable> publisher) {
        requireOffEdt();
        this.file = Objects.requireNonNull(file, "file");
        this.macOs = macOs;
        this.worker = Objects.requireNonNull(worker, "worker");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        lastConfig = readState(ConfigSnapshot.defaults(), true);
        initialState = lastConfig;
        state = initialState;
    }

    public State initialState() { return initialState; }
    public boolean macOs() { return macOs; }

    /**
     * Runs {@code companion} on the configuration worker now, and again after every later read that
     * changed the state or was forced (Reload config, Settings), before that state is published. The
     * future completes when this first run finishes. The theme catalog rescans its folder this way.
     */
    public CompletableFuture<Void> watchWith(Runnable companion) {
        Objects.requireNonNull(companion, "companion");
        this.companion = companion;
        return submit(() -> { companion.run(); return null; });
    }

    /** Registers once, publishing the current state before watching for subsequent changes. */
    public void start(Consumer<State> listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lifecycle) {
            if (closed) throw closedException();
            if (this.listener != null) throw new IllegalStateException("Configuration listener already started.");
            this.listener = listener;
            publish();
            worker.scheduleWithFixedDelay(() -> {
                // One failed poll must never cancel scheduleWithFixedDelay for good.
                try { refresh(false, false); }
                catch (Throwable failure) { LOG.log(System.Logger.Level.WARNING, "Configuration poll failed", failure); }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }

    public CompletableFuture<State> reload() {
        // An explicit retry must reach consumers even after I/O accepted an unchanged state.
        return submit(() -> refresh(true, true));
    }

    public CompletableFuture<Path> openSettings(Consumer<Path> opener) {
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

    /** The file's text (the template when it is missing) and how it parsed. */
    private record Source(String text, ConfigLoader.Result parsed) { }

    /** A caller's view of the file is out of date; the state is republished before this reaches the caller. */
    private static final class StaleSetting extends IllegalArgumentException {
        StaleSetting(String message) { super(message); }
    }

    /**
     * Writes settings on the config worker, then publishes them through the normal reload. Refuses a rejected file,
     * a value its setting does not accept, and a setting whose saved value is no longer the one {@code edit} expects.
     */
    public CompletableFuture<State> save(SettingsEdit edit) {
        Objects.requireNonNull(edit, "edit");
        return submit(() -> {
            try {
                return transact(".settings-", source -> {
                    var parsed = source.parsed();
                    if (parsed.rejected()) throw new IllegalArgumentException("Fix config.toml's errors before saving settings.");
                    for (var change : edit.changes().entrySet()) {
                        Setting<?> setting = change.getKey();
                        if (!parsed.values().saved(setting).equals(edit.expected().get(setting)))
                            throw new StaleSetting(setting.name() + " changed on disk. Review the new value and try again.");
                        change.getValue().ifPresent(value -> check(setting, value));
                    }
                    TomlDocument document = TomlDocument.parse(source.text());
                    for (var change : edit.changes().entrySet()) {
                        for (LegacyKey legacy : parsed.legacyKeys())
                            if (legacy.to().equals(change.getKey().key())) document = document.remove(legacy.from());
                        document = write(document, change.getKey(), change.getValue());
                    }
                    String updated = document.text();
                    var checked = ConfigLoader.parse(file, updated, macOs);
                    if (checked.rejected() || edit.changes().entrySet().stream()
                            .anyMatch(change -> !checked.values().saved(change.getKey()).equals(change.getValue())))
                        throw new IllegalArgumentException("Cannot safely save these settings; review config.toml.");
                    return updated;
                });
            } catch (StaleSetting stale) {
                refresh(true, true);
                throw stale;
            }
        });
    }

    /**
     * Migrate Config: backs the file up beside itself, then rewrites old keys to the current layout, on the config
     * worker. Nothing is written when there is nothing to migrate or the file is missing; a rejected file is refused.
     *
     * @param pluginSettingsExist whether a plugin already has its own settings file, so its old table may go
     */
    public CompletableFuture<Migration> migrate(java.util.function.Predicate<String> pluginSettingsExist) {
        Objects.requireNonNull(pluginSettingsExist, "pluginSettingsExist");
        return submit(() -> {
            if (TomlStateFile.readBounded(file, MAX_BYTES, "Configuration").isEmpty())
                return new Migration(0, Optional.empty(), refresh(true, true));
            var planned = new java.util.concurrent.atomic.AtomicReference<ConfigMigration.Outcome>();
            var backup = new java.util.concurrent.atomic.AtomicReference<Path>();
            State state = transact(".migrate-", source -> {
                var outcome = ConfigMigration.plan(source.text(), source.parsed(), pluginSettingsExist);
                if (outcome.isEmpty()) return source.text();
                planned.set(outcome.get());
                try {
                    backup.set(ConfigMigration.backUp(target(), source.text()));
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
                return outcome.get().text();
            });
            return planned.get() == null
                ? new Migration(0, Optional.empty(), state)
                : new Migration(planned.get().changes(), Optional.of(backup.get()), state);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void check(Setting<T> setting, Object value) {
        Optional<String> problem;
        try {
            problem = setting.kind().check((T) value);
        } catch (ClassCastException wrongType) {
            problem = Optional.of("Not a value for this setting.");
        }
        problem.ifPresent(message -> { throw new IllegalArgumentException(setting.name() + ": " + message); });
    }

    @SuppressWarnings("unchecked")
    private static <T> TomlDocument write(TomlDocument document, Setting<T> setting, Optional<?> value) {
        return SettingWriter.write(document, setting, (Optional<T>) value);
    }

    /** Applies explicit shortcut edits on the config worker, then publishes them through normal reload. */
    public CompletableFuture<State> saveKeybindings(Map<String, String> expected,
            Map<String, Optional<String>> changes, List<KeyBindings.Extension> extensions) {
        var before = Map.copyOf(expected);
        var edits = Map.copyOf(changes);
        var actions = List.copyOf(extensions);
        return submit(() -> {
            try {
                return transact(".shortcuts-", source -> {
                    var parsed = source.parsed();
                    if (parsed.rejected() || parsed.diagnostics().stream().anyMatch(d ->
                            d.severity() == ConfigDiagnostic.Severity.ERROR || d.key().startsWith("keybindings")))
                        throw new IllegalArgumentException("Fix the settings file's keybinding or configuration errors before saving shortcuts.");
                    if (!parsed.snapshot().keybindings().equals(before))
                        throw new StaleSetting("Shortcuts changed on disk. Review the updated bindings and try again.");
                    var next = new LinkedHashMap<>(before);
                    edits.forEach((id, value) -> { if (value.isPresent()) next.put(id, value.get()); else next.remove(id); });
                    var oldBindings = KeyBindings.withOverrides(macOs, before).withExtensions(actions).bindings();
                    var newBindings = KeyBindings.withOverrides(macOs, next).withExtensions(actions).bindings();
                    for (var bound : oldBindings.strokes().entrySet()) {
                        if (!edits.containsKey(bound.getKey()) && !newBindings.strokeFor(bound.getKey()).filter(bound.getValue()::equals).isPresent())
                            throw new IllegalArgumentException("Shortcut is used by " + bound.getKey() + ". Reassign it explicitly first.");
                    }
                    for (var edit : edits.entrySet()) {
                        var builtIn = ActionId.forId(edit.getKey());
                        var extension = actions.stream().filter(a -> a.id().equals(edit.getKey())).findFirst();
                        if (builtIn.isEmpty() && extension.isEmpty()) continue;
                        String intended = edit.getValue().orElseGet(() -> builtIn
                            .map(a -> KeyBindings.effectiveDefaultBinding(a, macOs))
                            .orElseGet(() -> extension.orElseThrow().defaultBinding().orElse("none")));
                        if (!KeyBindings.parse(intended, macOs).equals(newBindings.strokeFor(edit.getKey())))
                            throw new IllegalArgumentException("The default shortcut for " + edit.getKey() + " is unavailable. Assign a shortcut explicitly instead.");
                    }
                    String updated = KeybindingText.update(source.text(), edits);
                    var checked = ConfigLoader.parse(file, updated, macOs);
                    if (checked.rejected() || !checked.snapshot().keybindings().equals(next))
                        throw new IllegalArgumentException("Cannot safely save these shortcuts; review the settings file.");
                    return updated;
                });
            } catch (StaleSetting stale) {
                refresh(true, true);
                throw stale;
            }
        });
    }

    /** The file itself, through any symbolic link, or its absolute path when it does not exist yet. */
    private Path target() throws IOException {
        return Files.exists(file) || Files.isSymbolicLink(file) ? file.toRealPath() : file.toAbsolutePath();
    }

    /**
     * Reads the file (the template when it is missing), lets {@code edit} produce the new text or refuse by throwing,
     * then writes it atomically unless the file or its location changed meanwhile, and publishes the reload.
     * An {@link UncheckedIOException} from {@code edit} is rethrown as its cause.
     */
    private State transact(String prefix, Function<Source, String> edit) throws IOException {
        Path target = target();
        var original = TomlStateFile.readBounded(file, MAX_BYTES, "Configuration");
        String source = original.orElseGet(() -> ConfigTemplate.text(macOs));
        String updated;
        try {
            updated = edit.apply(new Source(source, ConfigLoader.parse(file, source, macOs)));
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        // An edit that changes nothing (Migrate Config with nothing to do) leaves the file and its timestamp alone.
        if (original.isPresent() && updated.equals(original.get())) return refresh(true, true);
        if (updated.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB.");
        if (!TomlStateFile.readBounded(file, MAX_BYTES, "Configuration").equals(original))
            throw new IOException("Settings changed while saving. Try again.");
        if (!target.equals(target())) throw new IOException("Settings location changed while saving. Try again.");
        TomlStateFile.writeAtomically(target, prefix, updated);
        return refresh(true, true);
    }

    private State refresh(boolean force, boolean publishUnchanged) {
        synchronized (lifecycle) {
            if (closed) return state;
        }
        State previous = lastConfig;
        lastConfig = readState(lastConfig.snapshot(), force);
        if (force || !lastConfig.equals(previous)) runCompanion();
        State next = lastConfig;
        synchronized (lifecycle) {
            if (!closed && (publishUnchanged || !next.equals(state))) {
                state = next;
                revision++;
                publish();
            }
            return state;
        }
    }

    private void runCompanion() {
        Runnable current = companion;
        if (current == null) return;
        // A failing companion must not stop configuration reads or the change poll.
        try { current.run(); }
        catch (Throwable failure) { LOG.log(System.Logger.Level.WARNING, "Configuration companion failed", failure); }
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
            return parsed.rejected()
                ? new State(lastGood, parsed.diagnostics(), file, true)
                : new State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables());
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
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
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
            var thread = new Thread(runnable, "jasper-config");
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
