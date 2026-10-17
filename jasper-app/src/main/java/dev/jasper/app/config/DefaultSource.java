package dev.jasper.app.config;

import dev.jasper.app.settings.Setting;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The defaults a look gives settings (spec 5.8): a key reads as the user's saved value, else this source's, else the
 * core default. Only {@link #ALLOWED} may be overridden, each with a value its setting accepts. Immutable.
 *
 * @param overrides each overridden setting's default
 */
public record DefaultSource(Map<Setting<?>, Object> overrides) {
    /** The settings a look may default: both theme picks, the palette's rows and the toolbar's height. */
    public static final Set<Setting<?>> ALLOWED = Set.of(CoreSettings.UI_THEME_LIGHT, CoreSettings.UI_THEME_DARK,
        CoreSettings.PALETTE_VISIBLE_ROWS, CoreSettings.WINDOW_TOOLBAR_HEIGHT);
    /** No overrides: the core defaults, as the default look has them. */
    public static final DefaultSource CORE = new DefaultSource(Map.of());

    /** Copies the map; refuses a setting outside {@link #ALLOWED} or a value its setting rejects. */
    public DefaultSource {
        overrides = Map.copyOf(overrides);
        for (var entry : overrides.entrySet()) {
            if (!ALLOWED.contains(entry.getKey())) throw new IllegalArgumentException("Not a setting a look may default: " + entry.getKey().name());
            check(entry.getKey(), entry.getValue());
        }
    }

    /** {@code setting}'s default under this source. */
    @SuppressWarnings("unchecked")
    public <T> T value(Setting<T> setting) {
        Object value = overrides.get(Objects.requireNonNull(setting, "setting"));
        return value == null ? setting.defaultValue() : (T) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> void check(Setting<T> setting, Object value) {
        Objects.requireNonNull(value, setting.name());
        if (!setting.defaultValue().getClass().isInstance(value))
            throw new IllegalArgumentException(setting.name() + " is not a " + setting.defaultValue().getClass().getSimpleName() + ": " + value);
        setting.kind().check((T) value).ifPresent(problem -> { throw new IllegalArgumentException(setting.name() + ": " + problem); });
    }
}
