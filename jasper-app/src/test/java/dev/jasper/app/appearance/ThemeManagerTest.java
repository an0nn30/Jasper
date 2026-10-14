package dev.jasper.app.appearance;
import dev.jasper.ui.theme.GeneratedTheme;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemeManagerTest {
    @Test void keysFlatLafSkipsAreRestoredWithoutReplacingItsOwn() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var theme = ThemeTestSupport.withUiKeys(ThemeTestSupport.generated(true), Map.of("SearchEverywhere.Header.background", "#123456",
                "EditorTabs.hoverBackground", "#00000019", "ComboBox.background", "#010101"));
            assertThat(ThemeManager.install(theme)).isTrue();
            assertColours(Map.of("SearchEverywhere.Header.background", "#123456", "EditorTabs.hoverBackground", "#00000019",
                "ComboBox.background", "#010101"));
            // A theme's own raw colours win consistently, including alpha and same-instance reinstall.
            UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            assertColours(Map.of("SearchEverywhere.Header.background", "#123456", "EditorTabs.hoverBackground", "#00000019",
                "ComboBox.background", "#010101"));
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void restoredAndDerivedKeysSurviveReinstallingTheSameLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var light = ThemeTestSupport.generated(false);
            ThemeManager.install(light);
            LookAndFeel lightLaf = UIManager.getLookAndFeel();
            ThemeManager.install(ThemeTestSupport.generated(true));
            // ThemeController rolls back like this when a later install fails.
            UIManager.setLookAndFeel(lightLaf);
            assertColours(Map.of("Jasper.semantic.surface.app", hex(light.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP)),
                "Jasper.semantic.accent", hex(light.semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT))));
            assertThat(UIManager.getFont("Label.font").getSize2D()).isEqualTo(13f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void switchingThemesLeavesNothingOfThePreviousOneBehind() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeTestSupport.withUiKeys(ThemeTestSupport.generated(false), Map.of("Plugins.hoverBackground", "#123456")));
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNotNull();
            var dark = ThemeTestSupport.generated(true);
            ThemeManager.install(dark);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNull();
            assertColours(Map.of("ToolWindow.background", (String) dark.uiKeys().get("ToolWindow.background"),
                "Jasper.semantic.surface.app", hex(dark.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP))));
        } finally { UIManager.setLookAndFeel(original); }
    }





    @Test void everyBuiltInVariantInstallsAsTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            for (var builtIn : dev.jasper.ui.theme.JasperTheme.builtIns()) {
                for (boolean dark : new boolean[] {true, false}) {
                    if (!builtIn.has(dark)) continue;
                    var theme = builtIn.generated(dark);
                    assertThat(ThemeManager.install(theme)).as("%s %s", builtIn.id(), dark ? "dark" : "light").isTrue();
                    assertThat(UIManager.getLookAndFeel().getName()).isEqualTo(builtIn.name());
                    assertThat(FlatLaf.isLafDark()).as(builtIn.id()).isEqualTo(dark);
                    assertThat(UIManager.getColor("Panel.background")).as(builtIn.id())
                        .isEqualTo(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void assertColours(Map<String, String> expected) {
        expected.forEach((key, value) -> assertThat(hex(UIManager.getColor(key))).as(key).isEqualTo(value));
    }

    @Test void aGeneratedThemeInstallsItsRolesAsTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            GeneratedTheme theme = ThemeTestSupport.generated(true);
            var roles = theme.semantic();
            assertThat(ThemeManager.install(theme)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Slate Steel");
            assertThat(FlatLaf.isLafDark()).isTrue();
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
            assertThat(UIManager.getColor("List.background")).isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_CARD));
            assertThat(UIManager.getColor("EditorTabs.underlineColor")).as("a key FlatLaf skips is put back")
                .isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.TAB_INDICATOR));
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT))
                .as("native search reads its canonical role directly").isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT));
            assertThat(UIManager.getInt("TextComponent.arc")).isEqualTo(2 * dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.CONTROL_RADIUS));
            assertThat(UIManager.getFont("Label.font").getSize2D()).as("canonical module typography replaces the legacy app override").isEqualTo(13f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** The 2026-10-01 spike: FlatLaf re-derives its controls from the base variables the theme sets. */
    @Test void flatLafReDerivesUnsetControlsFromTheBaseVariables() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeTestSupport.generated(true));
            Color first = UIManager.getColor("Button.disabledSelectedBackground");
            ThemeManager.install(ThemeTestSupport.generated(false));
            assertThat(UIManager.getColor("Button.disabledSelectedBackground")).isNotEqualTo(first);
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static String hex(Color color) {
        if (color == null) return null;
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
