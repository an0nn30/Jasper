package dev.jasper.app;

import dev.jasper.terminal.Palette;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeStateTest {
    @Test void savedVariantResolvesToItsBuiltInThemeAndPalette() {
        assertThat(ThemeState.defaults().resolve()).isEqualTo(new ResolvedTheme(BuiltinTheme.DARK, Palette.jasperDark()));
        assertThat(ThemeState.defaults().configure(Appearance.LIGHT).resolve())
            .isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperLight()));
    }

    @Test void temporaryChoiceOverridesSavedUntilTheSavedVariantChanges() {
        var chosen = ThemeState.defaults().choose(Appearance.LIGHT);
        assertThat(chosen.resolve().chrome()).isEqualTo(BuiltinTheme.LIGHT);
        assertThat(chosen.configure(Appearance.DARK).choice()).isEqualTo(Appearance.LIGHT);
        assertThat(chosen.configure(Appearance.LIGHT).override()).isNull();
        assertThat(chosen.configure(Appearance.LIGHT).choose(Appearance.DARK).resolve().palette())
            .isEqualTo(Palette.jasperDark());
    }
}
