package dev.moray.app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.image.BufferedImage;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static dev.moray.app.DesktopTestSupport.edt;

class AppIconsTest {
    @Test void bundledToolbarResourcesLoadAndPaintInBothAppearances() throws Exception {
        edt(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                FlatLightLaf.setup();
                int light = paintIcons();
                FlatDarkLaf.setup();
                int dark = paintIcons();
                assertThat(dark).as("dark chrome uses brighter icon strokes").isGreaterThan(light);
            } finally {
                try { UIManager.setLookAndFeel(original); }
                catch (javax.swing.UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }

    private static int paintIcons() {
        int brightness = 0;
        for (String name : new String[]{"square-plus", "app-window", "columns-2", "maximize", "search", "settings", "refresh"}) {
            var icon = AppIcons.icon(name);
            assertThat(icon.hasFound()).as(name).isTrue();
            BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { icon.paintIcon(null, graphics, 0, 0); } finally { graphics.dispose(); }
            int pixels = 0;
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                int color = image.getRGB(x, y);
                if ((color >>> 24) != 0) { pixels++; brightness += color & 255; }
            }
            assertThat(pixels).as(name + " has visible strokes").isGreaterThan(0);
        }
        return brightness;
    }
}
