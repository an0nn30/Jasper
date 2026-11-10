package dev.jasper.app.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

/**
 * Which application opens each file type on this system, and the means to make it Jasper and to hand it back. The
 * system-specific part is a {@link Backend}; this class adds the extension rule, the previous-handler bookkeeping
 * ({@link PreviousHandlers}) and the threading: backend work runs on one worker under one lock, so calls never overlap,
 * and the futures returned never complete exceptionally. A backend exception becomes {@link Outcome.Failed} for the
 * extension's types (for {@link #status}, a handler {@code Other("", "")}) with a log line.
 */
public final class FileAssociations {
    private static final System.Logger LOG = System.getLogger(FileAssociations.class.getName());
    private static final Pattern EXTENSION = Pattern.compile("\\.[a-z0-9_+-]{1,16}");
    private static final java.util.Set<String> BROWSER_OWNED = java.util.Set.of(".html", ".htm");
    private static final Duration INTERVAL = Duration.ofSeconds(5);

    /** Who opens a file type. */
    public sealed interface Handler {
        /** Jasper. */
        record Jasper() implements Handler { }

        /**
         * Another application.
         *
         * @param id its bundle id, ProgId or desktop id
         * @param name its display name; the id when unknown
         */
        record Other(String id, String name) implements Handler { }

        /** No application. */
        record None() implements Handler { }
    }

    /**
     * One type of an extension.
     *
     * @param type the system's type (a UTI, a ProgId or a MIME type)
     * @param handler who opens it now
     * @param previous the saved handler, possibly {@link Handler.None}; empty when nothing is saved
     */
    public record TypeStatus(String type, Handler handler, Optional<Handler> previous) { }

    /**
     * An extension's state.
     *
     * @param extension the normalised extension
     * @param types empty when the extension cannot be claimed here
     */
    public record ExtensionStatus(String extension, List<TypeStatus> types) {
        public ExtensionStatus {
            types = List.copyOf(types);
        }
    }

    /** Why file associations cannot be used at all. */
    public enum Unavailable { DEVELOPMENT_RUN, NOT_INSTALLED, NO_XDG_MIME, UNSUPPORTED_SYSTEM }

    /** Why an extension cannot be claimed. */
    public enum Refusal { INVALID, BROWSER_OWNED, NO_SYSTEM_TYPE, NOT_TEXT }

    /** Whether an extension can be claimed. */
    public sealed interface Claimability {
        /**
         * @param types the system types to claim
         * @param declares whether claiming first declares the extension to the system
         */
        record Claimable(List<String> types, boolean declares) implements Claimability {
            public Claimable {
                types = List.copyOf(types);
            }
        }

        /** @param refusal why not */
        record NotClaimable(Refusal refusal) implements Claimability { }
    }

    /** What happened to one type. */
    public sealed interface Outcome {
        /** Done. */
        record Done() implements Outcome { }

        /** The user finishes in the system's Settings. */
        record InSettings() implements Outcome { }

        /** Nothing was saved to hand back. */
        record NoPrevious() implements Outcome { }

        /** The saved application is no longer installed. */
        record PreviousGone() implements Outcome { }

        /** The extension was not attempted: it cannot be claimed here. @param refusal why */
        record Refused(Refusal refusal) implements Outcome { }

        /** @param detail the system's own message, for the log and the row */
        record Failed(String detail) implements Outcome { }
    }

    /**
     * One type's result.
     *
     * @param extension the normalised extension
     * @param type the system type; empty when the extension was refused
     * @param outcome what happened
     */
    public record TypeResult(String extension, String type, Outcome outcome) { }

    /** The system-specific part: one implementation per operating system. */
    interface Backend {
        Optional<Unavailable> unavailable();

        /** Called only with a normalised extension the facade accepted. */
        Claimability claimability(String extension);

        Handler handler(String type) throws IOException;

        /** One call per claim, so Windows opens Settings once. */
        List<TypeResult> claim(Map<String, List<String>> typesByExtension);

        /** Hands back saved handlers, which may include {@link Handler.None}. */
        List<TypeResult> restore(Map<String, Map<String, Handler>> previousByTypeByExtension);

        /** Drops a declaration Jasper made for a custom extension; a no-op where none was made. */
        void forget(String extension);
    }

    private final Executor worker;
    private final Backend backend;
    private final PreviousHandlers store;
    private final Clock clock;
    /** Serialises every backend call. */
    private final Object lock = new Object();
    private volatile Optional<Unavailable> unavailable;
    private volatile Runnable listener = () -> { };
    /** The last {@code status} extensions and answer. Guarded by {@code lock}. */
    private List<String> lastExtensions = List.of();
    private List<ExtensionStatus> lastAnswer = List.of();
    /** Guarded by this. */
    private Instant checked;
    private boolean checking;

    FileAssociations(Executor worker, Backend backend, PreviousHandlers store) {
        this(worker, backend, store, Clock.systemUTC());
    }

    FileAssociations(Executor worker, Backend backend, PreviousHandlers store, Clock clock) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The facade for this operating system, its work on one virtual thread per task named
     * {@code jasper-file-associations}. A system with no backend gets {@link #unsupported()}'s.
     *
     * @param osName {@code os.name}
     * @param dirs where {@code file-associations.toml} lives
     * @param environment the process environment
     * @param home the user's home folder
     * @param openSettings opens an {@code ms-settings:} link on Windows; it throws when it cannot
     */
    public static FileAssociations forThisPlatform(String osName, AppDirs dirs, Map<String, String> environment,
            Path home, Consumer<URI> openSettings) {
        Backend backend;
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) {
            backend = new MacFileAssociations(FileTypeCatalog.builtIn(), MacFileAssociations.MacCalls.system());
        } else if (os.startsWith("windows")) {
            backend = new WindowsFileAssociations(WindowsFileAssociations.Registry.system(), openSettings);
        } else if (os.startsWith("linux")) {
            backend = new LinuxFileAssociations(FileTypeCatalog.builtIn(), environment, home,
                    LinuxFileAssociations.Tools.system());
        } else {
            return unsupported();
        }
        return new FileAssociations(work -> Thread.ofVirtual().name("jasper-file-associations").start(work), backend,
                new PreviousHandlers(dirs.fileAssociations()));
    }

    /**
     * Opens a link with the desktop's browse action, as {@link WindowsFileAssociations} wants it: an
     * {@link UnsupportedOperationException} when there is no desktop or no browse action, an
     * {@link UncheckedIOException} when it fails.
     */
    public static Consumer<URI> desktopOpener() {
        return opener(() -> Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE),
                uri -> Desktop.getDesktop().browse(uri));
    }

    /** A link that opens. */
    @FunctionalInterface
    interface Browse {
        void browse(URI uri) throws IOException;
    }

    static Consumer<URI> opener(java.util.function.BooleanSupplier canBrowse, Browse browse) {
        return uri -> {
            if (!canBrowse.getAsBoolean()) {
                throw new UnsupportedOperationException("The desktop cannot open links");
            }
            try {
                browse.browse(uri);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        };
    }

    /** The backend, for tests. */
    Backend backendForTests() {
        return backend;
    }

    /** A facade for a system with no file associations: always {@link Unavailable#UNSUPPORTED_SYSTEM}. */
    public static FileAssociations unsupported() {
        return new FileAssociations(Runnable::run, new UnsupportedBackend(),
                new PreviousHandlers(Path.of(System.getProperty("java.io.tmpdir"), "jasper-no-file-associations.toml")));
    }

    /**
     * Lower-cases {@code extension} and adds the dot.
     *
     * @return the extension, or empty unless it is a dot and 1 to 16 of {@code a-z 0-9 _ + -}
     */
    public static Optional<String> normalise(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String text = extension.strip().toLowerCase(Locale.ROOT);
        if (!text.startsWith(".")) {
            text = "." + text;
        }
        return EXTENSION.matcher(text).matches() ? Optional.of(text) : Optional.empty();
    }

    /** Why file associations cannot be used, or empty when they can. Any thread; the first answer is kept. */
    public Optional<Unavailable> unavailable() {
        Optional<Unavailable> known = unavailable;
        if (known == null) {
            synchronized (lock) {
                known = unavailable;
                if (known == null) {
                    try {
                        known = Objects.requireNonNull(backend.unavailable(), "answer");
                    } catch (RuntimeException | LinkageError failure) {
                        LOG.log(System.Logger.Level.WARNING, "Could not tell whether file associations are available", failure);
                        known = Optional.of(Unavailable.UNSUPPORTED_SYSTEM);
                    }
                    unavailable = known;
                }
            }
        }
        return known;
    }

    /** Whether {@code extension} can be claimed. Invalid and Browser-owned extensions never reach the backend. */
    public CompletableFuture<Claimability> claimability(String extension) {
        Optional<String> normal = normalise(extension);
        if (normal.isEmpty()) {
            return CompletableFuture.completedFuture(new Claimability.NotClaimable(Refusal.INVALID));
        }
        if (BROWSER_OWNED.contains(normal.get())) {
            return CompletableFuture.completedFuture(new Claimability.NotClaimable(Refusal.BROWSER_OWNED));
        }
        return run(() -> claimabilityOf(normal.get()), () -> new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE));
    }

    /** Who opens each type of each extension now; an extension that cannot be claimed has no types. */
    public CompletableFuture<List<ExtensionStatus>> status(List<String> extensions) {
        List<String> asked = List.copyOf(extensions);
        return run(() -> {
            synchronized (lock) {
                List<ExtensionStatus> answer = statusOf(asked);
                lastExtensions = asked;
                lastAnswer = answer;
                return answer;
            }
        }, () -> List.of());
    }

    /**
     * Makes Jasper the handler of every claimable type of {@code extensions}. Before claiming a type it reads its
     * handler and, when that is not Jasper and nothing is saved, saves it: the earliest previous handler wins, and a
     * type Jasper already owns saves nothing. A type whose claim failed saves nothing. An extension that cannot be
     * claimed has one result with an empty type and a {@link Outcome.Failed} naming the {@link Refusal}.
     */
    public CompletableFuture<List<TypeResult>> claim(List<String> extensions) {
        List<String> asked = List.copyOf(extensions);
        return run(() -> {
            List<TypeResult> results;
            synchronized (lock) {
                results = claimOf(asked);
                restatus();
            }
            changed();
            return results;
        }, () -> List.of());
    }

    /**
     * Hands back the saved handler of every type of {@code extensions} that has one, including a saved
     * {@link Handler.None}. A {@link Outcome.Done} deletes the entry; {@link Outcome.PreviousGone}, {@link Outcome.NoPrevious}
     * and failures keep it.
     */
    public CompletableFuture<List<TypeResult>> restore(List<String> extensions) {
        List<String> asked = List.copyOf(extensions);
        return run(() -> {
            List<TypeResult> results;
            synchronized (lock) {
                results = restoreOf(asked);
                restatus();
            }
            changed();
            return results;
        }, () -> List.of());
    }

    /** Drops what Jasper declared for a custom extension. */
    public CompletableFuture<Void> forget(String extension) {
        Optional<String> normal = normalise(extension);
        if (normal.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return run(() -> {
            synchronized (lock) {
                try {
                    backend.forget(normal.get());
                } catch (RuntimeException | LinkageError failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not forget " + normal.get(), failure);
                }
            }
            return null;
        }, () -> null);
    }

    /** Sets the one listener, run on the EDT after each claim or restore and after a refresh that found a change. */
    public void onChanged(Runnable listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Asks again for the last {@link #status} extensions on the worker, unless one is running or the last began less
     * than 5 seconds ago; a clock set back never holds a check back. Tells the listener when the answer differs.
     */
    public void refresh() {
        synchronized (this) {
            Instant now = clock.instant();
            if (checking) {
                return;
            }
            if (checked != null && !now.isBefore(checked) && now.isBefore(checked.plus(INTERVAL))) {
                return;
            }
            checked = now;
            checking = true;
        }
        try {
            worker.execute(this::refreshNow);
        } catch (RejectedExecutionException closed) {
            finished();
            LOG.log(System.Logger.Level.DEBUG, "The file association check was not run", closed);
        } catch (RuntimeException failure) {
            finished();
            LOG.log(System.Logger.Level.WARNING, "The file association check could not be started", failure);
        } catch (Error failure) {
            finished();
            throw failure;
        }
    }

    private void refreshNow() {
        try {
            boolean differs;
            synchronized (lock) {
                if (lastExtensions.isEmpty()) {
                    return;
                }
                List<ExtensionStatus> answer = statusOf(lastExtensions);
                differs = !answer.equals(lastAnswer);
                lastAnswer = answer;
            }
            if (differs) {
                changed();
            }
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The file association check failed", failure);
        } finally {
            finished();
        }
    }

    private synchronized void finished() {
        checking = false;
    }

    private void changed() {
        SwingUtilities.invokeLater(() -> listener.run());
    }

    private <T> CompletableFuture<T> run(Supplier<T> work, Supplier<T> fallback) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    future.complete(work.get());
                } catch (Throwable failure) {
                    LOG.log(System.Logger.Level.WARNING, "File association work failed", failure);
                    future.complete(fallback.get());
                    if (failure instanceof VirtualMachineError error) {
                        throw error;
                    }
                }
            });
        } catch (RuntimeException notRun) {
            LOG.log(System.Logger.Level.WARNING, "File association work was not run", notRun);
            future.complete(fallback.get());
        }
        return future;
    }

    /** Asks status again for the last extensions after a change, so the next refresh sees no second change. Under lock. */
    private void restatus() {
        if (!lastExtensions.isEmpty()) {
            lastAnswer = statusOf(lastExtensions);
        }
    }

    private Claimability claimabilityOf(String extension) {
        try {
            synchronized (lock) {
                return Objects.requireNonNull(backend.claimability(extension), "claimability");
            }
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not tell whether " + extension + " can be claimed", failure);
            return new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE);
        }
    }

    /** The extension's claimability: invalid and Browser-owned ones never reach the backend. */
    private Claimability resolve(String extension) {
        Optional<String> normal = normalise(extension);
        if (normal.isEmpty()) {
            return new Claimability.NotClaimable(Refusal.INVALID);
        }
        if (BROWSER_OWNED.contains(normal.get())) {
            return new Claimability.NotClaimable(Refusal.BROWSER_OWNED);
        }
        return claimabilityOf(normal.get());
    }

    private List<String> claimableTypes(String extension) {
        return resolve(extension) instanceof Claimability.Claimable claimable ? claimable.types() : List.of();
    }

    private List<ExtensionStatus> statusOf(List<String> extensions) {
        List<ExtensionStatus> out = new java.util.ArrayList<>();
        for (String extension : extensions) {
            String normal = normalise(extension).orElse(extension);
            List<TypeStatus> types = new java.util.ArrayList<>();
            for (String type : claimableTypes(extension)) {
                Handler handler;
                try {
                    handler = Objects.requireNonNull(backend.handler(type), "handler");
                } catch (IOException | RuntimeException | LinkageError failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not read the handler of " + type, failure);
                    handler = new Handler.Other("", "");
                }
                types.add(new TypeStatus(type, handler, store.get(type)));
            }
            out.add(new ExtensionStatus(normal, types));
        }
        return out;
    }

    private List<TypeResult> claimOf(List<String> extensions) {
        List<TypeResult> results = new java.util.ArrayList<>();
        Map<String, List<String>> toClaim = new LinkedHashMap<>();
        /* The types whose previous handler this call saved, before the claim, so a crash cannot lose it. */
        java.util.Set<String> created = new java.util.HashSet<>();
        for (String extension : extensions) {
            String normalName = normalise(extension).orElse(extension);
            Claimability claimability = resolve(extension);
            if (!(claimability instanceof Claimability.Claimable claimable)) {
                Refusal why = ((Claimability.NotClaimable) claimability).refusal();
                results.add(new TypeResult(normalName, "", new Outcome.Refused(why)));
                continue;
            }
            Optional<String> normal = Optional.of(normalName);
            Map<String, Handler> read = new LinkedHashMap<>();
            String failure = null;
            for (String type : claimable.types()) {
                try {
                    read.put(type, Objects.requireNonNull(backend.handler(type), "handler"));
                } catch (IOException | RuntimeException | LinkageError e) {
                    LOG.log(System.Logger.Level.WARNING, "Could not read the handler of " + type, e);
                    failure = String.valueOf(e.getMessage());
                    break;
                }
            }
            if (failure != null) {
                for (String type : claimable.types()) {
                    results.add(new TypeResult(normal.get(), type, new Outcome.Failed(failure)));
                }
                continue;
            }
            for (Map.Entry<String, Handler> entry : read.entrySet()) {
                Handler was = entry.getValue();
                if (!(was instanceof Handler.Jasper) && store.get(entry.getKey()).isEmpty()) {
                    store.put(entry.getKey(), was);
                    created.add(entry.getKey());
                }
            }
            toClaim.merge(normal.get(), claimable.types(), (a, b) -> a);
        }
        if (toClaim.isEmpty()) {
            return results;
        }
        List<TypeResult> claimed;
        try {
            claimed = List.copyOf(backend.claim(toClaim));
        } catch (RuntimeException | LinkageError e) {
            LOG.log(System.Logger.Level.WARNING, "Claiming failed", e);
            List<TypeResult> failed = new java.util.ArrayList<>();
            toClaim.forEach((extension, types) -> types.forEach(type ->
                    failed.add(new TypeResult(extension, type, new Outcome.Failed(String.valueOf(e.getMessage()))))));
            claimed = failed;
        }
        for (TypeResult result : claimed) {
            if (result.outcome() instanceof Outcome.Failed && created.contains(result.type())) {
                store.remove(result.type());
            }
        }
        results.addAll(claimed);
        return results;
    }

    private List<TypeResult> restoreOf(List<String> extensions) {
        List<TypeResult> results = new java.util.ArrayList<>();
        Map<String, Map<String, Handler>> toRestore = new LinkedHashMap<>();
        for (String extension : extensions) {
            String normal = normalise(extension).orElse(extension);
            Map<String, Handler> saved = new LinkedHashMap<>();
            Claimability resolved = resolve(extension);
            if (!(resolved instanceof Claimability.Claimable claimable)) {
                Refusal why = ((Claimability.NotClaimable) resolved).refusal();
                results.add(new TypeResult(normal, "", new Outcome.Refused(why)));
                continue;
            }
            for (String type : claimable.types()) {
                store.get(type).ifPresent(handler -> saved.put(type, handler));
            }
            if (!saved.isEmpty()) {
                toRestore.put(normal, saved);
            }
        }
        if (toRestore.isEmpty()) {
            return results;
        }
        List<TypeResult> restored;
        try {
            restored = List.copyOf(backend.restore(toRestore));
        } catch (RuntimeException | LinkageError e) {
            LOG.log(System.Logger.Level.WARNING, "Restoring failed", e);
            List<TypeResult> failed = new java.util.ArrayList<>();
            toRestore.forEach((extension, types) -> types.keySet().forEach(type ->
                    failed.add(new TypeResult(extension, type, new Outcome.Failed(String.valueOf(e.getMessage()))))));
            restored = failed;
        }
        for (TypeResult result : restored) {
            if (result.outcome() instanceof Outcome.Done) {
                store.remove(result.type());
            }
        }
        results.addAll(restored);
        return results;
    }

    /** The backend of {@link #unsupported()}. */
    private static final class UnsupportedBackend implements Backend {
        @Override public Optional<Unavailable> unavailable() { return Optional.of(Unavailable.UNSUPPORTED_SYSTEM); }

        @Override public Claimability claimability(String extension) {
            return new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE);
        }

        @Override public Handler handler(String type) throws IOException { throw new IOException("unsupported"); }

        @Override public List<TypeResult> claim(Map<String, List<String>> typesByExtension) { return List.of(); }

        @Override public List<TypeResult> restore(Map<String, Map<String, Handler>> previous) { return List.of(); }

        @Override public void forget(String extension) { }
    }
}
