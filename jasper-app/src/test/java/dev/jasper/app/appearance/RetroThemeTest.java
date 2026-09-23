package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import javax.swing.*;
import javax.swing.plaf.metal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class RetroThemeTest {
    @AfterEach void restore() throws Exception { SwingUtilities.invokeAndWait(() -> new ThemeController()); }

    @Test void retroIsStockMetalWithLightChromeAndAnIndependentBlackTerminal() {
        var themes = new ThemeController(ThemeStyle.RETRO, Appearance.DARK);
        assertThat(UIManager.getLookAndFeel()).isExactlyInstanceOf(MetalLookAndFeel.class);
        assertThat(MetalLookAndFeel.getCurrentTheme()).isExactlyInstanceOf(OceanTheme.class);
        assertThat(new JButton().getUI()).isInstanceOf(MetalButtonUI.class);
        assertThat(new JTabbedPane().getUI()).isInstanceOf(MetalTabbedPaneUI.class);
        assertThat(themes.choice()).isEqualTo(Appearance.LIGHT);
        assertThat(themes.current().palette().background()).isEqualTo(java.awt.Color.BLACK);
        for (String key : java.util.List.of("Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground"))
            assertThat(UIManager.getColor(key)).as(key).isNotNull();
        var before = themes.current();
        themes.selectAppearance(Appearance.DARK);
        themes.configure(Appearance.DARK);
        assertThat(themes.current()).isEqualTo(before);
        assertThat(themes.style()).isEqualTo(ThemeStyle.RETRO);
    }

    @Test void fontSelectionPrefersTheHostPlatformEvenWhenOtherPlatformFontsAreInstalled() {
        var all = java.util.Set.of("Helvetica Neue", "Helvetica", "Segoe UI", "Tahoma",
            "Noto Sans", "Liberation Sans", "DejaVu Sans");
        assertThat(MetalDefaults.fontFamily("Mac OS X", all)).isEqualTo("Helvetica Neue");
        assertThat(MetalDefaults.fontFamily("Windows 11", all)).isEqualTo("Segoe UI");
        assertThat(MetalDefaults.fontFamily("Linux", all)).isEqualTo("Noto Sans");
        assertThat(MetalDefaults.fontFamily("Mac OS X", java.util.Set.of("Helvetica"))).isEqualTo("Helvetica");
        assertThat(MetalDefaults.fontFamily("Windows 10", java.util.Set.of("Tahoma"))).isEqualTo("Tahoma");
        assertThat(MetalDefaults.fontFamily("Linux", java.util.Set.of("Liberation Sans", "DejaVu Sans")))
            .isEqualTo("Liberation Sans");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Mac OS X", "Windows 11", "Linux", "unknown"})
    void missingPreferredFontsRemainPortable(String os) {
        assertThat(MetalDefaults.fontFamily(os, java.util.Set.of("DejaVu Sans"))).isEqualTo("DejaVu Sans");
        assertThat(MetalDefaults.fontFamily(os, java.util.Set.of())).isEqualTo(java.awt.Font.SANS_SERIF);
        assertThat(MetalDefaults.fontFamily(os, java.util.Set.of("Unrelated Font"))).isEqualTo(java.awt.Font.SANS_SERIF);
    }

    @Test void installedRetroFontsAreRegularAndModernFontsAreRestored() {
        new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        var modern = UIManager.getFont("Label.font");
        new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT);
        for (String key : java.util.List.of("Menu.font", "Label.font", "Button.font", "TextField.font",
                "TabbedPane.font", "InternalFrame.titleFont")) {
            assertThat(UIManager.getFont(key).getStyle()).as(key).isEqualTo(java.awt.Font.PLAIN);
            assertThat(UIManager.getFont(key).getSize()).as(key).isPositive();
        }
        new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        assertThat(UIManager.getFont("Label.font")).isEqualTo(modern);
    }

    @Test void metalAliasesDoNotLeakIntoTheNextModernInstallation() {
        new ThemeController(ThemeStyle.RETRO, Appearance.DARK);
        assertThat(UIManager.getBoolean("Jasper.retro")).isTrue();
        new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        assertThat(UIManager.getBoolean("Jasper.retro")).isFalse();
        assertThat(UIManager.getLookAndFeel()).isInstanceOf(com.formdev.flatlaf.FlatLightLaf.class);
        assertThat(new JButton().getUI()).isInstanceOf(BrandedButtonUI.class);
    }
@Test void failedRetroInstallationRestoresBothGlobalDefaultsOwners() {
    new ThemeController();
    var before = UIManager.getLookAndFeel();
    var beforeMetal = MetalLookAndFeel.getCurrentTheme();
    assertThatThrownBy(() -> new ThemeController(ThemeStyle.RETRO, Appearance.DARK, theme -> {
        MetalDefaults.install();
        return false;
    })).isInstanceOf(ThemeController.InstallationFailure.class);
    assertThat(UIManager.getLookAndFeel()).isSameAs(before);
    assertThat(MetalLookAndFeel.getCurrentTheme()).isSameAs(beforeMetal);
    assertThat(UIManager.getBoolean("Jasper.retro")).isFalse();
}

}
