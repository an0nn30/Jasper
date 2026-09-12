package dev.moray.app;

import dev.moray.terminal.Palette;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeStateTest {
    @Test void automaticBuiltinsFollowSystemButCustomColorsStayFixed() {
        var initial = ThemeState.defaults();
        assertThat(initial.systemChanged(BuiltinTheme.LIGHT).resolve().palette())
            .isEqualTo(Palette.morayLight());
        var fixed = initial.configure(new ColorsConfig(Appearance.SYSTEM, "custom"), Palette.morayDark());
        assertThat(fixed.systemChanged(BuiltinTheme.LIGHT).resolve())
            .isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.morayDark()));
    }

    @Test void manualChoiceSurvivesPaletteEditsButSavedAppearanceChangeClearsIt() {
        var colors = new ColorsConfig(Appearance.SYSTEM, "custom");
        var chosen = ThemeState.defaults().configure(colors, Palette.morayDark())
            .choose(Appearance.DARK).systemChanged(BuiltinTheme.LIGHT);
        var edited = chosen.configure(colors, Palette.morayLight());
        assertThat(edited.resolve().chrome()).isEqualTo(BuiltinTheme.DARK);
        assertThat(edited.resolve().palette()).isEqualTo(Palette.morayLight());
        assertThat(edited.choose(Appearance.SYSTEM).resolve().chrome()).isEqualTo(BuiltinTheme.LIGHT);
        assertThat(edited.configure(new ColorsConfig(Appearance.LIGHT, "custom"), Palette.morayLight()).override())
            .isNull();
    }
}
