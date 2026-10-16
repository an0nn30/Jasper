package dev.jasper.app.appearance;
import dev.jasper.ui.theme.GeneratedTheme;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.Color;
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
            // The theme's own roles behind keys FlatLaf skips or derives itself, and an alpha-carrying role (border.subtle).
            var theme = ThemeTestSupport.recolored(ThemeTestSupport.generated(true), Map.of(
                dev.jasper.ui.theme.SemanticColor.SURFACE_APP, new Color(0x12, 0x34, 0x56),
                dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT, new Color(0x01, 0x01, 0x01)));
            assertThat(ThemeManager.install(theme, Kit.DEFAULT)).isTrue();
            var expected = Map.of("SearchEverywhere.Header.background", "#123456", "ComboBox.background", "#010101",
                "Separator.foreground", hex(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE)));
            assertThat(expected.get("Separator.foreground")).as("Slate Cobalt Dark's border.subtle carries alpha").hasSize(9);
            assertColours(expected);
            // A theme's own raw colours win consistently, including alpha and same-instance reinstall.
            UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            assertColours(expected);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void restoredAndDerivedKeysSurviveReinstallingTheSameLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var light = ThemeTestSupport.generated(false);
            ThemeManager.install(light, Kit.DEFAULT);
            LookAndFeel lightLaf = UIManager.getLookAndFeel();
            ThemeManager.install(ThemeTestSupport.generated(true), Kit.DEFAULT);
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
            ThemeManager.install(ThemeTestSupport.generated(false), Kit.DEFAULT);
            // A key only the previous look and feel's defaults hold.
            UIManager.getLookAndFeelDefaults().put("Plugins.hoverBackground", new javax.swing.plaf.ColorUIResource(new Color(0x12, 0x34, 0x56)));
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNotNull();
            var dark = ThemeTestSupport.generated(true);
            ThemeManager.install(dark, Kit.DEFAULT);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNull();
            assertColours(Map.of("ToolWindow.background",
                (String) dev.jasper.ui.theme.UiColorKeys.flatLaf(dark, dev.jasper.ui.theme.UiKeySet.DEFAULT).get("ToolWindow.background"),
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
                    assertThat(ThemeManager.install(theme, Kit.DEFAULT)).as("%s %s", builtIn.id(), dark ? "dark" : "light").isTrue();
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
            assertThat(ThemeManager.install(theme, Kit.DEFAULT)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Slate Cobalt");
            assertThat(FlatLaf.isLafDark()).isTrue();
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
            assertThat(UIManager.getColor("List.background")).isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_CARD));
            assertThat(UIManager.getColor("EditorTabs.underlineColor")).as("a key FlatLaf skips is put back")
                .isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.TAB_INDICATOR));
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT))
                .as("native search reads its canonical role directly").isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT));
            assertThat(UIManager.getInt("TextComponent.arc")).isEqualTo(2 * dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_RADIUS));
            assertThat(UIManager.getFont("Label.font").getSize2D()).as("canonical module typography replaces the legacy app override").isEqualTo(13f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** The 2026-10-01 spike: FlatLaf re-derives its controls from the base variables the theme sets. */
    @Test void flatLafReDerivesUnsetControlsFromTheBaseVariables() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeTestSupport.generated(true), Kit.DEFAULT);
            Color first = UIManager.getColor("Button.disabledSelectedBackground");
            ThemeManager.install(ThemeTestSupport.generated(false), Kit.DEFAULT);
            assertThat(UIManager.getColor("Button.disabledSelectedBackground")).isNotEqualTo(first);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** Enterprise presentation plan 1: the app's split divider is the kit's, and the kit travels with the look and feel. */
    @Test void theSplitDividerComesFromTheKitAndTheKitTravelsWithTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var kit = divider();
            assertThat(ThemeManager.install(ThemeTestSupport.generated(true), kit)).isTrue();
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(5);
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).isSameAs(kit);
            assertThat(UIManager.getBoolean("TitlePane.centerTitle")).as("still unconditional in plan 1").isTrue();
            ThemeManager.install(ThemeTestSupport.generated(true), Kit.DEFAULT);
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(8);
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).isSameAs(Kit.DEFAULT);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** Start-up installs under its kit, and every later install of the same controller keeps it. */
    @Test void startUpInstallsUnderItsKitAndConfigureKeepsIt() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var kit = divider();
        try (var controller = ThemeController.startup(true, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit,
                failure -> { throw new AssertionError(failure); })) {
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).isSameAs(kit);
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(5);
            controller.configure(false, TerminalSchemeChoice.MATCH);
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).as("configure reinstalls under the start-up kit").isSameAs(kit);
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(5);
        } finally {
            ThemeTestSupport.controller(true).close();
            UIManager.setLookAndFeel(original);
        }
    }

    /** The default kit with the split divider at 5. */
    private static Kit divider() {
        return new Kit("divider", Kit.DEFAULT.metrics().with(DesignMetrics.Role.NATIVE_SPLIT_DIVIDER, 5), Kit.DEFAULT.type(),
            Kit.DEFAULT.faces(), Kit.DEFAULT.uiKeys());
    }

    private static String hex(Color color) {
        if (color == null) return null;
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
