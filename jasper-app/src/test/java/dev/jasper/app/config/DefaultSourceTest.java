package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 5.8: a look defaults five settings, each value one its setting accepts. */
class DefaultSourceTest {
    @Test void theCoreSourceGivesTheCoreDefaultsAndAnOverrideGivesItsValue() {
        assertThat(DefaultSource.CORE.value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(CoreSettings.PALETTE_VISIBLE_ROWS.defaultValue());
        var kit = new DefaultSource(Map.of(CoreSettings.PALETTE_VISIBLE_ROWS, 8, CoreSettings.UI_THEME_DARK, "catppuccin-mocha"));
        assertThat(kit.value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        assertThat(kit.value(CoreSettings.UI_THEME_DARK)).isEqualTo("catppuccin-mocha");
        assertThat(kit.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo(CoreSettings.UI_THEME_LIGHT.defaultValue());
        assertThat(DefaultSource.ALLOWED).containsExactlyInAnyOrder(CoreSettings.UI_THEME_LIGHT, CoreSettings.UI_THEME_DARK,
            CoreSettings.PALETTE_VISIBLE_ROWS, CoreSettings.WINDOW_TOOLBAR_HEIGHT, CoreSettings.WINDOW_TOOLBAR);
        assertThat(new DefaultSource(Map.of(CoreSettings.WINDOW_TOOLBAR, ToolbarMode.ICONS)).value(CoreSettings.WINDOW_TOOLBAR)).isEqualTo(ToolbarMode.ICONS);
    }

    @Test void anotherSettingOrAValueTheSettingRejectsIsRefused() {
        assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.UI_FONT_SIZE, 12f)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ui.font.size");
        assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.PALETTE_VISIBLE_ROWS, 40)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("palette.visible_rows");
        assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.WINDOW_TOOLBAR_HEIGHT, "34")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("window.toolbar_height");
        assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.WINDOW_TOOLBAR, "icons")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("window.toolbar");
    }
}
