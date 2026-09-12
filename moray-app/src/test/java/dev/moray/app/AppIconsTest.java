package dev.moray.app;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class AppIconsTest {
    private static final IconCase[] ICONS = {
        new IconCase("square-plus", 0x61afef, 0x315fc4),
        new IconCase("app-window", 0x98c379, 0x3d7d3b),
        new IconCase("columns-2", 0xc678dd, 0x87218b),
        new IconCase("maximize", 0x56b6c2, 0x006b96),
        new IconCase("search", 0xe5c07b, 0x986801),
        new IconCase("settings", 0xa4adba, 0x696c77),
        new IconCase("refresh", 0xe06c75, 0xca4035)
    };

    @Test void everyToolbarIconPaintsTheSelectedTwoToneTreatmentInBothThemes() throws Exception {
        edt(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                var themes = new ThemeController();
                Set<Integer> darkStrokes = paintAndVerify(ICONS, true);
                themes.select(BuiltinTheme.LIGHT);
                Set<Integer> lightStrokes = paintAndVerify(ICONS, false);

                assertThat(darkStrokes).hasSize(ICONS.length);
                assertThat(lightStrokes).hasSize(ICONS.length);
                assertThat(lightStrokes).doesNotContainAnyElementsOf(darkStrokes);
            } finally {
                try { UIManager.setLookAndFeel(original); }
                catch (javax.swing.UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }

    @Test void anExistingIconRecolorsWhenTheApplicationThemeChanges() throws Exception {
        edt(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                var themes = new ThemeController();
                FlatSVGIcon icon = AppIcons.icon("square-plus");
                assertThat(paint(icon).opaqueRgb()).contains(0x61afef);

                themes.select(BuiltinTheme.LIGHT);

                assertThat(paint(icon).opaqueRgb()).contains(0x315fc4).doesNotContain(0x61afef);
            } finally {
                try { UIManager.setLookAndFeel(original); }
                catch (javax.swing.UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }

    @Test void enclosedIconFacesPaintMoreStronglyThanTheirSoftFields() throws Exception {
        edt(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                new ThemeController();
                for (FaceCase item : new FaceCase[]{
                    new FaceCase("square-plus", 7, 7),
                    new FaceCase("app-window", 14, 14),
                    new FaceCase("columns-2", 8, 14),
                    new FaceCase("settings", 14, 7)
                }) {
                    BufferedImage image = paintImage(AppIcons.icon(item.name()));
                    int fieldAlpha = alphaAtLogical(image, 14, 26);
                    int faceAlpha = alphaAtLogical(image, item.x(), item.y());
                    assertThat(fieldAlpha).as(item.name() + " 18% field").isBetween(35, 60);
                    assertThat(faceAlpha).as(item.name() + " 24% face over field")
                        .isGreaterThan(fieldAlpha + 25);
                }
            } finally {
                try { UIManager.setLookAndFeel(original); }
                catch (javax.swing.UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }

    private static Set<Integer> paintAndVerify(IconCase[] cases, boolean dark) {
        Set<Integer> strokes = new LinkedHashSet<>();
        for (IconCase item : cases) {
            FlatSVGIcon icon = AppIcons.icon(item.name());
            int stroke = dark ? item.darkRgb() : item.lightRgb();
            assertThat(icon.hasFound()).as(item.name()).isTrue();
            assertThat(icon.getWidth()).as(item.name() + " logical width").isEqualTo(28);
            assertThat(icon.getHeight()).as(item.name() + " logical height").isEqualTo(28);

            Painted painted = paint(icon);
            assertThat(painted.opaqueRgb()).as(item.name() + " stroke color").contains(stroke);
            assertThat(painted.translucentPixels()).as(item.name() + " soft color field").isGreaterThan(100);
            assertThat(painted.opaquePixels()).as(item.name() + " recognizable outline").isGreaterThan(10);
            strokes.add(stroke);
        }
        return strokes;
    }

    private static Painted paint(FlatSVGIcon icon) {
        BufferedImage image = paintImage(icon);
        Set<Integer> opaqueRgb = new LinkedHashSet<>();
        int translucent = 0;
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            Color color = new Color(image.getRGB(x, y), true);
            if (color.getAlpha() == 255) {
                opaque++;
                opaqueRgb.add(color.getRGB() & 0xffffff);
            } else if (color.getAlpha() > 0) translucent++;
        }
        return new Painted(opaqueRgb, translucent, opaque);
    }

    private static BufferedImage paintImage(FlatSVGIcon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { icon.paintIcon(null, graphics, 0, 0); } finally { graphics.dispose(); }
        return image;
    }

    private static int alphaAtLogical(BufferedImage image, int x, int y) {
        int scaledX = Math.min(image.getWidth() - 1, Math.round(x * image.getWidth() / 28f));
        int scaledY = Math.min(image.getHeight() - 1, Math.round(y * image.getHeight() / 28f));
        return image.getRGB(scaledX, scaledY) >>> 24;
    }

    private record IconCase(String name, int darkRgb, int lightRgb) {}
    private record FaceCase(String name, int x, int y) {}
    private record Painted(Set<Integer> opaqueRgb, int translucentPixels, int opaquePixels) {}
}
