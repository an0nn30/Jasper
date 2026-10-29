package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.startup.StartupSteps;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The splash's frames on a target with its own origin and clip, at whole, fractional and unequal desktop scales: a step
 * shown after another leaves exactly the frame a fresh splash paints on it, and the target's transform and clip.
 */
class SplashFrameTest {
    private static final StartupSteps.Step FIRST = new StartupSteps.Step(StartupSteps.RUNTIME, 1, 4);
    private static final StartupSteps.Step NEXT = new StartupSteps.Step(StartupSteps.WORKSPACE, 3, 4);

    @ParameterizedTest
    @CsvSource({"1,1", "1.25,1.25", "1.5,1.5", "1.75,1.75", "2,2", "1.25,1.5", "1.333,1.333"})
    void aStepKeepsTheTargetsOriginClipAndShowsFreshProgress(double scaleX, double scaleY) {
        var painter = SplashPainter.of(SplashPainterTest.content(), 1);
        var actual = new BufferedImage(1500, 950, BufferedImage.TYPE_INT_RGB);
        var expected = new BufferedImage(1500, 950, BufferedImage.TYPE_INT_RGB);
        Graphics2D target = target(actual, scaleX, scaleY), reference = target(expected, scaleX, scaleY);
        try {
            var transform = target.getTransform();
            var clip = target.getClip().getBounds();
            var buffer = new SplashBuffer(painter);
            buffer.paint(target, FIRST);
            buffer.show(target, NEXT);
            new SplashBuffer(painter).paint(reference, NEXT);
            for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                assertThat(actual.getRGB(x, y)).as("pixel %s,%s at scale %s,%s", x, y, scaleX, scaleY)
                    .isEqualTo(expected.getRGB(x, y));
            assertThat(target.getTransform()).isEqualTo(transform);
            assertThat(target.getClip().getBounds()).isEqualTo(clip);
        } finally {
            target.dispose();
            reference.dispose();
        }
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1.25,1.25", "1.5,1.5", "1.75,1.75", "2,2", "4,4", "1.25,1.5"})
    void proteanKeepsTargetOriginAndClipDuringWholeFrameUpdates(double scaleX, double scaleY) {
        var labels = StartupSteps.queue("Protean", java.util.List.of());
        var painter = SplashPainter.protean("build", labels, 1);
        var actual = new BufferedImage(3500, 2000, BufferedImage.TYPE_INT_RGB);
        var expected = new BufferedImage(3500, 2000, BufferedImage.TYPE_INT_RGB);
        var next = new StartupSteps.Step(labels.get(2), 3, 4);
        var target = target(actual, scaleX, scaleY);
        var reference = target(expected, scaleX, scaleY);
        try {
            var transform = target.getTransform();
            var clip = target.getClip().getBounds();
            var buffer = new SplashBuffer(painter);
            buffer.paint(target, FIRST);
            painter.showStep(next, 0, false);
            buffer.show(target, next);
            new SplashBuffer(painter).paint(reference, next);
            assertThat(java.util.Arrays.equals(SplashPainterTest.pixels(actual), SplashPainterTest.pixels(expected))).isTrue();
            assertThat(target.getTransform()).isEqualTo(transform);
            assertThat(target.getClip().getBounds()).isEqualTo(clip);
        } finally {
            target.dispose();
            reference.dispose();
        }
    }

    /** A target offset by (7, 9), clipped to (4, 4, 1400, 850), at the given scale. */
    private static Graphics2D target(BufferedImage image, double scaleX, double scaleY) {
        Graphics2D g = image.createGraphics();
        g.translate(7, 9);
        g.clipRect(4, 4, 1400, 850);
        g.scale(scaleX, scaleY);
        return g;
    }
}
