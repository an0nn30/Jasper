package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.FileAssociations;
import dev.jasper.sdk.ui.FileAssociations.Claimability;
import dev.jasper.sdk.ui.FileAssociations.FileTypeStatus;
import dev.jasper.sdk.ui.FileAssociations.Handler;
import dev.jasper.sdk.ui.FileAssociations.Outcome;
import dev.jasper.sdk.ui.FileAssociations.Refusal;
import dev.jasper.sdk.ui.FileAssociations.TypeResult;
import dev.jasper.sdk.ui.FileAssociations.TypeState;
import dev.jasper.sdk.ui.FileAssociations.Unavailable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * The scripted file associations every plugin's {@code Platform.fileAssociations} shares, with the application's
 * semantics: extensions are normalised, the browser's are refused, a claim saves each type's first previous application
 * and a restore hands back only types that are Jasper's now. Everything runs on the caller's thread, which must be the
 * EDT, and every future is complete when it is returned.
 */
final class FakeFileAssociations {
    private static final Pattern EXTENSION = Pattern.compile("\\.[a-z0-9_+-]{1,16}");

    private record Registration(String pluginId, Runnable handler, AtomicBoolean closed) { }

    private final FakePluginHost host;
    private final Object lock = new Object();
    private Optional<Unavailable> unavailable = Optional.empty();
    private boolean inSettings;
    private final Map<String, List<String>> claimable = new LinkedHashMap<>();
    private final Map<String, Refusal> refused = new LinkedHashMap<>();
    private final Map<String, Handler> handlers = new LinkedHashMap<>();
    private final Map<String, Handler> saved = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();

    FakeFileAssociations(FakePluginHost host) { this.host = host; }

    void unavailable(Optional<Unavailable> reason) {
        Objects.requireNonNull(reason, "reason");
        synchronized (lock) { unavailable = reason; }
    }

    void type(String extension, List<String> types) {
        String normal = normalise(extension).orElseThrow(() -> new IllegalArgumentException("Not an extension: " + extension));
        List<String> copy = List.copyOf(types);
        if (copy.isEmpty()) throw new IllegalArgumentException("A claimable extension has at least one type");
        synchronized (lock) { refused.remove(normal); claimable.put(normal, copy); }
    }

    void refuse(String extension, Refusal refusal) {
        String normal = normalise(extension).orElseThrow(() -> new IllegalArgumentException("Not an extension: " + extension));
        Objects.requireNonNull(refusal, "refusal");
        synchronized (lock) { claimable.remove(normal); refused.put(normal, refusal); }
    }

    void handler(String type, Handler handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        synchronized (lock) { handlers.put(type, handler); }
    }

    void failClaims(String type, String detail) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detail, "detail");
        synchronized (lock) { failures.put(type, detail); }
    }

    void inSettings(boolean windowsLike) { synchronized (lock) { inSettings = windowsLike; } }

    List<String> calls() { return List.copyOf(calls); }

    /** Runs every open plugin's change handler on the EDT. */
    void changed() {
        Runnable run = () -> {
            for (var registration : List.copyOf(registrations)) {
                if (registration.closed().get()) continue;
                try { registration.handler().run(); }
                catch (RuntimeException | LinkageError failure) {
                    host.recordFailure(registration.pluginId() + " file types change: " + failure);
                }
            }
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) { run.run(); return; }
        try { javax.swing.SwingUtilities.invokeAndWait(run); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(failure.getCause());
        }
    }

    private static Optional<String> normalise(String extension) {
        if (extension == null) return Optional.empty();
        String text = extension.strip().toLowerCase(Locale.ROOT);
        if (!text.startsWith(".")) text = "." + text;
        return EXTENSION.matcher(text).matches() ? Optional.of(text) : Optional.empty();
    }

    /** One plugin's view; {@code open} says whether the plugin is still running. */
    FileAssociations view(String pluginId, java.util.function.BooleanSupplier open, java.util.function.Consumer<Subscription> own) {
        return new FileAssociations() {
            private void requireUi() {
                if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
                if (!javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("File associations require the UI thread");
            }

            @Override public Optional<Unavailable> unavailable() {
                requireUi();
                synchronized (lock) { return unavailable; }
            }

            @Override public CompletableFuture<List<FileTypeStatus>> status(List<String> extensions) {
                requireUi();
                List<String> asked = List.copyOf(extensions);
                synchronized (lock) {
                    List<FileTypeStatus> out = new ArrayList<>();
                    for (String extension : asked) {
                        List<TypeState> states = new ArrayList<>();
                        for (String type : typesOf(extension)) {
                            states.add(new TypeState(type, handlers.getOrDefault(type, new Handler.None()),
                                Optional.ofNullable(saved.get(type))));
                        }
                        out.add(new FileTypeStatus(normalise(extension).orElse(extension), states));
                    }
                    return CompletableFuture.completedFuture(out);
                }
            }

            @Override public CompletableFuture<Claimability> claimability(String extension) {
                requireUi();
                Objects.requireNonNull(extension, "extension");
                synchronized (lock) { return CompletableFuture.completedFuture(resolve(extension)); }
            }

            @Override public CompletableFuture<List<TypeResult>> claim(List<String> extensions) {
                requireUi();
                List<String> asked = List.copyOf(extensions);
                List<TypeResult> results;
                synchronized (lock) {
                    asked.forEach(extension -> calls.add("claim:" + normalise(extension).orElse(extension)));
                    results = unavailable.isPresent() ? unavailableResults(asked) : claimOf(asked);
                }
                changed();
                return CompletableFuture.completedFuture(results);
            }

            @Override public CompletableFuture<List<TypeResult>> restore(List<String> extensions) {
                requireUi();
                List<String> asked = List.copyOf(extensions);
                List<TypeResult> results;
                synchronized (lock) {
                    asked.forEach(extension -> calls.add("restore:" + normalise(extension).orElse(extension)));
                    results = unavailable.isPresent() ? unavailableResults(asked) : restoreOf(asked);
                }
                changed();
                return CompletableFuture.completedFuture(results);
            }

            @Override public CompletableFuture<Void> forget(String extension) {
                requireUi();
                Objects.requireNonNull(extension, "extension");
                Optional<String> normal = normalise(extension);
                if (normal.isPresent()) {
                    synchronized (lock) {
                        calls.add("forget:" + normal.get());
                        typesOf(normal.get()).forEach(saved::remove);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override public Subscription onChanged(Runnable handler) {
                requireUi();
                Objects.requireNonNull(handler, "handler");
                var registration = new Registration(pluginId, handler, new AtomicBoolean());
                registrations.add(registration);
                Subscription subscription = () -> { registration.closed().set(true); registrations.remove(registration); };
                own.accept(subscription);
                return subscription;
            }
        };
    }

    /** Under the lock. */
    private Claimability resolve(String extension) {
        Optional<String> normal = normalise(extension);
        if (normal.isEmpty()) return new Claimability.NotClaimable(Refusal.INVALID);
        String name = normal.get();
        if (name.equals(".html") || name.equals(".htm")) return new Claimability.NotClaimable(Refusal.BROWSER_OWNED);
        if (refused.containsKey(name)) return new Claimability.NotClaimable(refused.get(name));
        List<String> types = claimable.get(name);
        if (types == null) return new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE);
        return new Claimability.Claimable(types, false);
    }

    private List<String> typesOf(String extension) {
        return resolve(extension) instanceof Claimability.Claimable c ? c.types() : List.of();
    }

    private List<TypeResult> unavailableResults(List<String> extensions) {
        Outcome failed = new Outcome.Failed("unavailable: " + unavailable.get());
        return extensions.stream().map(extension -> new TypeResult(normalise(extension).orElse(extension), "", failed)).toList();
    }

    private List<TypeResult> claimOf(List<String> extensions) {
        List<TypeResult> results = new ArrayList<>();
        for (String extension : extensions) {
            String name = normalise(extension).orElse(extension);
            if (!(resolve(extension) instanceof Claimability.Claimable claimable)) {
                results.add(new TypeResult(name, "", new Outcome.Refused(((Claimability.NotClaimable) resolve(extension)).refusal())));
                continue;
            }
            for (String type : claimable.types()) {
                Handler was = handlers.getOrDefault(type, new Handler.None());
                boolean created = false;
                if (!(was instanceof Handler.Jasper) && !saved.containsKey(type)) {
                    saved.put(type, was);
                    created = true;
                }
                if (failures.containsKey(type)) {
                    if (created) saved.remove(type);
                    results.add(new TypeResult(name, type, new Outcome.Failed(failures.get(type))));
                } else if (inSettings) {
                    results.add(new TypeResult(name, type, new Outcome.InSettings()));
                } else {
                    handlers.put(type, new Handler.Jasper());
                    results.add(new TypeResult(name, type, new Outcome.Done()));
                }
            }
        }
        return results;
    }

    private List<TypeResult> restoreOf(List<String> extensions) {
        List<TypeResult> results = new ArrayList<>();
        for (String extension : extensions) {
            String name = normalise(extension).orElse(extension);
            if (!(resolve(extension) instanceof Claimability.Claimable claimable)) {
                results.add(new TypeResult(name, "", new Outcome.Refused(((Claimability.NotClaimable) resolve(extension)).refusal())));
                continue;
            }
            for (String type : claimable.types()) {
                Handler previous = saved.get(type);
                if (previous == null) continue;
                if (!(handlers.getOrDefault(type, new Handler.None()) instanceof Handler.Jasper)) {
                    saved.remove(type);
                } else if (previous instanceof Handler.None) {
                    results.add(new TypeResult(name, type, new Outcome.NoPrevious()));
                } else {
                    handlers.put(type, previous);
                    saved.remove(type);
                    results.add(new TypeResult(name, type, new Outcome.Done()));
                }
            }
        }
        return results;
    }
}
