package dev.jasper.app.shortcuthelp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shortcut edits staged on the Keymap page until Apply: an action id maps to a binding, or to empty to restore its
 * default. An edit that leaves the saved overrides as they are is not kept. EDT only.
 */
final class KeymapDraft {
    private final Map<String, Optional<String>> staged = new LinkedHashMap<>();

    /** Stages {@code changes} over the {@code saved} overrides. */
    void stage(Map<String, Optional<String>> changes, Map<String, String> saved) {
        staged.putAll(changes);
        prune(saved);
    }

    /** Drops staged edits that the {@code saved} overrides already hold. */
    void prune(Map<String, String> saved) {
        staged.entrySet().removeIf(entry -> entry.getValue().equals(Optional.ofNullable(saved.get(entry.getKey()))));
    }

    /** The saved overrides with every staged edit applied. */
    Map<String, String> overrides(Map<String, String> saved) { return merged(saved, staged); }

    /** As {@link #overrides}, with {@code more} applied on top, staging nothing. */
    Map<String, String> preview(Map<String, String> saved, Map<String, Optional<String>> more) {
        var all = new LinkedHashMap<>(staged);
        all.putAll(more);
        return merged(saved, all);
    }

    Map<String, Optional<String>> edits() { return Map.copyOf(staged); }

    boolean modified() { return !staged.isEmpty(); }

    void clear() { staged.clear(); }

    /** Forgets exactly these edits once they are saved; edits staged since then stay. */
    void saved(Map<String, Optional<String>> edits) { edits.forEach(staged::remove); }

    private static Map<String, String> merged(Map<String, String> saved, Map<String, Optional<String>> edits) {
        var result = new LinkedHashMap<>(saved);
        edits.forEach((id, binding) -> {
            if (binding.isPresent()) result.put(id, binding.get());
            else result.remove(id);
        });
        return result;
    }
}
