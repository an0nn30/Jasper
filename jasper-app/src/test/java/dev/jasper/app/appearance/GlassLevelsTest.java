package dev.jasper.app.appearance;
import dev.jasper.ui.theme.GlassLevels;

import dev.jasper.app.config.GlassSettings;
import dev.jasper.app.config.WorkspaceBlur;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GlassLevelsTest {
    @Test void savedSettingsOverrideTheThemeOneValueAtATime() {
        var theme = new GlassLevels(0.95, true);
        assertThat(GlassSettings.UNSET.resolve(theme)).isEqualTo(theme);
        assertThat(new GlassSettings(80f, WorkspaceBlur.THEME).resolve(theme)).isEqualTo(new GlassLevels(0.8, true));
        assertThat(new GlassSettings(GlassSettings.UNSET_OPACITY, WorkspaceBlur.OFF).resolve(theme)).isEqualTo(new GlassLevels(0.95, false));
        assertThat(new GlassSettings(100f, WorkspaceBlur.ON).resolve(GlassLevels.OPAQUE)).isEqualTo(new GlassLevels(1, true));
    }

    @Test void opaqueIsUnblurred() {
        assertThat(GlassLevels.OPAQUE).isEqualTo(new GlassLevels(1, false));
    }
}
