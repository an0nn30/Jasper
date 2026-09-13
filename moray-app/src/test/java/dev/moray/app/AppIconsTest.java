package dev.moray.app;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.image.BufferedImage;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class AppIconsTest {
    private static final String[] ICONS = {"square-plus", "app-window", "columns-2", "maximize", "search", "settings", "refresh"};

    @Test void toolbarIconsPaintNeutralOutlinesWithoutFilledFields() throws Exception {
        edt(() -> {
            new ThemeController();
            for (String name : ICONS) {
                FlatSVGIcon icon = AppIcons.icon(name);
                assertThat(icon.hasFound()).as(name).isTrue();
                assertThat(icon.getIconWidth()).isEqualTo(16);
                BufferedImage image = paint(icon);
                assertThat(image.getRGB(2, 30) >>> 24).as(name + " no soft field").isZero();
                assertThat(hasColor(image, 0xbcc2d2)).as(name + " neutral stroke").isTrue();
            }
        });
    }

    @Test void existingOutlineRecolorsOnThemeChange() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            FlatSVGIcon icon = AppIcons.icon("square-plus");
            assertThat(hasColor(paint(icon), 0xbcc2d2)).isTrue();
            themes.select(BuiltinTheme.LIGHT);
            assertThat(hasColor(paint(icon), 0x383a42)).isTrue();
            assertThat(hasColor(paint(icon), 0xbcc2d2)).isFalse();
        });
    }

    private static BufferedImage paint(FlatSVGIcon icon) {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.scale(2, 2); icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return image;
    }
    private static boolean hasColor(BufferedImage image, int color) {
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            if (image.getRGB(x, y) == (0xff000000 | color)) return true;
        return false;
    }
}
