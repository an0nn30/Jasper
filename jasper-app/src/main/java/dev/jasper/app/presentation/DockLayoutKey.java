package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * Under which key a window's tool windows save the dock in workspace.toml (spec 10.7): empty for the default look's
 * keys, else the provider's id.
 *
 * @param key the key
 */
public record DockLayoutKey(String key) {
    /** The key is required. */
    public DockLayoutKey { Objects.requireNonNull(key); }
}
