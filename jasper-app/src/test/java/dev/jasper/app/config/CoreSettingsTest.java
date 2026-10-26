package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.Applies;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settings.TomlLiterals;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreSettingsTest {
    @Test void linuxUsesDesktopColorsAndOtherPlatformsKeepTheirAuthoredDefault() {
        for (String linux : List.of("Linux", "linux", "LINUX"))
            assertThat(CoreSettings.interfaceThemeByDefault(linux, "authored")).isEqualTo("desktop-colors");
        for (String other : new String[]{"Mac OS X", "Windows 11", "FreeBSD", "", null})
            assertThat(CoreSettings.interfaceThemeByDefault(other, "authored")).isEqualTo("authored");
    }

    @Test void absentThemePicksAndResetUseTheHostDefaults() {
        String expected = System.getProperty("os.name", "").equalsIgnoreCase("Linux")
            ? "desktop-colors" : "jasper-meadow";
        for (String text : List.of("", "[ui.theme]\n")) {
            var loaded = ConfigLoader.parse(java.nio.file.Path.of("config.toml"), text, false);
            assertThat(loaded.snapshot().darkTheme()).isEqualTo(expected);
            assertThat(loaded.snapshot().lightTheme()).isEqualTo(expected);
            assertThat(loaded.snapshot().desktopSource()).isEqualTo("auto");
            assertThat(loaded.snapshot().appearance()).isEqualTo(AppearanceMode.SYSTEM);
            assertThat(loaded.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        }
        var saved = ConfigLoader.parse(java.nio.file.Path.of("config.toml"),
            "[ui.theme]\ndark = 'jasper-meadow'\nlight = 'slate-cobalt'\n", false);
        assertThat(saved.snapshot().darkTheme()).isEqualTo("jasper-meadow");
        assertThat(saved.snapshot().lightTheme()).isEqualTo("slate-cobalt");
        var draft = new dev.jasper.app.settingsui.SettingsDraft(saved.values());
        for (var setting : List.of(CoreSettings.UI_THEME_DARK, CoreSettings.UI_THEME_LIGHT)) {
            draft.stage(setting, java.util.Optional.empty());
            assertThat(draft.value(setting)).isEqualTo(expected);
        }
        var look = new DefaultSource(Map.of(CoreSettings.UI_THEME_DARK, "catppuccin-mocha",
            CoreSettings.UI_THEME_LIGHT, "catppuccin-latte"));
        var lookDraft = new dev.jasper.app.settingsui.SettingsDraft(saved.values(), look);
        assertThat(lookDraft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("jasper-meadow");
        assertThat(lookDraft.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("slate-cobalt");
        lookDraft.stage(CoreSettings.UI_THEME_DARK, java.util.Optional.empty());
        lookDraft.stage(CoreSettings.UI_THEME_LIGHT, java.util.Optional.empty());
        assertThat(lookDraft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("catppuccin-mocha");
        assertThat(lookDraft.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("catppuccin-latte");
    }

    @Test void savedAuthoredPicksSurviveReload(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) throws Exception {
        var file = home.resolve("config.toml");
        java.nio.file.Files.writeString(file, "[ui.theme]\ndark = 'jasper-meadow'\nlight = 'slate-cobalt'\n");
        try (var service = new ConfigService(file, false)) {
            assertThat(service.initialState().snapshot().darkTheme()).isEqualTo("jasper-meadow");
            assertThat(service.initialState().snapshot().lightTheme()).isEqualTo("slate-cobalt");
            var reloaded = service.reload().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(reloaded.snapshot().darkTheme()).isEqualTo("jasper-meadow");
            assertThat(reloaded.snapshot().lightTheme()).isEqualTo("slate-cobalt");
        }
    }

    @Test void viewModeIsARestartOnlyChoiceWithNormalAsItsDefault() {
        var setting = CoreSettings.WINDOW_VIEW_MODE;
        assertThat(setting.name()).isEqualTo("window.view_mode");
        assertThat(setting.label()).isEqualTo("View mode");
        assertThat(setting.defaultValue()).isEqualTo(ViewMode.NORMAL);
        assertThat(setting.applies()).isEqualTo(Applies.RESTART);
        assertThat(((SettingKind.Choice<ViewMode>) setting.kind()).tokens()).containsExactly(
            Map.entry("normal", ViewMode.NORMAL), Map.entry("compact", ViewMode.COMPACT));
    }

    @Test void defaultsBuildTheBuiltInSnapshot() {
        assertThat(ConfigSnapshot.from(SettingValues.defaults(CoreSettings.SCHEMA), Map.of(), Map.of()))
            .isEqualTo(ConfigSnapshot.defaults());
    }

    @Test void everySettingIsDescribedAndItsTemplateValueRoundTrips() {
        assertThat(CoreSettings.SCHEMA.settings()).hasSize(45);
        for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
            assertThat(setting.label()).as(setting.name()).isNotBlank();
            assertThat(setting.description()).as(setting.name()).isNotBlank();
            assertRoundTrip(setting);
        }
    }

    private static <T> void assertRoundTrip(Setting<T> setting) {
        T value = setting.example().orElse(setting.defaultValue());
        var reading = setting.kind().read(TomlLiterals.parseValue(setting.kind().literal(value)), setting.defaultValue());
        assertThat(reading.issues()).as(setting.name()).isEmpty();
        assertThat(reading.value()).as(setting.name()).isEqualTo(value);
    }

    @Test void theVersionTwoLayoutKeepsOldKeysAsAliases() {
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "size"))).containsSame(CoreSettings.TERMINAL_FONT_SIZE);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "family"))).containsSame(CoreSettings.TERMINAL_FONT_FAMILY);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "fallback"))).containsSame(CoreSettings.TERMINAL_FONT_FALLBACK);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "ligatures"))).containsSame(CoreSettings.TERMINAL_FONT_LIGATURES);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "line_height"))).containsSame(CoreSettings.TERMINAL_FONT_LINE_HEIGHT);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("ui", "theme", "terminal"))).as("removed with the theme names").isEmpty();
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("terminal", "colors"))).isEmpty();
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("background", "enabled"))).containsSame(CoreSettings.SYSTEM_RUN_IN_BACKGROUND);
        assertThat(CoreSettings.SCHEMA.reserved(List.of("keybindings"))).isTrue();
        assertThat(CoreSettings.SCHEMA.reserved(List.of("plugins"))).isTrue();
        assertThat(CoreSettings.SCHEMA.reserved(List.of("palette", "scopes"))).isTrue();
        assertThat(CoreSettings.TERMINAL_ENV.kind()).isInstanceOf(SettingKind.StringMap.class);
        assertThat(CoreSettings.SYSTEM_RUN_IN_BACKGROUND.applies()).isEqualTo(Applies.RESTART);
        assertThat(CoreSettings.WINDOW_COLUMNS.applies()).isEqualTo(Applies.NEW_WINDOWS);
        assertThat(CoreSettings.TERMINAL_SHELL_PROGRAM.applies()).isEqualTo(Applies.NEW_PANES);
    }

    @Test void theSnapshotReadsEveryField() {
        var values = SettingValues.builder(CoreSettings.SCHEMA)
            .put(CoreSettings.TERMINAL_FONT_SIZE, 20f, CoreSettings.TERMINAL_FONT_SIZE.key())
            .put(CoreSettings.SYSTEM_RUN_IN_BACKGROUND, true, CoreSettings.SYSTEM_RUN_IN_BACKGROUND.key())
            .put(CoreSettings.UI_APPEARANCE, AppearanceMode.LIGHT, CoreSettings.UI_APPEARANCE.key())
            .put(CoreSettings.TERMINAL_SCHEME, "slate-cobalt/high-contrast", CoreSettings.TERMINAL_SCHEME.key())
            .put(CoreSettings.UI_THEME_DARK, "ocean", CoreSettings.UI_THEME_DARK.key())
            .put(CoreSettings.TERMINAL_ENV, Map.of("A", "1"), CoreSettings.TERMINAL_ENV.key())
            .put(CoreSettings.WINDOW_STATUS_BAR_HEIGHT, 30, CoreSettings.WINDOW_STATUS_BAR_HEIGHT.key()).build();
        var snapshot = ConfigSnapshot.from(values, Map.of("copy", "none"), Map.of());
        assertThat(snapshot.fontSize()).isEqualTo(20f);
        assertThat(snapshot.backgroundEnabled()).isTrue();
        assertThat(snapshot.appearance()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(snapshot.terminalScheme()).isEqualTo(TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow());
        assertThat(snapshot.darkTheme()).isEqualTo("ocean");
        assertThat(snapshot.lightTheme()).isEqualTo(CoreSettings.UI_THEME_LIGHT.defaultValue());
        assertThat(snapshot.terminal().env()).containsExactly(Map.entry("A", "1"));
        assertThat(snapshot.statusBarHeight()).isEqualTo(30);
        assertThat(snapshot.keybindings()).containsExactly(Map.entry("copy", "none"));
    }

    @Test void residencyIsOnByDefaultWhereAWindowlessJasperStaysReachable() {
        assertThat(CoreSettings.runsInBackgroundByDefault("Mac OS X")).as("menu bar item and Dock").isTrue();
        assertThat(CoreSettings.runsInBackgroundByDefault("Windows 11")).as("notification-area icon").isTrue();
        assertThat(CoreSettings.runsInBackgroundByDefault("Linux")).as("no tray").isFalse();
        assertThat(CoreSettings.runsInBackgroundByDefault("FreeBSD")).isFalse();
        assertThat(CoreSettings.runsInBackgroundByDefault(null)).isFalse();
        assertThat(CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue())
            .isEqualTo(CoreSettings.runsInBackgroundByDefault(System.getProperty("os.name")));
        assertThat(ConfigSnapshot.defaults().backgroundEnabled()).isEqualTo(CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue());
    }

    @org.junit.jupiter.api.Test void nativeDecorationsIsARestartOnlyWindowSettingOffByDefault() {
        var setting = CoreSettings.WINDOW_NATIVE_DECORATIONS;
        assertThat(setting.name()).isEqualTo("window.native_decorations");
        assertThat(setting.defaultValue()).isFalse();
        assertThat(setting.applies()).isEqualTo(dev.jasper.app.settings.Applies.RESTART);
        assertThat(dev.jasper.app.config.ConfigSnapshot.defaults().nativeDecorations()).isFalse();
        assertThat(dev.jasper.app.config.ConfigSnapshot.defaults().toBuilder().nativeDecorations(true).build().nativeDecorations()).isTrue();
    }

    @Test void theMemoryIndicatorIsOffByDefault() {
        assertThat(CoreSettings.WINDOW_MEMORY_INDICATOR.name()).isEqualTo("window.memory_indicator");
        assertThat(CoreSettings.WINDOW_MEMORY_INDICATOR.defaultValue()).isFalse();
        assertThat(CoreSettings.WINDOW_MEMORY_INDICATOR.label()).isEqualTo("Show memory indicator");
    }
    @Test void desktopSourceIsAnIndependentChoiceAndRoundTripsEverySnapshotPath() {
        var setting = CoreSettings.UI_THEME_DESKTOP_SOURCE;
        assertThat(setting.name()).isEqualTo("ui.theme.desktop_source");
        assertThat(setting.label()).isEqualTo("Desktop color source");
        assertThat(setting.defaultValue()).isEqualTo("auto");
        assertThat(((SettingKind.Choice<String>) setting.kind()).tokens()).containsExactly(
            Map.entry("auto", "auto"), Map.entry("qt", "qt"), Map.entry("gtk3", "gtk3"), Map.entry("gtk4", "gtk4"));
        assertThat(ConfigSnapshot.defaults().desktopSource()).isEqualTo("auto");
        for (String token : List.of("auto", "qt", "gtk3", "gtk4")) {
            var values = SettingValues.builder(CoreSettings.SCHEMA).put(setting, token, setting.key()).build();
            var read = ConfigSnapshot.from(values, Map.of(), Map.of());
            assertThat(read.desktopSource()).isEqualTo(token);
            assertThat(read.toBuilder().build()).isEqualTo(read);
            assertThat(ConfigSnapshot.builder().desktopSource(token).build()).isEqualTo(read);
            assertThat(read.appearance()).isEqualTo(AppearanceMode.SYSTEM);
            assertThat(read.darkTheme()).isEqualTo(CoreSettings.UI_THEME_DARK.defaultValue());
            var look = new DefaultSource(Map.of(CoreSettings.UI_THEME_DARK, "slate-cobalt"));
            assertThat(ConfigSnapshot.from(values, look, Map.of(), Map.of()).desktopSource()).isEqualTo(token);
            assertThat(ConfigSnapshot.defaults(look).desktopSource()).isEqualTo("auto");
        }
    }

    @Test void aSnapshotBuilderRefusesAnInvalidDesktopSource() {
        assertThatThrownBy(() -> ConfigSnapshot.builder().desktopSource("bogus").build()).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ui.theme.desktop_source");
    }

    @Test void invalidDesktopSourceFallsBackWithAnOrdinaryDiagnostic() {
        var result = ConfigLoader.parse(java.nio.file.Path.of("config.toml"), "[ui.theme]\ndesktop_source = 'bogus'\n", false);
        assertThat(result.snapshot().desktopSource()).isEqualTo("auto");
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic ->
            assertThat(diagnostic.key()).isEqualTo("ui.theme.desktop_source"));
    }

}
