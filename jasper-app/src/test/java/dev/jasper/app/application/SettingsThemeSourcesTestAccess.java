package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.ThemeChoices;
import java.util.Optional;

/** Owner-package test bridge to the application's actual authored-source adapter. */
public final class SettingsThemeSourcesTestAccess {
    private SettingsThemeSourcesTestAccess() { }
    public static ThemeChoices choices(ThemeCatalog catalog) {
        return choices(catalog, () -> Optional.of(false));
    }
    public static ThemeChoices choices(ThemeCatalog catalog, java.util.function.Supplier<Optional<Boolean>> systemDark) {
        return new ThemeChoices(()->catalog.latest().names(),ignored->new ThemeChoices.Import(()->{},()->{}),new SettingsThemeSources(catalog),systemDark,ignored->new Subscription(()->{}));
    }
}
