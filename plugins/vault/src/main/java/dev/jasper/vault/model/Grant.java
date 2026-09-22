package dev.jasper.vault.model;

import java.util.Objects;
import java.util.UUID;

/** "Always allow": {@code pluginId} may fetch {@code credentialId} without a prompt. */
public record Grant(String pluginId, UUID credentialId) {
    public Grant { Objects.requireNonNull(pluginId, "pluginId"); Objects.requireNonNull(credentialId, "credentialId"); }
}
