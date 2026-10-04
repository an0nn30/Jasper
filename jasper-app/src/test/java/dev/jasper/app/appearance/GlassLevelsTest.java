package dev.jasper.app.appearance;

import dev.jasper.app.config.GlassSettings;
import dev.jasper.app.config.WorkspaceBlur;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GlassLevelsTest {
    @Test void savedSettingsOverrideTheThemeOneValueAtATime() {
        var theme = new GlassLevels(0.95, true);
        assertThat(GlassLevels.resolve(GlassSettings.UNSET, theme)).isEqualTo(theme);
        assertThat(GlassLevels.resolve(new GlassSettings(80f, WorkspaceBlur.THEME), theme)).isEqualTo(new GlassLevels(0.8, true));
        assertThat(GlassLevels.resolve(new GlassSettings(GlassSettings.UNSET_OPACITY, WorkspaceBlur.OFF), theme)).isEqualTo(new GlassLevels(0.95, false));
        assertThat(GlassLevels.resolve(new GlassSettings(100f, WorkspaceBlur.ON), GlassLevels.OPAQUE)).isEqualTo(new GlassLevels(1, true));
    }

    @Test void opaqueIsUnblurred() {
        assertThat(GlassLevels.OPAQUE).isEqualTo(new GlassLevels(1, false));
    }
}
