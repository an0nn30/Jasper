package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Who reopens a recorded key: resolvers registered by key prefix, the longest matching prefix winning. The Editor
 * reopens {@code local:} keys itself. A resolver that throws, or answers null, cannot reopen the key.
 */
public final class Reopeners {
    /** The prefix of local documents' keys, as {@code LocalDocument.key} writes them. */
    public static final String LOCAL = "local:";

    private final Function<Path, EditorDocument> local;
    private final Map<String, Function<String, Optional<Function<WindowOwner, EditorDocument>>>> byPrefix = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** @param local builds the document for a local file */
    public Reopeners(Function<Path, EditorDocument> local) { this.local = Objects.requireNonNull(local); }

    /**
     * @param keyPrefix not blank and not {@link #LOCAL}
     * @param resolve resolves a recorded key; see {@code Editor.reopener}
     * @return removes this registration, and nothing else
     */
    public Subscription register(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
        if (keyPrefix == null || keyPrefix.isBlank() || keyPrefix.startsWith(LOCAL)) throw new IllegalArgumentException("Not a key prefix: " + keyPrefix);
        Objects.requireNonNull(resolve, "resolve");
        byPrefix.put(keyPrefix, resolve);
        changed();
        return () -> { if (byPrefix.remove(keyPrefix, resolve)) changed(); };
    }

    /**
     * @param key a recorded document key
     * @return a factory for the window reopening it, or empty when nothing can reopen it now
     */
    public Optional<Function<WindowOwner, EditorDocument>> resolve(String key) {
        if (key.startsWith(LOCAL)) {
            Path file;
            try { file = Path.of(key.substring(LOCAL.length())); }
            catch (InvalidPathException malformed) { return Optional.empty(); }
            return Optional.of(owner -> local.apply(file));
        }
        String best = null;
        for (String prefix : byPrefix.keySet()) if (key.startsWith(prefix) && (best == null || prefix.length() > best.length())) best = prefix;
        if (best == null) return Optional.empty();
        var resolver = byPrefix.get(best);
        if (resolver == null) return Optional.empty();
        try {
            Optional<Function<WindowOwner, EditorDocument>> found = resolver.apply(key);
            return found == null ? Optional.empty() : found;
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    /**
     * @param listener runs when a registration is added or removed
     * @return removes the listener
     */
    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    private void changed() { listeners.forEach(Runnable::run); }
}
