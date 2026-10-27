package dev.jasper.app.bootstrap;

import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.AppearancePolicy;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.SystemAppearance;

/** Validated interface identity used by the next ordinary launch's splash. */
final class SplashSelection {
    private SplashSelection() { }

    static String theme(ConfigSnapshot config, PluginSelection selection) {
        // Imported themes and unavailable picks both use Meadow art. Avoid rescanning the user's
        // theme folder before the application's normal read merely to choose that same illustration.
        var library = ThemeLibrary.of(selection.themes());
        if (config.appearance() != dev.jasper.app.config.AppearanceMode.SYSTEM)
            return theme(config, library, SystemAppearance.Observation.unavailable("Explicit appearance"));
        // The snapshot owns and closes its macOS observer. Linux remains unavailable here: only the
        // application's retained DesktopColors owner may start helpers, after the splash opens.
        try (var appearance = SystemAppearance.open()) { return theme(config, library, appearance.current()); }
    }

    static String theme(ConfigSnapshot config, ThemeLibrary library, SystemAppearance.Observation observation) {
        boolean dark = AppearancePolicy.dark(config.appearance(), observation);
        return ThemeSelection.of(library, config.darkTheme(), config.lightTheme(), config.terminalScheme(),
            (key, message) -> { }).theme(dark).id();
    }
}
