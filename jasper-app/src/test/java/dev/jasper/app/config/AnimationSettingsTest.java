package dev.jasper.app.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnimationSettingsTest {
    @Test void savedAnimationPolicyUsesFinalTimingsAndIgnoresRetiredValues() {
        var result = ConfigLoader.parse(Path.of("config.toml"), """
            [ui.animations]
            enabled = false
            tab_open_ms = 240
            tab_close_ms = 90
            tab_switch_ms = 180
            """, true);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().animations()).isEqualTo(new AnimationSettings(false, 475, 425, 0));
        assertThat(result.snapshot().toBuilder().tabHeight(40).build().animations())
            .isEqualTo(new AnimationSettings(false, 475, 425, 0));
    }

    @Test void absentSettingsEnableFinalMotionWhileAnInvalidToggleRejectsTheFile() {
        assertThat(ConfigLoader.parse(Path.of("config.toml"), "", true).snapshot().animations())
            .isEqualTo(new AnimationSettings(true, 475, 425, 0));
        var invalid = ConfigLoader.parse(Path.of("config.toml"), "[ui.animations]\nenabled = 4\ntab_open_ms = 'slow'", true);
        assertThat(invalid.rejected()).isTrue();
        assertThat(invalid.diagnostics()).extracting(ConfigDiagnostic::key).containsExactly("ui.animations.enabled");
        assertThat(invalid.snapshot().animations()).isEqualTo(new AnimationSettings(true, 475, 425, 0));
    }

    @Test void retiredDurationsIgnoreTypesAndRangesWithoutRejectingOrWarning() {
        var result = ConfigLoader.parse(Path.of("config.toml"), """
            [ui.animations]
            tab_open_ms = 'slow'
            tab_close_ms = -1
            tab_switch_ms = [9999, false]
            """, true);
        assertThat(result.snapshot().animations()).isEqualTo(new AnimationSettings(true, 475, 425, 0));
        assertThat(result.rejected()).isFalse();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.legacyKeys()).isEmpty();
    }

    @Test void unknownAnimationSiblingsStillReportTheirExactLocations() {
        var result = ConfigLoader.parse(Path.of("config.toml"), """
            [ui.animations]
            tab_open_ms = 0
            tab_open_ms_extra = 12
            tab_open = 0
            [ui.animations.other]
            speed = 2
            """, true);
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key)
            .containsExactly("ui.animations.tab_open_ms_extra", "ui.animations.tab_open", "ui.animations.other");
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::line).containsExactly(3, 4, 5);
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::severity).containsOnly(ConfigDiagnostic.Severity.WARNING);
        assertThat(result.rejected()).isFalse();
    }

    @Test void newConfigurationTemplatesExposeOnlyTheSharedAnimationToggle() {
        String uncommented = ConfigTemplate.text(true).replaceAll("(?m)^# ([a-z_0-9]+ = )", "$1");
        var parsed = org.tomlj.Toml.parse(uncommented);
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.getTable("ui.animations").keySet()).containsExactly("enabled");
    }

    @Test void internalDurationsStillAllowDeterministicMotionAndRejectInvalidValues() {
        assertThat(new AnimationSettings(true, 0, 1000, 2000).tabOpenMillis()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> new AnimationSettings(true, -1, 100, 130));
        assertThatIllegalArgumentException().isThrownBy(() -> new AnimationSettings(true, 80, 2001, 130));
        assertThatIllegalArgumentException().isThrownBy(() -> new AnimationSettings(true, 80, 100, -1));
    }

    @Test void loadingRetiredDurationsKeepsExistingFileBytes(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path file = directory.resolve("config.toml");
        String original = "# keep\r\n[ui.animations]\r\nenabled = true\r\ntab_open_ms = 310 # tuned\r\n";
        Files.writeString(file, original);
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().snapshot().animations()).isEqualTo(new AnimationSettings(true, 475, 425, 0));
            assertThat(Files.readString(file)).isEqualTo(original);
        }
        assertThat(Files.readString(file)).isEqualTo(original);
    }

    @Test void savingTheToggleKeepsRetiredAndUnrelatedSettingsAfterReload(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "# keep\n[window]\ntab_height = 40\n[ui.animations]\ntab_open_ms = 310 # tuned\ntab_close_ms = 17\ntab_switch_ms = 900\n");
        try (var service = new ConfigService(file, true)) {
            var state = service.save(SettingsEdit.of(CoreSettings.UI_ANIMATIONS_ENABLED, false, service.initialState().values()))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(state.snapshot().animations()).isEqualTo(new AnimationSettings(false, 475, 425, 0));
        }
        try (var reloaded = new ConfigService(file, true)) {
            assertThat(reloaded.initialState().snapshot().animations()).isEqualTo(new AnimationSettings(false, 475, 425, 0));
            assertThat(reloaded.initialState().snapshot().tabHeight()).isEqualTo(40);
            var state = reloaded.save(SettingsEdit.of(CoreSettings.UI_ANIMATIONS_ENABLED, true, reloaded.initialState().values()))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(state.snapshot().animations()).isEqualTo(new AnimationSettings(true, 475, 425, 0));
        }
        assertThat(Files.readString(file)).contains("# keep", "tab_height = 40", "tab_open_ms = 310 # tuned",
            "tab_close_ms = 17", "tab_switch_ms = 900", "enabled = true");
    }
}
