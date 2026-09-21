package dev.jasper.sdk.events;

import dev.jasper.sdk.Variant;
import java.util.Objects;

/** Application topics that need no capability. */
public final class AppEvents {
    /** The look changed; read {@link ThemeChanged#variant()}. */
    public static final Topic<ThemeChanged> THEME_CHANGED = Topic.of("jasper.app.theme-changed", ThemeChanged.class);
    /** The configuration file was reloaded; plugin tables may have changed. */
    public static final Topic<ConfigReloaded> CONFIG_RELOADED = Topic.of("jasper.app.config-reloaded", ConfigReloaded.class);

    private AppEvents() { }

    /**
     * The application's look changed.
     *
     * @param variant the new variant
     */
    public record ThemeChanged(Variant variant) {
        /** Rejects a null variant. */
        public ThemeChanged { Objects.requireNonNull(variant, "variant"); }
    }

    /** The configuration was reloaded. */
    public record ConfigReloaded() { }
}
