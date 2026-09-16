package dev.jasper.app;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ConfigurationStatusTest {
    @Test void statusKeepsConfigAndMetadataBoundedAndReadableInBothThemes() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            for (BuiltinTheme theme : BuiltinTheme.values()) {
                themes.select(theme);
                var status = new WindowStatusBar();
                status.applyPalette(theme.palette());
                for (var severity : ConfigDiagnostic.Severity.values()) {
                    Path file = Path.of("fixture", "settings.toml");
                    status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(
                        new ConfigDiagnostic(severity, file, 1234567, 1, "window.tab_height", "<html>Check this key")), file, true));
                    status.setMetadata("<html>very long shell".repeat(80), "/a/long/directory/".repeat(80), "120 × 36", true, false);
                    assertThat(status.getMinimumSize().width).isZero();
                    assertThat(status.getPreferredSize().width).isZero();
                    assertThat(status.getBackground()).isEqualTo(theme.palette().background());
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
    @Test void statusSurfaceFollowsTheAppliedPaletteBackground() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            var status = new WindowStatusBar();
            for (var theme : BuiltinTheme.values()) {
                themes.select(theme);
                status.applyPalette(theme.palette());
                status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), Path.of("config.toml"), true));
                assertThat(status.getBackground()).isEqualTo(theme.palette().background());
                assertThat(contrast(status.configButton().getForeground(), theme.palette().background())).isGreaterThanOrEqualTo(3);
            }
            themes.select(BuiltinTheme.DARK);
        });
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
