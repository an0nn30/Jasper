package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.jasper.app.startup.StartupSteps;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThemeSplashTest {
    static final List<String> IDS = List.of("jasper-meadow", "slate-cobalt", "ubuntu-yaru", "catppuccin-latte",
        "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha");

    @Test void sevenBundledIllustrationsAreSharpDistinctStillImages() throws Exception {
        var signatures = new HashSet<Integer>();
        for (String id : IDS) {
            try (var input = ThemeSplash.class.getResourceAsStream("themes/" + id + ".png")) {
                assertThat(input).as(id).isNotNull();
                var art = ImageIO.read(input);
                assertThat(art.getWidth()).as(id + " width").isGreaterThanOrEqualTo(1440);
                assertThat(art.getHeight()).as(id + " height").isGreaterThanOrEqualTo(880);
                assertThat(art.getWidth() / (double) art.getHeight()).isCloseTo(720d / 440, org.assertj.core.data.Offset.offset(.002));
            }
            var painter = SplashPainter.themed(id, "1.2.3", 4);
            assertThat(painter.animated()).isFalse();
            var rendered = render(painter, "Loading plugin: Credential Vault", .5, 1);
            signatures.add(java.util.Arrays.hashCode(SplashPainterTest.pixels(rendered)));
            var path = Path.of("build/reports/splash/themes/" + id + ".png");
            Files.createDirectories(path.getParent());
            ImageIO.write(rendered, "png", path.toFile());
            for (int factor : new int[]{2, 4})
                ImageIO.write(render(painter, "Loading plugin: Credential Vault", .5, factor), "png",
                    path.resolveSibling(id + "-" + factor + "x.png").toFile());
        }
        assertThat(signatures).hasSize(7);
    }

    @Test void customDesktopAndUnsafeIdsUseMeadowWithoutBecomingResourcePaths() {
        for (String id : List.of("custom-theme", "desktop-colors", "../meadow", "", "Catppuccin-Mocha")) {
            var fallback = SplashPainter.themed(id, "1.2.3", 1);
            assertThat(fallback.animated()).as(id).isFalse();
            assertThat(java.util.Arrays.equals(SplashPainterTest.pixels(render(fallback, "Ready", .2, 1)),
                SplashPainterTest.pixels(render(SplashPainter.themed("jasper-meadow", "1.2.3", 1), "Ready", .2, 1))))
                .as(id).isTrue();
        }
    }

    @Test void letteringIsReadableAgainstEachActualIllustrationsSkyAndFooter() throws Exception {
        for (String id : IDS) {
            try (var input = ThemeSplash.class.getResourceAsStream("themes/" + id + ".png")) {
                var art = ImageIO.read(input);
                var sky = new java.awt.Color(art.getRGB(art.getWidth() * 3 / 5, art.getHeight() / 4));
                var colors = SplashColors.palette(id);
                assertThat(contrast(colors.ink(), sky)).as(id + " title on sky").isGreaterThan(4.5);
                assertThat(contrast(colors.muted(), sky)).as(id + " tagline on sky").isGreaterThan(4.5);
                assertThat(contrast(colors.ink(), colors.panel())).as(id + " status on footer").isGreaterThan(4.5);
            }
        }
    }

    private static double contrast(java.awt.Color first, java.awt.Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(java.awt.Color color) {
        double[] rgb = {color.getRed() / 255d, color.getGreen() / 255d, color.getBlue() / 255d};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }

    @Test void themedPaintingLeavesTheCallersGraphicsStateAlone() {
        var painter = SplashPainter.themed("catppuccin-mocha", "build", 1);
        var image = new BufferedImage(900, 550, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.scale(1.25, 1.25); g.clipRect(2, 3, 700, 420);
            g.setColor(java.awt.Color.RED);
            var transform = g.getTransform(); var clip = g.getClip(); var font = g.getFont(); var hints = g.getRenderingHints();
            painter.paint(g, "Ready", .5);
            assertThat(g.getTransform()).isEqualTo(transform);
            assertThat(g.getClip().getBounds()).isEqualTo(clip.getBounds());
            assertThat(g.getFont()).isEqualTo(font);
            assertThat(g.getColor()).isEqualTo(java.awt.Color.RED);
            assertThat(g.getRenderingHints()).isEqualTo(hints);
        } finally { g.dispose(); }
    }

    @Test void invalidArtworkIsRejectedForTheOriginalMeadowFallback() {
        assertThatIllegalArgumentException().isThrownBy(() -> ThemeSplash.decode(null));
        assertThatIllegalArgumentException().isThrownBy(() -> ThemeSplash.decode(new java.io.ByteArrayInputStream(new byte[]{1, 2})));
    }

    @ParameterizedTest @ValueSource(doubles = {1, 1.25, 2, 4})
    void stepUpdatesAndBackwardProgressExactlyMatchFreshFrames(double scale) {
        for (String id : IDS) {
            var painter = SplashPainter.themed(id, "build 1.2.3", (int) Math.ceil(scale));
            var actual = render(painter, "", 0, scale);
            var buffer = new SplashBuffer(painter);
            var g = actual.createGraphics();
            try {
                g.scale(scale, scale);
                for (var step : List.of(new StartupSteps.Step("Loading plugin: " + "Long plugin name ".repeat(15), 8, 8),
                        new StartupSteps.Step("Ready", 1, 8), new StartupSteps.Step("Ready", 8, 8))) {
                    buffer.show(g, step);
                    assertThat(java.util.Arrays.equals(SplashPainterTest.pixels(actual),
                        SplashPainterTest.pixels(render(painter, step.text(), step.fraction(), scale))))
                        .as(id + " at " + scale + " / " + step).isTrue();
                }
            } finally { g.dispose(); }
        }
    }

    @Test void progressIsClampedAndVersionAndStatusRemainRuntimeText() {
        var painter = SplashPainter.themed("slate-cobalt", "build one", 1);
        for (double fraction : new double[]{-1, Double.NaN})
            assertThat(java.util.Arrays.equals(SplashPainterTest.pixels(render(painter, "Ready", fraction, 1)),
                SplashPainterTest.pixels(render(painter, "Ready", 0, 1)))).as("fraction %s", fraction).isTrue();
        assertThat(java.util.Arrays.equals(SplashPainterTest.pixels(render(painter, "Ready", 9, 1)),
            SplashPainterTest.pixels(render(painter, "Ready", 1, 1)))).as("fraction 9").isTrue();
        assertThat(SplashPainterTest.pixels(render(painter, "Ready", 0, 1)))
            .isNotEqualTo(SplashPainterTest.pixels(render(SplashPainter.themed("slate-cobalt", "build two", 1), "Ready", 0, 1)));
        assertThat(SplashPainterTest.pixels(render(painter, "Ready", 0, 1)))
            .isNotEqualTo(SplashPainterTest.pixels(render(painter, "Loading", 0, 1)));
    }

    private static BufferedImage render(SplashPainter painter, String step, double fraction, double scale) {
        var image = new BufferedImage((int) Math.ceil(720 * scale), (int) Math.ceil(440 * scale), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(painter.background()); g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.scale(scale, scale); painter.paint(g, step, fraction);
        } finally { g.dispose(); }
        return image;
    }
}
