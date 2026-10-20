package dev.jasper.remote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Each window's chosen session row ({@code host:<id>} or {@code distro:<profile>}), shared by its toolbar view and its
 * Sessions tree (Enterprise presentation spec 14.4). UI thread only.
 */
final class SessionSelection {
    private final Map<UUID, String> chosen = new HashMap<>();
    private final List<BiConsumer<UUID, Optional<String>>> listeners = new ArrayList<>();

    /** The window's chosen row, if any. */
    Optional<String> get(UUID window) { return Optional.ofNullable(chosen.get(window)); }

    /** Records the window's choice and tells every listener once; the same choice again changes nothing. */
    void set(UUID window, Optional<String> row) {
        Objects.requireNonNull(window); Objects.requireNonNull(row);
        if (get(window).equals(row)) return;
        if (row.isPresent()) chosen.put(window, row.get()); else chosen.remove(window);
        for (var listener : List.copyOf(listeners)) listener.accept(window, row);
    }

    /** The window closed. */
    void forget(UUID window) { chosen.remove(window); }

    /** Hears every change, with the window and its new choice. */
    void onChanged(BiConsumer<UUID, Optional<String>> listener) { listeners.add(Objects.requireNonNull(listener)); }
}
