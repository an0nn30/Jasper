package dev.jasper.app;

import dev.jasper.terminal.Palette;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeStateTest {
    @Test void automaticBuiltinsFollowSystemButCustomColorsStayFixed() {
        var initial = ThemeState.defaults();
        assertThat(initial.systemChanged(BuiltinTheme.LIGHT).resolve().palette())
            .isEqualTo(Palette.jasperLight());
        var fixed = initial.configure(new ColorsConfig(Appearance.SYSTEM, "custom"), Palette.jasperDark());
        assertThat(fixed.systemChanged(BuiltinTheme.LIGHT).resolve())
            .isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperDark()));
    }

    @Test void manualChoiceSurvivesPaletteEditsButSavedAppearanceChangeClearsIt() {
        var colors = new ColorsConfig(Appearance.SYSTEM, "custom");
        var chosen = ThemeState.defaults().configure(colors, Palette.jasperDark())
            .choose(Appearance.DARK).systemChanged(BuiltinTheme.LIGHT);
        var edited = chosen.configure(colors, Palette.jasperLight());
        assertThat(edited.resolve().chrome()).isEqualTo(BuiltinTheme.DARK);
        assertThat(edited.resolve().palette()).isEqualTo(Palette.jasperLight());
        assertThat(edited.choose(Appearance.SYSTEM).resolve().chrome()).isEqualTo(BuiltinTheme.LIGHT);
        assertThat(edited.configure(new ColorsConfig(Appearance.LIGHT, "custom"), Palette.jasperLight()).override())
            .isNull();
    }
}
