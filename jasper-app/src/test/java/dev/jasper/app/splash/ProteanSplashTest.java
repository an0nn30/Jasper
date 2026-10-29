package dev.jasper.app.splash;

import dev.jasper.app.startup.StartupSteps;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProteanSplashTest {
    private static final String BUILD = new dev.jasper.app.platform.BuildInfo(false, java.util.Optional.of("1.2.3"),
        42, "abc123456789abcdef", "codex/protean", false, java.util.Optional.empty()).buildLine();
    static final List<String> LABELS = StartupSteps.queue("Protean", List.of("File Server", "Remote", "日本語 🚀"));
    @Test void geometryAndSettledRetargetingStayConsistentAtDeviceScales() {
        for (double sx : new double[] {1, 1.25, 1.5, 1.75, 2, 4}) {
            for (double sy : new double[] {sx, 1.5}) {
                var p = SplashPainter.protean(BUILD, LABELS, 1);
                assertThat(p.width()).isEqualTo(840);
                assertThat(p.height()).isEqualTo(480);
                var buffer = new SplashBuffer(p);
                var image = new BufferedImage((int) Math.ceil(840 * sx), (int) Math.ceil(480 * sy), BufferedImage.TYPE_INT_RGB);
                var first = new StartupSteps.Step(LABELS.getFirst(), 1, LABELS.size());
                var last = new StartupSteps.Step(LABELS.getLast(), LABELS.size(), LABELS.size());
                var g = image.createGraphics();
                g.scale(sx, sy);
                buffer.paint(g, first);
                p.showStep(last, 1_000_000_000L, true);
                p.animationTime(1_240_000_000L);
                buffer.show(g, last);
                g.dispose();
                var fresh = SplashPainter.protean(BUILD, LABELS, 1);
                fresh.showStep(last, 0, false);
                var expected = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                var eg = expected.createGraphics();
                eg.scale(sx, sy);
                fresh.paint(eg, last.text(), last.fraction());
                eg.dispose();
                assertThat(java.util.Arrays.equals(rgb(image), rgb(expected))).as("full pixel parity at %s x %s", sx, sy).isTrue();
                assertThat(image.getRGB((int) (30 * sx), (int) (444 * sy)) & 0xffffff).isEqualTo(0xf34532);
            }
        }
    }
    @Test void onlyFourRowsAppearAndLongTextStaysInsideItsColumn() {
        var labels = List.of("A".repeat(200), "日本語 🚀".repeat(100), "Remote", "Workspace", "hidden fifth");
        var alternate = List.of(labels.get(0), labels.get(1), labels.get(2), labels.get(3), "different hidden fifth");
        var first = render(SplashPainter.protean("Build " + "long ".repeat(200), labels, 1), .2);
        var second = render(SplashPainter.protean("Build " + "long ".repeat(200), alternate, 1), .2);
        assertThat(java.util.Arrays.equals(rgb(first), rgb(second))).as("fifth row is outside the viewport").isTrue();
        for (int y = 270; y < 390; y++) {
            for (int x = 801; x < 840; x++) {
                assertThat(first.getRGB(x, y) & 0xffffff).as("text ends before right margin").isEqualTo(0x212a33);
            }
        }
    }

    @Test void completedInkMovesUpAndFadesWhileNextRowRises() {
        var labels = List.of("M".repeat(48), "Next", "Upcoming", "Workspace");
        var painter = SplashPainter.protean("build", labels, 1);
        painter.showStep(new StartupSteps.Step("Next", 2, 4), 1_000_000_000L, true);
        var start = render(painter, .5);
        painter.animationTime(1_120_000_000L);
        var middle = render(painter, .5);
        painter.animationTime(1_240_000_000L);
        var end = render(painter, .5);
        assertThat(ink(start, 500, 277, 740, 295)).isGreaterThan(100);
        assertThat(ink(middle, 500, 277, 740, 295)).isLessThan(ink(start, 500, 277, 740, 295) / 4);
        assertThat(ink(end, 500, 277, 740, 295)).isZero();
        assertThat(ink(start, 390, 300, 430, 316)).as("incoming starts one row below").isGreaterThan(10);
        assertThat(ink(end, 390, 277, 430, 296)).as("incoming reaches active baseline").isGreaterThan(10);
        assertThat(java.util.Arrays.equals(rgb(start), rgb(middle))).isFalse();
        var settled = SplashPainter.protean("build", labels, 1);
        settled.showStep(new StartupSteps.Step("Next", 2, 4), 0, false);
        assertThat(java.util.Arrays.equals(rgb(end), rgb(render(settled, .5)))).isTrue();
    }

    private static int ink(BufferedImage image, int left, int top, int right, int bottom) {
        int ink = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            int color = image.getRGB(x, y);
            if (((color >> 16) & 255) > 140 && ((color >> 8) & 255) > 140) ink++;
        }
        return ink;
    }

    private static BufferedImage render(SplashPainter painter, double fraction) {
        var image = new BufferedImage(painter.width(), painter.height(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { painter.paint(g, "", fraction); }
        finally { g.dispose(); }
        return image;
    }

    @Test void animationFramesStopAfterOneSettledPaintAndIdleStepsNeedNoPaint() {
        var painter = SplashPainter.protean(BUILD, LABELS, 1);
        assertThat(painter.animationTime(1_000_000_000L)).as("initial idle frame").isFalse();
        var step = new StartupSteps.Step(LABELS.get(1), 2, LABELS.size());
        painter.showStep(step, 1_000_000_000L, true);
        assertThat(painter.animationTime(1_120_000_000L)).isTrue();
        assertThat(painter.animationTime(1_240_000_000L)).as("last settled frame must reach the screen").isTrue();
        assertThat(painter.animationTime(1_256_000_000L)).as("settled queue does not repaint").isFalse();
        painter.showStep(new StartupSteps.Step(LABELS.get(2), 3, LABELS.size()), 2_000_000_000L, false);
        assertThat(painter.animationTime(2_016_000_000L)).as("disabled motion paints only on notification").isFalse();
    }

    @Test void exportReviewSheets() throws Exception {
        var directory = java.nio.file.Path.of("build/protean-splash-evidence");
        java.nio.file.Files.createDirectories(directory);
        for (int scale : new int[] {1, 2, 4}) {
            var sheet = new BufferedImage(840 * scale, 480 * scale * 3, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < 3; row++) {
                var p = SplashPainter.protean(BUILD, LABELS, scale);
                int index = row == 0 ? 1 : row == 1 ? 3 : LABELS.size();
                var step = new StartupSteps.Step(LABELS.get(index - 1), index, LABELS.size());
                p.showStep(step, 0, false);
                var g = sheet.createGraphics();
                g.translate(0, 480 * scale * row);
                g.scale(scale, scale);
                p.paint(g, step.text(), step.fraction());
                g.dispose();
            }
            javax.imageio.ImageIO.write(sheet, "png", directory.resolve("static-" + scale + "x.png").toFile());
            var motion = new BufferedImage(840 * scale, 480 * scale * 3, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < 3; row++) {
                var p = SplashPainter.protean(BUILD, LABELS, scale);
                var step = new StartupSteps.Step(LABELS.get(1), 2, LABELS.size());
                p.showStep(step, 1_000_000_000L, true);
                p.animationTime(1_000_000_000L + 120_000_000L * row);
                var g = motion.createGraphics();
                g.translate(0, 480 * scale * row);
                g.scale(scale, scale);
                p.paint(g, step.text(), step.fraction());
                g.dispose();
            }
            javax.imageio.ImageIO.write(motion, "png", directory.resolve("motion-" + scale + "x.png").toFile());
        }
        var painter = SplashPainter.protean(BUILD, LABELS, 1);
        javax.imageio.ImageIO.write(render(painter, 1.0 / LABELS.size()), "png", directory.resolve("preview.png").toFile());
        var step = new StartupSteps.Step(LABELS.get(1), 2, LABELS.size());
        painter.showStep(step, 1_000_000_000L, true);
        for (int frame = 0; frame <= 15; frame++) {
            painter.animationTime(1_000_000_000L + frame * 16_000_000L);
            javax.imageio.ImageIO.write(render(painter, step.fraction()), "png",
                directory.resolve(String.format("frame-%02d.png", frame)).toFile());
        }
    }
    private static int[] rgb(BufferedImage image) { return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); }
}
