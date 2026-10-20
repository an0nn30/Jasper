package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 5.8: a look defaults five settings, each value one its setting accepts, and plugins' declared settings. */
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
    /** Plan 15a: a look's tables for plugins' settings are copied whole; a plugin without one has an empty table. */
    @Test void pluginTablesAreCopiedAndAbsentOnesAreEmpty() {
        var table = new HashMap<String, Object>(Map.of("size", 12L));
        var plugins = new HashMap<String, Map<String, Object>>(Map.of("dev.x.tool", table));
        var source = new DefaultSource(Map.of(), plugins);
        table.put("size", 14L);
        table.put("wrap", true);
        plugins.put("dev.other", Map.of("size", 9L));
        assertThat(source.plugin("dev.x.tool")).isEqualTo(Map.of("size", 12L));
        assertThat(source.plugin("dev.other")).isEmpty();
        assertThat(DefaultSource.CORE.plugin("dev.x.tool")).isEmpty();
        assertThat(new DefaultSource(Map.of(CoreSettings.PALETTE_VISIBLE_ROWS, 8)).plugin("dev.x.tool")).isEmpty();
    }
}
