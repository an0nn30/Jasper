package dev.jasper.app;

import java.awt.image.BufferedImage;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class AppIconsTest {
    private static final String[] ICONS = {"square-plus", "app-window", "columns-2", "maximize", "search", "settings", "refresh", "close", "plus"};

    @Test void bundledGnomeIconsLoadAndPaintAtTheirOriginalSize() throws Exception {
        edt(() -> {
            for (String name : ICONS) {
                Icon icon = AppIcons.icon(name);
                assertThat(icon.getIconWidth()).as(name).isEqualTo(16);
                assertThat(icon.getIconHeight()).as(name).isEqualTo(16);
                int[] pixels = paint(icon);
                assertThat(java.util.Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) != 0)).as(name).isTrue();
                assertThat(java.util.Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) == 0)).as(name).isTrue();
            }
        });
    }

    @Test void originalArtworkIsUnchangedByLookAndFeel() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            Icon icon = AppIcons.icon("square-plus");
            int[] before = paint(icon);
            themes.selectLaf(UiLookAndFeel.NIMBUS);
            assertThat(paint(icon)).containsExactly(before);
            themes.selectLaf(UiLookAndFeel.METAL);
        });
    }

    @Test void unknownIconIsRejected() {
        assertThatThrownBy(() -> AppIcons.icon("missing")).isInstanceOf(IllegalArgumentException.class);
    }

    private static int[] paint(Icon icon) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return image.getRGB(0, 0, 16, 16, null, 0, 16);
    }
}
