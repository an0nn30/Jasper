package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class GtkThemeControllerTest {
    @AfterEach void restore() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> new ThemeController()); }

    @Test void gtkUsesDecoratedChromeAndItsDerivedTerminal() {
        var themes = GtkTestThemes.themes(GtkTestThemes.LIGHT);
        assertThat(themes.style()).isEqualTo(ThemeStyle.GTK);
        assertThat(themes.requestedStyle()).isEqualTo(ThemeStyle.GTK);
        assertThat(themes.fallbackReason()).isEmpty();
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.GTK);
        assertThat(themes.current().palette().background()).isEqualTo(Color.WHITE);
        assertThat(themes.current().palette().ansi()).isEqualTo(Palette.jasperLight().ansi());
        assertThat(themes.choice()).isEqualTo(Appearance.LIGHT);
        assertThat(UIManager.getBoolean("Jasper.gtk")).isTrue();
        var before = themes.current();
        themes.selectAppearance(Appearance.DARK);
        themes.configure(Appearance.DARK);
        assertThat(themes.current()).isEqualTo(before);
    }

    @Test void darkGtkThemeIsDarkEverywhere() {
        var themes = GtkTestThemes.themes(GtkTestThemes.DARK);
        assertThat(themes.choice()).isEqualTo(Appearance.DARK);
        assertThat(themes.current().dark()).isTrue();
        assertThat(themes.current().palette().ansi()).isEqualTo(Palette.jasperDark().ansi());
    }

    @Test void unavailableGtkFallsBackToLiveModernWithAReason() {
        var themes = GtkTestThemes.unavailable(Appearance.LIGHT);
        assertThat(themes.style()).isEqualTo(ThemeStyle.MODERN);
        assertThat(themes.requestedStyle()).isEqualTo(ThemeStyle.GTK);
        assertThat(themes.fallbackReason()).hasValueSatisfying(reason ->
            assertThat(reason).contains("GTK is not available on this desktop").contains("modern"));
        assertThat(UIManager.getLookAndFeel()).isInstanceOf(com.formdev.flatlaf.FlatLightLaf.class);
        themes.selectAppearance(Appearance.DARK);
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
    }

    @Test void realInstallerEitherInstallsGtkOrFallsBack() {
        var themes = new ThemeController(ThemeStyle.GTK, Appearance.DARK);
        assertThat(themes.requestedStyle()).isEqualTo(ThemeStyle.GTK);
        if (themes.style() == ThemeStyle.GTK) {
            assertThat(UIManager.getLookAndFeel().getClass().getName()).isEqualTo(GtkDefaults.LOOK_AND_FEEL);
            assertThat(themes.fallbackReason()).isEmpty();
        } else {
            assertThat(themes.style()).isEqualTo(ThemeStyle.MODERN);
            assertThat(themes.fallbackReason()).isPresent();
        }
    }

    @Test void fontReconfigurationKeepsTheGtkPalette() {
        var themes = GtkTestThemes.themes(GtkTestThemes.DARK);
        var palette = themes.current().palette();
        themes.configure(Appearance.DARK, new UiFontConfig("Serif", 18));
        assertThat(themes.current().palette()).isEqualTo(palette);
        assertThat(UIManager.getBoolean("Jasper.gtk")).isTrue();
        assertThat(UIManager.getFont("Label.font").getFamily()).isEqualTo("Serif");
    }

    @Test void otherStylesNeverReportAFallback() {
        assertThat(new ThemeController(ThemeStyle.RETRO, Appearance.DARK).fallbackReason()).isEmpty();
        assertThat(new ThemeController(ThemeStyle.MODERN, Appearance.DARK).requestedStyle()).isEqualTo(ThemeStyle.MODERN);
    }
}
