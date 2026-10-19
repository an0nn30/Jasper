package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.startup.StartupSteps;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Offscreen frames retain device-resolution text, art and borders at fractional desktop scales. */
class SplashFrameTest {
    @ParameterizedTest
    @CsvSource({"1,1", "1.25,1.25", "1.5,1.5", "1.75,1.75", "2,2", "1.25,1.5", "1.333,1.333"})
    void presentingFramesPreservesScaleOriginClipAndFreshProgress(double scaleX, double scaleY) {
        var painter = SplashPainter.of(SplashPainterTest.content(), 1);
        var splash = new Splash(painter, List.of());
        var actual = new BufferedImage(1500, 950, BufferedImage.TYPE_INT_RGB);
        var expected = new BufferedImage(1500, 950, BufferedImage.TYPE_INT_RGB);
        var target = actual.createGraphics();
        var reference = expected.createGraphics();
        try {
            for (var g : List.of(target, reference)) {
                g.translate(7, 9);
                g.clipRect(4, 4, 1400, 850);
                g.scale(scaleX, scaleY);
            }
            var transform = target.getTransform();
            var clip = target.getClip().getBounds();
            for (var step : new StartupSteps.Step[] {
                new StartupSteps.Step(StartupSteps.RUNTIME, 1, 4),
                new StartupSteps.Step(StartupSteps.WORKSPACE, 3, 4)
            }) {
                splash.show(step);
                splash.draw(target);
                java.util.Arrays.fill(((java.awt.image.DataBufferInt) expected.getRaster().getDataBuffer()).getData(), 0);
                painter.paint(reference, step.text(), step.fraction());
                for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                    assertThat(actual.getRGB(x, y)).as("pixel %s,%s at scale %s,%s", x, y, scaleX, scaleY)
                        .isEqualTo(expected.getRGB(x, y));
                assertThat(target.getTransform()).isEqualTo(transform);
                assertThat(target.getClip().getBounds()).isEqualTo(clip);
            }
        } finally {
            target.dispose();
            reference.dispose();
        }
    }

    @ParameterizedTest
    @CsvSource({"1,1,720,440", "1.25,1.25,900,550", "1.5,1.5,1080,660",
        "1.75,1.75,1260,770", "2,2,1440,880", "1.25,1.5,900,660", "1.333,1.333,960,587"})
    void completeFramesMatchDirectDeviceResolutionRendering(double scaleX, double scaleY, int width, int height) {
        var painter = SplashPainter.of(SplashPainterTest.content(), 1);
        for (var step : new StartupSteps.Step[] {
            new StartupSteps.Step(StartupSteps.RUNTIME, 1, 4),
            new StartupSteps.Step(StartupSteps.WORKSPACE, 3, 4)
        }) {
            var frame = painter.frame(step, scaleX, scaleY);
            assertThat(frame.getWidth()).isEqualTo(width);
            assertThat(frame.getHeight()).isEqualTo(height);
            var expected = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var g = expected.createGraphics();
            try {
                g.scale(scaleX, scaleY);
                painter.paint(g, step.text(), step.fraction());
            } finally { g.dispose(); }
            assertThat(SplashPainterTest.pixels(frame)).isEqualTo(SplashPainterTest.pixels(expected));
        }
    }
}
