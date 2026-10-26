package dev.jasper.app.contributions;

import dev.jasper.app.config.TabNames;
import dev.jasper.app.config.TitleTemplate;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The pieces plugins add to Terminal tab names (tab names spec 6): each piece's description and owner, and its value
 * per terminal tab. The plugin runtime writes it; windows re-present a tab whose values change, and Settings lists the
 * pieces. EDT only.
 */
public final class TabNamePieces {
    /**
     * One piece.
     *
     * @param name what users write between braces
     * @param description the line Settings shows beside it
     * @param ownerId the plugin that added it
     */
    public record Piece(String name, String description, String ownerId) { }

    private static final System.Logger LOG = System.getLogger(TabNamePieces.class.getName());
    private final Map<String, Piece> pieces = new TreeMap<>();
    private final Map<UUID, Map<String, String>> values = new HashMap<>();
    private final List<Consumer<Optional<UUID>>> listeners = new ArrayList<>();

    /**
     * Adds a piece; closing the subscription removes it and its values from every tab.
     *
     * @throws IllegalArgumentException for a name that is not a piece name or is built in
     * @throws IllegalStateException when the name is already held
     */
    public Subscription add(String name, String description, String ownerId) {
        if (!TitleTemplate.isName(name) || TabNames.BUILT_IN.contains(name))
            throw new IllegalArgumentException("Not a plugin tab name piece: " + name);
        Piece held = pieces.get(name);
        if (held != null) throw new IllegalStateException("{" + name + "} is already a tab name piece of " + held.ownerId());
        Piece piece = new Piece(name, Objects.requireNonNull(description, "description"), Objects.requireNonNull(ownerId, "ownerId"));
        pieces.put(name, piece);
        changed(Optional.empty());
        return new Subscription(() -> {
            if (!pieces.remove(name, piece)) return;
            values.values().forEach(tab -> tab.remove(name));
            values.values().removeIf(Map::isEmpty);
            changed(Optional.empty());
        });
    }

    /** Sets {@code name}'s value for a tab, cleaned as {@link TabNames#clean} does; blank clears it. Ignored for a piece nobody holds. */
    public void set(String name, UUID tabId, String value) {
        if (!pieces.containsKey(name)) return;
        String cleaned = TabNames.clean(value);
        Map<String, String> tab = values.computeIfAbsent(tabId, id -> new HashMap<>());
        String previous = cleaned.isEmpty() ? tab.remove(name) : tab.put(name, cleaned);
        if (tab.isEmpty()) values.remove(tabId);
        if (!Objects.equals(previous, cleaned.isEmpty() ? null : cleaned)) changed(Optional.of(tabId));
    }

    /** Every plugin piece's value for a tab, by name. */
    public Map<String, String> values(UUID tabId) { return Map.copyOf(values.getOrDefault(tabId, Map.of())); }

    /** Every piece, in name order. */
    public List<Piece> pieces() { return List.copyOf(pieces.values()); }

    /** A closed tab's values go. */
    public void forget(UUID tabId) { values.remove(tabId); }

    /** Calls {@code listener} with the tab whose values changed, or with empty when a piece came or went. */
    public Subscription onChanged(Consumer<Optional<UUID>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private void changed(Optional<UUID> tab) {
        for (var listener : List.copyOf(listeners)) {
            try { listener.accept(tab); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A tab name piece listener failed", failure); }
        }
    }
}
