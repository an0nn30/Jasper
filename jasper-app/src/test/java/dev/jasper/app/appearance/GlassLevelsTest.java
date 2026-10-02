package dev.jasper.app.appearance;

import dev.jasper.app.config.GlassSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GlassLevelsTest {
    @Test void savedSettingsOverrideTheThemeOneValueAtATime() {
        var theme = new GlassLevels(0.95, 0.5);
        assertThat(GlassLevels.resolve(GlassSettings.UNSET, theme)).isEqualTo(theme);
        assertThat(GlassLevels.resolve(new GlassSettings(80f, GlassSettings.UNSET_BLUR), theme)).isEqualTo(new GlassLevels(0.8, 0.5));
        assertThat(GlassLevels.resolve(new GlassSettings(GlassSettings.UNSET_OPACITY, 0f), theme)).isEqualTo(new GlassLevels(0.95, 0));
        assertThat(GlassLevels.resolve(new GlassSettings(100f, 20f), GlassLevels.OPAQUE)).isEqualTo(new GlassLevels(1, 0.2));
    }
}
