package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsEditTest {
    @Test void oneSettingExpectsWhatTheFileHolds() {
        var values = ConfigLoader.parse(Path.of("config.toml"), "[window]\ntoolbar = \"icons\"\n", true).values();
        var edit = SettingsEdit.of(CoreSettings.WINDOW_TOOLBAR, ToolbarMode.HIDDEN, values);
        assertThat(edit.changes()).isEqualTo(Map.of(CoreSettings.WINDOW_TOOLBAR, Optional.of(ToolbarMode.HIDDEN)));
        assertThat(edit.expected()).isEqualTo(Map.of(CoreSettings.WINDOW_TOOLBAR, Optional.of(ToolbarMode.ICONS)));
        var unset = SettingsEdit.of(CoreSettings.WINDOW_STATUS_BAR, false, values);
        assertThat(unset.expected()).isEqualTo(Map.of(CoreSettings.WINDOW_STATUS_BAR, Optional.empty()));
    }
}
