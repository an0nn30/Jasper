package dev.jasper.app.config;

import dev.jasper.app.settings.Setting;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The defaults a look gives settings (spec 5.8): a key reads as the user's saved value, else this source's, else the
 * core default. Only {@link #ALLOWED} may be overridden, each with a value its setting accepts. {@code plugins} holds a
 * look's defaults for plugins' declared settings (spec 5.8), raw TOML values that the plugin's runtime reads with each
 * setting's own kind when the plugin declares it. Immutable.
 *
 * @param overrides each overridden setting's default
 * @param plugins by plugin id, that plugin's raw default values by dotted setting key
 */
public record DefaultSource(Map<Setting<?>, Object> overrides, Map<String, Map<String, Object>> plugins) {
    /** The settings a look may default: both theme picks, the palette's rows, and the toolbar's height and mode. */
    public static final Set<Setting<?>> ALLOWED = Set.of(CoreSettings.UI_THEME_LIGHT, CoreSettings.UI_THEME_DARK,
        CoreSettings.PALETTE_VISIBLE_ROWS, CoreSettings.WINDOW_TOOLBAR_HEIGHT, CoreSettings.WINDOW_TOOLBAR);
    /** No overrides: the core defaults, as the default look has them. */
    public static final DefaultSource CORE = new DefaultSource(Map.of(), Map.of());

    /** Copies both maps and each plugin's table; refuses a setting outside {@link #ALLOWED} or a value its setting rejects. */
    public DefaultSource {
        overrides = Map.copyOf(overrides);
        for (var entry : overrides.entrySet()) {
            if (!ALLOWED.contains(entry.getKey())) throw new IllegalArgumentException("Not a setting a look may default: " + entry.getKey().name());
            check(entry.getKey(), entry.getValue());
        }
        var tables = new HashMap<String, Map<String, Object>>();
        for (var entry : plugins.entrySet()) tables.put(Objects.requireNonNull(entry.getKey(), "plugin id"), Map.copyOf(entry.getValue()));
        plugins = Map.copyOf(tables);
    }

    /**
     * Core overrides only, with no defaults for plugins' settings.
     *
     * @param overrides each overridden setting's default
     */
    public DefaultSource(Map<Setting<?>, Object> overrides) { this(overrides, Map.of()); }

    /** {@code setting}'s default under this source. */
    @SuppressWarnings("unchecked")
    public <T> T value(Setting<T> setting) {
        Object value = overrides.get(Objects.requireNonNull(setting, "setting"));
        return value == null ? setting.defaultValue() : (T) value;
    }

    /**
     * The look's defaults for one plugin's declared settings.
     *
     * @param pluginId the plugin's id
     * @return its raw TOML values by dotted setting key; empty when the look has none
     */
    public Map<String, Object> plugin(String pluginId) { return plugins.getOrDefault(Objects.requireNonNull(pluginId, "pluginId"), Map.of()); }

    @SuppressWarnings("unchecked")
    private static <T> void check(Setting<T> setting, Object value) {
        Objects.requireNonNull(value, setting.name());
        if (!setting.defaultValue().getClass().isInstance(value))
            throw new IllegalArgumentException(setting.name() + " is not a " + setting.defaultValue().getClass().getSimpleName() + ": " + value);
        setting.kind().check((T) value).ifPresent(problem -> { throw new IllegalArgumentException(setting.name() + ": " + problem); });
    }
}
