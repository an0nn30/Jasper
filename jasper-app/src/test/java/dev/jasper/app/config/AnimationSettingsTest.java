package dev.jasper.app.config;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnimationSettingsTest {
    @Test void savedAnimationPolicyAndTemporaryTimingsAreRecognized() {
        var result = ConfigLoader.parse(Path.of("config.toml"), """
            [ui.animations]
            enabled = false
            tab_open_ms = 240
            tab_close_ms = 90
            tab_switch_ms = 180
            """, true);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.snapshot().animations()).isEqualTo(new AnimationSettings(false, 240, 90, 180));
        assertThat(result.snapshot().toBuilder().tabHeight(40).build().animations())
            .isEqualTo(new AnimationSettings(false, 240, 90, 180));
    }

    @Test void absentSettingsEnableTheInitialMotionWhileBadTypesUseSafeDefaults() {
        assertThat(ConfigLoader.parse(Path.of("config.toml"), "", true).snapshot().animations())
            .isEqualTo(new AnimationSettings(true, 150, 120, 130));
        var invalid = ConfigLoader.parse(Path.of("config.toml"), "[ui.animations]\nenabled = 4\ntab_open_ms = 'slow'", true);
        assertThat(invalid.diagnostics()).hasSize(2);
        assertThat(invalid.snapshot().animations()).isEqualTo(AnimationSettings.defaults());
    }

    @Test void zeroIsInstantAndOutOfRangeSavedDurationsAreClamped() {
        var result = ConfigLoader.parse(Path.of("config.toml"),
            "[ui.animations]\ntab_open_ms = 0\ntab_close_ms = -1\ntab_switch_ms = 9999", true);
        assertThat(result.snapshot().animations()).isEqualTo(new AnimationSettings(true, 0, 0, 2000));
        assertThat(result.diagnostics()).hasSize(2);
        assertThatIllegalArgumentException().isThrownBy(() -> new AnimationSettings(true, -1, 120, 130));
        assertThatIllegalArgumentException().isThrownBy(() -> new AnimationSettings(true, 150, 2001, 130));
        assertThatIllegalArgumentException().isThrownBy(() -> new AnimationSettings(true, 150, 120, -1));
    }

    @Test void savingTheToggleKeepsTuningAndOtherSettingsAfterReload(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path file = directory.resolve("config.toml");
        java.nio.file.Files.writeString(file, "# keep\n[window]\ntab_height = 40\n[ui.animations]\ntab_open_ms = 310\n");
        try (var service = new ConfigService(file, true)) {
            var state = service.save(SettingsEdit.of(CoreSettings.UI_ANIMATIONS_ENABLED, false, service.initialState().values()))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(state.snapshot().animations()).isEqualTo(new AnimationSettings(false, 310, 120, 130));
        }
        try (var reloaded = new ConfigService(file, true)) {
            assertThat(reloaded.initialState().snapshot().animations()).isEqualTo(new AnimationSettings(false, 310, 120, 130));
            assertThat(reloaded.initialState().snapshot().tabHeight()).isEqualTo(40);
        }
        assertThat(java.nio.file.Files.readString(file)).contains("# keep");
    }
}
