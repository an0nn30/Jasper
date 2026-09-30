package dev.jasper.editor.open;

import dev.jasper.editor.api.LocationResolver;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.files.FileLocation;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Location resolvers by file-source id, as {@code Editor.locationDocuments} registers them. A resolver that throws knows nothing. */
public final class Locations {
    private final Map<String, LocationResolver> bySource = new ConcurrentHashMap<>();

    /**
     * @param sourceId not blank
     * @return removes this registration, and nothing else
     */
    public Subscription register(String sourceId, LocationResolver resolver) {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("Not a source id: " + sourceId);
        Objects.requireNonNull(resolver, "resolver");
        bySource.put(sourceId, resolver);
        return () -> bySource.remove(sourceId, resolver);
    }

    /** @return the sources whose places Open in Editor… offers */
    public Set<String> sources() { return Set.copyOf(bySource.keySet()); }

    public Optional<LocationResolver> resolver(String sourceId) { return Optional.ofNullable(bySource.get(sourceId)); }

    /** @return where an open document is, from the resolver of the source it belongs to */
    public Optional<FileLocation.Sourced> location(String documentKey) {
        for (var entry : bySource.entrySet()) {
            try {
                Optional<FileLocation.Sourced> found = entry.getValue().location(documentKey);
                if (found != null && found.isPresent() && found.get().sourceId().equals(entry.getKey())) return found;
            } catch (RuntimeException failure) {
                // A failing resolver knows no documents.
            }
        }
        return Optional.empty();
    }
}
