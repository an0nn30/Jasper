package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ConfigurationStatusTest {
    @Test void statusKeepsConfigAndMetadataBoundedAndReadableInBothThemes() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            for (Theme theme : java.util.List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                themes.configure(theme);
                var status = new WindowStatusBar();
                status.applyPalette(theme.palette());
                for (var severity : ConfigDiagnostic.Severity.values()) {
                    Path file = Path.of("fixture", "settings.toml");
                    status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(
                        new ConfigDiagnostic(severity, file, 1234567, 1, "window.tab_height", "<html>Check this key")), file, true));
                    DesktopTestSupport.setStatusMetadata(status, "<html>very long shell".repeat(80), "/a/long/directory/".repeat(80), "120 × 36", true, false);
                    assertThat(status.getMinimumSize().width).isZero();
                    assertThat(status.getPreferredSize().width).isZero();
                    assertThat(status.getBackground()).isEqualTo(UIManager.getColor("StatusBar.background"));
                    assertThat(contrast(status.configButton().getForeground(), status.getBackground())).isGreaterThanOrEqualTo(4.5);
                    for (int width : new int[]{958, 320, 100, 20, 0}) {
                        status.setSize(width, 30); layout(status);
                        bounded(status);
                        var image = new java.awt.image.BufferedImage(Math.max(1, width), 30, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var graphics = image.createGraphics(); status.paint(graphics); graphics.dispose();
                    }
                }
                status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), Path.of("/tmp/config.toml"), true));
                assertThat(contrast(status.configButton().getForeground(), status.getBackground())).isGreaterThanOrEqualTo(4.5);
            }
        });
    }
    @Test void statusSurfaceFollowsChromeRatherThanTerminalPalette() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            var status = new WindowStatusBar();
            for (var theme : java.util.List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                themes.configure(theme);
                status.applyPalette(theme.palette());
                status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), Path.of("config.toml"), true));
                assertThat(status.getBackground()).isEqualTo(UIManager.getColor("StatusBar.background"));
                assertThat(contrast(status.configButton().getForeground(), status.getBackground())).isGreaterThanOrEqualTo(3);
            }
            themes.configure(Theme.DARK);
        });
    }
    @Test void statusBarPaintsTheThemesStatusSurfaceUnderATopRule() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            try {
                for (var theme : List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                    themes.configure(theme);
                    var status = new WindowStatusBar();
                    status.setSize(600, 30); status.doLayout();
                    var image = new java.awt.image.BufferedImage(600, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics(); status.paint(graphics); graphics.dispose();
                    assertThat(new Color(image.getRGB(300, 0))).as(theme.name()).isEqualTo(UIManager.getColor("StatusBar.background"));
                    assertThat(new Color(image.getRGB(300, 15))).as(theme.name()).isEqualTo(UIManager.getColor("StatusBar.background"));
                }
            } finally { themes.configure(Theme.DARK); }
        });
    }

    /**
     * Regression for the Task 5 status surface: a light theme with a dark title bar (as IntelliJ New UI Light) keeps
     * its light status bar on surface.status; status text and the running dot meet their floors on that surface, and
     * rail badges on the dark rail, while the shared status roles used on panels are not floored.
     */
    @Test void aDarkTitleBarOverALightThemeKeepsALightReadableStatusBarAndReadableRailBadges() throws Exception {
        edt(() -> {
            var generated = install(Map.of(dev.jasper.ui.theme.Token.TITLE_BAR, new Color(0x1c1c1c)),
                Map.of(dev.jasper.ui.theme.SemanticColor.ACCENT, new Color(0x1a2a6a)));
            try {
                var semantic = generated.semantic();
                var status = new WindowStatusBar();
                assertThat(status.getBackground()).isEqualTo(semantic.color(dev.jasper.ui.theme.SemanticColor.SURFACE_STATUS))
                    .isEqualTo(generated.tokens().get(dev.jasper.ui.theme.Token.STATUS_BAR)).isNotEqualTo(new Color(0x1c1c1c));
                assertStatusReadable(status);
                for (var tone : dev.jasper.app.contributions.PanelBadge.Tone.values())
                    assertThat(contrast(badge(tone), new Color(0x1c1c1c))).as("rail badge " + tone).isGreaterThanOrEqualTo(3);
                assertThat(semantic.color(dev.jasper.ui.theme.SemanticColor.STATUS_SUCCESS)).as("shared role unchanged")
                    .isEqualTo(generated.tokens().get(dev.jasper.ui.theme.Token.SUCCESS));
            } finally { new ThemeController(); }
        });
    }

    /** An explicit dark status surface on a light theme: painted status text is floored there, the shared roles are not. */
    @Test void statusTextIsFlooredOnlyWhereItIsPaintedOnTheStatusSurface() throws Exception {
        edt(() -> {
            var generated = install(Map.of(), Map.of(dev.jasper.ui.theme.SemanticColor.SURFACE_STATUS, new Color(0x1c1c1c)));
            try {
                var semantic = generated.semantic();
                var status = new WindowStatusBar();
                assertThat(status.getBackground()).isEqualTo(new Color(0x1c1c1c));
                assertStatusReadable(status);
                for (var role : List.of(dev.jasper.ui.theme.SemanticColor.STATUS_SUCCESS, dev.jasper.ui.theme.SemanticColor.STATUS_WARNING,
                        dev.jasper.ui.theme.SemanticColor.STATUS_DANGER))
                    assertThat(contrast(semantic.color(role), semantic.color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL)))
                        .as(role.key() + " stays readable on panels").isGreaterThanOrEqualTo(4.5);
            } finally { new ThemeController(); }
        });
    }

    private static dev.jasper.ui.theme.GeneratedTheme install(Map<dev.jasper.ui.theme.Token, Color> tokenChanges,
                                                              Map<dev.jasper.ui.theme.SemanticColor, Color> semantic) {
        var base = Theme.JASPER_LIGHT.source().document();
        var tokens = new java.util.EnumMap<dev.jasper.ui.theme.Token, Color>(dev.jasper.ui.theme.Token.class);
        tokens.putAll(base.tokens());
        for (var derived : List.of(dev.jasper.ui.theme.Token.SUCCESS, dev.jasper.ui.theme.Token.WARNING,
                dev.jasper.ui.theme.Token.ERROR, dev.jasper.ui.theme.Token.LINK)) tokens.remove(derived);
        tokens.putAll(tokenChanges);
        var document = new dev.jasper.ui.theme.ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), base.parentLine(),
            base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), tokens, base.syntax(), base.ui(), base.icons(), base.workspace(), semantic);
        var generated = dev.jasper.ui.theme.ThemeGenerator.generate(document);
        new ThemeController(new Theme("status-surface", "Status surface", false,
            dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(generated.terminal()), new Theme.UserFile(Path.of("status-surface.toml"), document)));
        return dev.jasper.ui.swing.FlatLafInstaller.installedTheme();
    }

    private static void assertStatusReadable(WindowStatusBar status) {
        for (var severity : ConfigDiagnostic.Severity.values()) {
            Path file = Path.of("fixture", "settings.toml");
            status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(
                new ConfigDiagnostic(severity, file, 1, 1, "window.tab_height", "Check this key")), file, true));
            assertThat(contrast(status.configButton().getForeground(), status.getBackground())).as(severity.name()).isGreaterThanOrEqualTo(4.5);
        }
        status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), Path.of("config.toml"), true));
        assertThat(contrast(status.configButton().getForeground(), status.getBackground())).isGreaterThanOrEqualTo(4.5);
        DesktopTestSupport.setStatusMetadata(status, "sh", "/tmp", "80 × 24", true, false);
        status.setSize(600, 30); layout(status);
        var image = new java.awt.image.BufferedImage(600, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); status.paint(graphics); graphics.dispose();
        int x = com.formdev.flatlaf.util.UIScale.scale(14) + com.formdev.flatlaf.util.UIScale.scale(5) / 2;
        assertThat(contrast(new Color(image.getRGB(x, 15)), status.getBackground())).as("running dot").isGreaterThanOrEqualTo(3);
    }

    private static Color badge(dev.jasper.app.contributions.PanelBadge.Tone tone) {
        var icon = new BadgedIcon(new ImageIcon(new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)), tone);
        var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
        int dot = com.formdev.flatlaf.util.UIScale.scale(6);
        return new Color(image.getRGB(16 - dot / 2 - 1, dot / 2));
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }
    private static void bounded(Container parent) {
        for (Component child : parent.getComponents()) {
            assertThat(child.getX()).isGreaterThanOrEqualTo(0);
            assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(parent.getWidth());
            if (child instanceof Container container) bounded(container);
        }
    }
    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }
    private static double luminance(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
}
