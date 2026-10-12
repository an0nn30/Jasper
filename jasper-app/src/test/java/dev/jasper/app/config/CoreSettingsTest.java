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
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("ui", "theme", "terminal"))).containsSame(CoreSettings.TERMINAL_COLORS);
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
            .put(CoreSettings.TERMINAL_COLORS, "dark", CoreSettings.TERMINAL_COLORS.key())
            .put(CoreSettings.TERMINAL_ENV, Map.of("A", "1"), CoreSettings.TERMINAL_ENV.key())
            .put(CoreSettings.WINDOW_STATUS_BAR_HEIGHT, 30, CoreSettings.WINDOW_STATUS_BAR_HEIGHT.key()).build();
        var snapshot = ConfigSnapshot.from(values, Map.of("copy", "none"), Map.of());
        assertThat(snapshot.fontSize()).isEqualTo(20f);
        assertThat(snapshot.backgroundEnabled()).isTrue();
        assertThat(snapshot.terminalColors()).isEqualTo(TerminalColors.DARK);
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
}
