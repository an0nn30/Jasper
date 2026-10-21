package dev.jasper.sdk.events;

import dev.jasper.sdk.Variant;
import java.util.Objects;

/** Application topics that need no capability. */
public final class AppEvents {
    /**
     * The chrome theme or the interface font changed after start-up, including a change to another theme of the same
     * variant; read {@link ThemeChanged#variant()}.
     */
    public static final Topic<ThemeChanged> THEME_CHANGED = Topic.of("jasper.app.theme-changed", ThemeChanged.class);
    /**
     * Jasper applied a new reading of its configuration file, {@code config.toml}; every plugin's settings file has
     * been read again by then.
     */
    public static final Topic<ConfigReloaded> CONFIG_RELOADED = Topic.of("jasper.app.config-reloaded", ConfigReloaded.class);

    private AppEvents() { }

    /**
     * The chrome theme or the interface font changed.
     *
     * @param variant whether the chrome theme is dark or light
     */
    public record ThemeChanged(Variant variant) {
        /** Rejects a null variant. */
        public ThemeChanged { Objects.requireNonNull(variant, "variant"); }
    }

    /** The configuration was read again; it carries nothing. */
    public record ConfigReloaded() { }
}
