package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.startup.StartupSteps;
import dev.jasper.app.testsupport.BrandingFixtures;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.junit.jupiter.api.Test;

/** Spec 10.11: the splash's fill, its step text, its scale, its theme independence and its files. */
class SplashPainterTest {
    static SplashPainter.Content content(byte[] icon64, byte[] art, Optional<byte[]> nameFace) {
        return new SplashPainter.Content("JasperOps", "1.2.0 · Build 8ba67ed3", "Copyright 2026 Dustin McKenna-Watts", icon64,
            BrandingFixtures.png(128, 128), art, nameFace, Optional.empty(), Optional.empty());
    }

    static SplashPainter.Content content() { return content(BrandingFixtures.png(64, 64), BrandingFixtures.svg(), Optional.empty()); }

    static BufferedImage render(SplashPainter painter, String step, double fraction, int scale) {
        var image = new BufferedImage(SplashPainter.WIDTH * scale, SplashPainter.HEIGHT * scale, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.scale(scale, scale); painter.paint(g, step, fraction); }
        finally { g.dispose(); }
        return image;
    }

    static int[] pixels(BufferedImage image) { return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); }

    @Test void theFillIsTheFractionOfTheBarWithoutEasing() {
        assertThat(SplashPainter.fillWidth(0.06)).isEqualTo(43);
        assertThat(SplashPainter.fillWidth(0.36)).isEqualTo(258);
        assertThat(SplashPainter.fillWidth(0.76)).isEqualTo(546);
        assertThat(SplashPainter.fillWidth(0.88)).isEqualTo(632);
        assertThat(SplashPainter.fillWidth(1.0)).isEqualTo(718);
        assertThat(SplashPainter.fillWidth(2.0)).isEqualTo(718);
        assertThat(SplashPainter.fillWidth(0)).isZero();
        assertThat(SplashPainter.fillWidth(-1)).isZero();
        assertThat(SplashPainter.fillWidth(Double.NaN)).isZero();
        var image = render(SplashPainter.of(content(), 1), StartupSteps.RUNTIME, 0.5, 1);
        int track = SplashColors.TRACK.getRGB() & 0xffffff;
        assertThat(image.getRGB(359, 435) & 0xffffff).as("the fill's last column").isNotEqualTo(track);
        assertThat(image.getRGB(360, 435) & 0xffffff).as("the track after it").isEqualTo(track);
    }

    /** Review Focus 4: a step too long for its room ends with an ellipsis and keeps the 24 px gap to the copyright line. */
    @Test void aLongStepIsEllipsisedBeforeTheCopyrightWithItsGap() {
        var painter = SplashPainter.of(content(), 1);
        String step = StartupSteps.PLUGIN + "A plugin whose name goes on " + "and on ".repeat(40);
        var empty = render(painter, "", 0.4, 1);
        var full = render(painter, step, 0.4, 1);
        int copyrightLeft = 719;
        for (int y = 390; y < 412; y++) for (int x = 35; x < 719; x++) {
            int rgb = empty.getRGB(x, y);
            if ((((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3 > 90) copyrightLeft = Math.min(copyrightLeft, x);
        }
        assertThat(copyrightLeft).as("the copyright line's ink").isBetween(400, 700);
        // Two pixels of slack for side bearings: the gap is measured between advances, this between inks.
        for (int y = 390; y < 412; y++) for (int x = copyrightLeft - SplashPainter.GAP + 3; x < 719; x++)
            assertThat(full.getRGB(x, y)).as("nothing of the step at %s,%s", x, y).isEqualTo(empty.getRGB(x, y));
        var font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        var frc = new FontRenderContext(null, true, true);
        String fitted = SplashPainter.fit(font, frc, step, 200f);
        assertThat(fitted).endsWith("…").startsWith(StartupSteps.PLUGIN);
        assertThat(SplashPainter.advance(font, frc, fitted)).isLessThanOrEqualTo(200f);
        assertThat(SplashPainter.fit(font, frc, StartupSteps.CONFIGURATION, 400f)).isEqualTo(StartupSteps.CONFIGURATION);
    }

    @Test void theArtFollowsTheDeviceScale() {
        var painter = SplashPainter.of(content(), 2);
        var one = render(painter, StartupSteps.RUNTIME, 0.1, 1);
        var two = render(painter, StartupSteps.RUNTIME, 0.1, 2);
        assertThat(two.getWidth()).isEqualTo(1440);
        assertThat(two.getRGB(0, 0)).isEqualTo(SplashColors.BORDER.getRGB());
        assertThat(two.getRGB(1, 1)).as("a 1 px border is 2 device pixels").isEqualTo(SplashColors.BORDER.getRGB());
        assertThat(two.getRGB(2, 300)).isNotEqualTo(SplashColors.BORDER.getRGB());
        // The fixture art is one flat colour, so its 2x raster is that colour wherever the 1x raster is.
        assertThat(two.getRGB(720, 200)).isEqualTo(one.getRGB(360, 100));
    }

    /** Spec 10.11: the same in Light and Dark; the splash reads no theme and no UIManager key. */
    @Test void theSplashIgnoresTheTheme() {
        var painter = SplashPainter.of(content(), 1);
        int[] plain = pixels(render(painter, StartupSteps.WORKSPACE, 0.9, 1));
        Object foreground = UIManager.get("Label.foreground"), font = UIManager.get("defaultFont");
        try {
            UIManager.put("Label.foreground", Color.MAGENTA);
            UIManager.put("defaultFont", new FontUIResource(Font.SERIF, Font.ITALIC, 30));
            assertThat(pixels(render(painter, StartupSteps.WORKSPACE, 0.9, 1))).isEqualTo(plain);
        } finally {
            UIManager.put("Label.foreground", foreground);
            UIManager.put("defaultFont", font);
        }
    }

    /** Review Focus 2: a file that cannot be drawn refuses the splash at once; a face that does not decode falls back. */
    @Test void undrawableFilesAreRefusedAndAnUndecodableFaceFallsBack() {
        byte[] notPng = "not a png".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SplashPainter.of(content(notPng, BrandingFixtures.svg(), Optional.empty()), 1))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("64 px icon");
        assertThatThrownBy(() -> SplashPainter.of(content(BrandingFixtures.png(64, 64), "<html/>".getBytes(StandardCharsets.UTF_8),
            Optional.empty()), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> SplashPainter.of(content(BrandingFixtures.png(64, 64), BrandingFixtures.svg(), Optional.of(new byte[] {1, 2, 3})), 1))
            .doesNotThrowAnyException();
    }

    /** Execution notes (2026-10-17) ruling 8: a text the kit's face cannot draw takes the platform face at its size. */
    @Test void aTextTheFaceCannotDrawTakesThePlatformFace() throws Exception {
        byte[] plex = java.nio.file.Files.readAllBytes(dev.jasper.app.testsupport.EnterpriseResources.RESOURCES
            .resolve("presentation/fonts/ibm-plex-sans-regular.ttf"));
        Font face = Font.createFont(Font.TRUETYPE_FONT, new java.io.ByteArrayInputStream(plex)).deriveFont(12f);
        assertThat(SplashPainter.displayable(face, StartupSteps.PLUGIN + "Tunnels…", Font.SANS_SERIF, Font.PLAIN)).isSameAs(face);
        Font fallback = SplashPainter.displayable(face, StartupSteps.PLUGIN + "\u65E5\u672C", Font.SANS_SERIF, Font.PLAIN);
        assertThat(fallback.getFamily()).isNotEqualTo(face.getFamily());
        assertThat(fallback.getSize2D()).isEqualTo(12f);
    }

    @Test void theSplashIsCentredOnTheMainScreenAndOneThatCannotOpenStaysClosed() {
        assertThat(Splash.centred(new Rectangle(0, 0, 1440, 900))).isEqualTo(new Point(360, 230));
        assertThat(Splash.centred(new Rectangle(1440, -200, 1920, 1080))).isEqualTo(new Point(2040, 120));
        assertThatThrownBy(() -> new Splash(SplashPainter.of(content(), 1), null)).isInstanceOf(NullPointerException.class);
        var splash = new Splash(SplashPainter.of(content(), 1), java.util.List.of(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)));
        // Headless: the window cannot be made; that is logged and the splash stays closed.
        assertThatCode(() -> {
            splash.open(new StartupSteps.Step(StartupSteps.RUNTIME, 1, 4));
            splash.show(new StartupSteps.Step(StartupSteps.CONFIGURATION, 2, 4));
            splash.close();
            splash.close();
        }).doesNotThrowAnyException();
    }
}
