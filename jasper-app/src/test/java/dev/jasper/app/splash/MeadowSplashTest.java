package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;
import dev.jasper.app.startup.StartupSteps;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class MeadowSplashTest {
    @Test void theDefaultSplashUsesMeadowAndOnlyTheMascotChangesPose() throws Exception {
        var painter = SplashPainter.meadow("1.0.0", 2);
        var idle = SplashPainterTest.render(painter, "Loading plugin: Credential Vault", .4, 2);
        painter.animationFrame(4);
        var blink = SplashPainterTest.render(painter, "Loading plugin: Credential Vault", .4, 2);
        assertThat(idle.getRGB(600, 50) & 0xffffff).isEqualTo(0xFAF9F5);
        assertThat(idle.getRGB(30, 870) & 0xffffff).isEqualTo(0x52643F);
        int changed = 0;
        for (int y = 0; y < idle.getHeight(); y++) for (int x = 0; x < idle.getWidth(); x++) {
            if (idle.getRGB(x,y) != blink.getRGB(x,y)) {
                changed++;
                assertThat(x).isBetween(108, 443);
                assertThat(y).isBetween(180, 599);
            }
        }
        assertThat(changed).isPositive();
        var evidence = java.nio.file.Path.of("build/reports/splash/meadow.png");
        java.nio.file.Files.createDirectories(evidence.getParent());
        javax.imageio.ImageIO.write(idle, "png", evidence.toFile());
    }

    @Test void animationAndStepUpdatesMatchAFreshFrameAtFractionalScale() {
        var painter = SplashPainter.meadow("1.0.0", 2);
        var buffer = new SplashBuffer(painter);
        var first = new StartupSteps.Step(StartupSteps.RUNTIME, 1, 4);
        var next = new StartupSteps.Step(StartupSteps.WORKSPACE, 3, 4);
        var actual = new BufferedImage(900, 550, BufferedImage.TYPE_INT_RGB);
        var expected = new BufferedImage(900, 550, BufferedImage.TYPE_INT_RGB);
        var a = actual.createGraphics(); var b = expected.createGraphics();
        try {
            a.scale(1.25, 1.25); b.scale(1.25, 1.25);
            buffer.paint(a, first);
            painter.animationFrame(9);
            buffer.show(a, next);
            new SplashBuffer(painter).paint(b, next);
            assertThat(SplashPainterTest.pixels(actual)).containsExactly(SplashPainterTest.pixels(expected));
        } finally { a.dispose(); b.dispose(); }
    }
}
