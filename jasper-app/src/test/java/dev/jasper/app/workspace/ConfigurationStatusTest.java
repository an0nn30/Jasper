package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
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
            var themes = ThemeTestSupport.controller(true);
            for (boolean themeDark : new boolean[]{true, false}) {
                themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                var status = new WindowStatusBar();
                status.refreshTheme();
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
            var themes = ThemeTestSupport.controller(true);
            var status = new WindowStatusBar();
            for (boolean themeDark : new boolean[]{true, false}) {
                themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                status.refreshTheme();
                status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), Path.of("config.toml"), true));
                assertThat(status.getBackground()).isEqualTo(UIManager.getColor("StatusBar.background"));
                assertThat(contrast(status.configButton().getForeground(), status.getBackground())).isGreaterThanOrEqualTo(3);
            }
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
        });
    }
    @Test void statusBarPaintsTheThemesStatusSurfaceUnderATopRule() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            try {
                for (boolean themeDark : new boolean[]{true, false}) {
                    themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                    var status = new WindowStatusBar();
                    status.setSize(600, 30); status.doLayout();
                    var image = new java.awt.image.BufferedImage(600, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics(); status.paint(graphics); graphics.dispose();
                    assertThat(new Color(image.getRGB(300, 0))).as((themeDark ? "dark" : "light")).isEqualTo(UIManager.getColor("StatusBar.background"));
                    assertThat(new Color(image.getRGB(300, 15))).as((themeDark ? "dark" : "light")).isEqualTo(UIManager.getColor("StatusBar.background"));
                }
            } finally { themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH); }
        });
    }

    /**
     * Regression for the Task 5 status surface: a light theme with a dark title bar (as IntelliJ New UI Light) keeps
     * its light status bar on surface.status; status text and the running dot meet their floors on that surface, and
     * rail badges on the dark rail, while the shared status roles used on panels are not floored.
     */
    @Test void aDarkTitleBarOverALightThemeKeepsALightReadableStatusBarAndReadableRailBadges() throws Exception {
        edt(() -> {
            var generated = install(Map.of(dev.jasper.ui.theme.SemanticColor.SURFACE_APP, new Color(0x1c1c1c), dev.jasper.ui.theme.SemanticColor.ACCENT, new Color(0x1a2a6a)));
            try {
                var semantic = generated.semantic();
                var status = new WindowStatusBar();
                assertThat(status.getBackground()).isEqualTo(semantic.color(dev.jasper.ui.theme.SemanticColor.SURFACE_STATUS))
                    .isNotEqualTo(new Color(0x1c1c1c));
                assertStatusReadable(status);
                for (var tone : dev.jasper.app.contributions.PanelBadge.Tone.values())
                    assertThat(contrast(badge(tone), new Color(0x1c1c1c))).as("rail badge " + tone).isGreaterThanOrEqualTo(3);
                assertThat(semantic.color(dev.jasper.ui.theme.SemanticColor.STATUS_SUCCESS)).as("shared role unchanged")
                    .isEqualTo(ThemeTestSupport.color(false, dev.jasper.ui.theme.SemanticColor.STATUS_SUCCESS));
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    /** An explicit dark status surface on a light theme: painted status text is floored there, the shared roles are not. */
    @Test void statusTextIsFlooredOnlyWhereItIsPaintedOnTheStatusSurface() throws Exception {
        edt(() -> {
            var generated = install(Map.of(dev.jasper.ui.theme.SemanticColor.SURFACE_STATUS, new Color(0x1c1c1c)));
            try {
                var semantic = generated.semantic();
                var status = new WindowStatusBar();
                assertThat(status.getBackground()).isEqualTo(new Color(0x1c1c1c));
                assertStatusReadable(status);
                for (var role : List.of(dev.jasper.ui.theme.SemanticColor.STATUS_SUCCESS, dev.jasper.ui.theme.SemanticColor.STATUS_WARNING,
                        dev.jasper.ui.theme.SemanticColor.STATUS_DANGER))
                    // Unfloored: panels show Slate Cobalt's authored status colours (Light status.success is 3.32:1 on surface.panel).
                    assertThat(semantic.color(role)).as(role.key() + " keeps its authored colour on panels")
                        .isEqualTo(ThemeTestSupport.color(false, role));
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    /** Slate Cobalt Light with {@code roles} recoloured, installed as the look and feel. */
    private static dev.jasper.ui.theme.GeneratedTheme install(Map<dev.jasper.ui.theme.SemanticColor, Color> roles) {
        ThemeTestSupport.install(ThemeTestSupport.recolored(ThemeTestSupport.generated(false), roles));
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
