package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class SplitDividerTest {
    @Test void dividerRemainsVisibleAlongBothAxesAcrossThemeChanges() throws Exception {
        edt(() -> {
            ThemeController themes = new ThemeController();
            JSplitPane split = new JSplitPane();
            try {
                for (Theme theme : java.util.List.of(Theme.DARK, Theme.LIGHT)) {
                    themes.select(theme);
                    SwingUtilities.updateComponentTreeUI(split);
                    split.setBorder(null);
                    for (int orientation : new int[] {JSplitPane.HORIZONTAL_SPLIT, JSplitPane.VERTICAL_SPLIT}) {
                        split.setOrientation(orientation);
                        split.setSize(400, 300);
                        split.setDividerLocation(0.5);
                        split.doLayout();
                        var divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
                        BufferedImage image = new BufferedImage(divider.getWidth(), divider.getHeight(), BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics();
                        graphics.setColor(split.getBackground());
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        divider.paint(graphics);
                        graphics.dispose();
                        boolean vertical = orientation == JSplitPane.HORIZONTAL_SPLIT;
                        int across = vertical ? image.getWidth() : image.getHeight();
                        int length = vertical ? image.getHeight() : image.getWidth();
                        // Sample the entire divider away from the central grip: a grip alone is insufficient.
                        for (int along = 0; along < length; along++) {
                            if (Math.abs(along - length / 2) < UIScale.scale(16)) continue;
                            Color stroke = new Color(image.getRGB(vertical ? across / 2 : along,
                                vertical ? along : across / 2));
                            assertThat(contrast(stroke, theme.palette().background()))
                                .as("%s orientation %s at %s", theme, orientation, along).isGreaterThanOrEqualTo(3);
                        }
                        int painted = 0;
                        for (int cross = 0; cross < across; cross++) {
                            if (image.getRGB(vertical ? cross : 1, vertical ? 1 : cross) != split.getBackground().getRGB()) painted++;
                        }
                        assertThat(painted).as("thin stroke within a wider resize target").isEqualTo(UIScale.scale(2));
                        assertThat(across).isGreaterThanOrEqualTo(UIScale.scale(8));
                    }
                }
            } finally {
                themes.select(Theme.DARK);
            }
        });
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
