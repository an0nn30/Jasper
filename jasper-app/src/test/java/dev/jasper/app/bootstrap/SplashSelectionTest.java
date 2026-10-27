package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.platform.SystemAppearance.Observation;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SplashSelectionTest {
    @TempDir Path home;

    private ConfigSnapshot choices(AppearanceMode mode) {
        return ConfigSnapshot.defaults().toBuilder().appearance(mode)
            .darkTheme("catppuccin-mocha").lightTheme("catppuccin-latte").build();
    }

    @Test void appearanceSelectsTheActiveValidatedInterfaceTheme() {
        assertThat(select(choices(AppearanceMode.DARK), Observation.light())).isEqualTo("catppuccin-mocha");
        assertThat(select(choices(AppearanceMode.LIGHT), Observation.dark())).isEqualTo("catppuccin-latte");
        assertThat(select(choices(AppearanceMode.SYSTEM), Observation.light())).isEqualTo("catppuccin-latte");
        assertThat(select(choices(AppearanceMode.SYSTEM), Observation.dark())).isEqualTo("catppuccin-mocha");
        assertThat(select(choices(AppearanceMode.SYSTEM), Observation.unavailable("pending"))).isEqualTo("catppuccin-mocha");
    }

    @Test void missingAndWrongPolarityPicksUseTheSameValidatedFallbackAsTheApplication() {
        assertThat(select(choices(AppearanceMode.DARK).toBuilder().darkTheme("missing").build(), Observation.dark()))
            .isEqualTo("jasper-meadow");
        assertThat(select(choices(AppearanceMode.LIGHT).toBuilder().lightTheme("catppuccin-mocha").build(), Observation.light()))
            .isEqualTo("jasper-meadow");
        assertThat(select(choices(AppearanceMode.DARK).toBuilder().darkTheme("desktop-colors").build(), Observation.dark()))
            .isEqualTo("desktop-colors");
    }

    @Test void dormantPluginPicksFollowTheApplicationsFallback() {
        var plugins = new dev.jasper.app.appearance.PluginThemes(java.util.List.of(), java.util.Map.of("sleeping", "Sleeping"),
            java.util.Set.of("sleeping"), java.util.List.of());
        var config = choices(AppearanceMode.DARK).toBuilder().darkTheme("sleeping").build();
        assertThat(SplashSelection.theme(config, ThemeLibrary.of(plugins), Observation.dark())).isEqualTo("jasper-meadow");
    }

    @Test void preferencesSavedThroughTheRealServiceChooseNewArtAtTheNextStartup() throws Exception {
        Path file = home.resolve("config.toml");
        try (var first = new ConfigService(file, false)) {
            var saved = first.save(SettingsEdit.of(CoreSettings.UI_APPEARANCE, AppearanceMode.DARK,
                first.initialState().values())).get(10, TimeUnit.SECONDS);
            first.save(SettingsEdit.of(CoreSettings.UI_THEME_DARK, "slate-cobalt", saved.values())).get(10, TimeUnit.SECONDS);
        }
        try (var second = new ConfigService(file, false)) {
            assertThat(select(second.initialState().snapshot(), Observation.light())).isEqualTo("slate-cobalt");
            second.save(SettingsEdit.of(CoreSettings.UI_THEME_DARK, "ubuntu-yaru", second.initialState().values()))
                .get(10, TimeUnit.SECONDS);
        }
        try (var third = new ConfigService(file, false)) {
            assertThat(select(third.initialState().snapshot(), Observation.light())).isEqualTo("ubuntu-yaru");
        }
    }

    private String select(ConfigSnapshot config, Observation observation) {
        return SplashSelection.theme(config, ThemeLibrary.bundledOnly(), observation);
    }
}
