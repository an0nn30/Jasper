package dev.jasper.app.splash;

import dev.jasper.app.presentation.KitParser;
import dev.jasper.app.testsupport.EnterpriseResources;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Spec 10.11 and 16.1.4: the splash under the original reference branding, at 1x, against the ten Boot Splash frames
 * (1x, 720 by 440). The frames are palette-quantised screenshots of the animated mock (the plan's Measured targets), so
 * the render's flat colours are exact and the mock's within 3, regions compare by mean difference, text by its ink box,
 * and the fill by frame 10's rows. The version, the copyright, the steps and their fill are the mock's fixtures. The
 * frames were drawn on macOS and Linux and Windows rasterize IBM Plex differently, so the text is compared on macOS only
 * (plan Rulings 18); every other probe runs on every OS. Renders and difference images are written to
 * jasper-app/build/enterprise-splash/ for inspection.
 */
class BootSplashMockTest {
    static final Path FRAMES_DIRECTORY = EnterpriseResources.ROOT.resolve("docs/design/enterprise/reference/10 Boot Splash");
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-splash");
    static final String VERSION = "1.0.0 · Build a3f9c21", COPYRIGHT = "CopyRight Notice Here";

    /** An inclusive ink box. */
    record Box(int left, int top, int right, int bottom) { }

    /** One frame: its id, its file, its step, the fill it caught, and its step's measured ink box. */
    record Frame(String id, String file, String step, double fraction, Box stepInk) { }

    static final List<Frame> FRAMES = List.of(
        new Frame("01", "Boot Splash 01 - Starting Java runtime.png", "Starting Java runtime…", 0.06, new Box(42, 395, 164, 407)),
        new Frame("02", "Boot Splash 02 - Reading configuration.png", "Reading configuration…", 96 / 718.0, new Box(42, 395, 166, 407)),
        new Frame("03", "Boot Splash 03 - Unlocking credential vault.png", "Unlocking credential vault…", 135 / 718.0, new Box(42, 395, 189, 407)),
        new Frame("04", "Boot Splash 04 - Loading plugin File Server.png", "Loading plugin: File Server", 0.36, new Box(42, 395, 182, 407)),
        new Frame("05", "Boot Splash 05 - Loading plugin Tunnels.png", "Loading plugin: Tunnels", 0.46, new Box(42, 395, 166, 407)),
        new Frame("06", "Boot Splash 06 - Loading plugin Credential Vault.png", "Loading plugin: Credential Vault", 0.56, new Box(42, 395, 210, 407)),
        new Frame("07", "Boot Splash 07 - Loading plugin Run Dialog.png", "Loading plugin: Run Dialog", 407 / 718.0, new Box(42, 395, 182, 407)),
        new Frame("08", "Boot Splash 08 - Indexing hosts.png", "Indexing hosts…", 0.76, new Box(42, 395, 128, 407)),
        new Frame("09", "Boot Splash 09 - Restoring workspace.png", "Restoring workspace…", 0.88, new Box(42, 396, 161, 407)),
        new Frame("10", "Boot Splash 10 - Starting Workstation.png", "Starting JasperOps Workstation…", 1.0, new Box(42, 396, 220, 407)));

    static final Rectangle ART = new Rectangle(1, 1, 718, 250), ICON = new Rectangle(41, 279, 64, 64);
    /** Where only the gradient shows. */
    static final List<Rectangle> GROUND = List.of(new Rectangle(1, 252, 718, 24), new Rectangle(1, 276, 39, 70),
        new Rectangle(330, 276, 389, 70), new Rectangle(1, 346, 718, 42), new Rectangle(1, 412, 718, 20));
    static final Rectangle NAME = new Rectangle(110, 280, 490, 42), VERSION_LINE = new Rectangle(110, 319, 490, 26),
        STEP = new Rectangle(30, 385, 490, 35), COPYRIGHT_LINE = new Rectangle(520, 385, 180, 35);
    static final Box NAME_INK = new Box(122, 291, 265, 317), VERSION_INK = new Box(122, 323, 271, 332),
        COPYRIGHT_INK = new Box(568, 398, 678, 407);

    static SplashPainter.Content content(KitParser.Parsed parsed) throws java.io.IOException {
        var branding = parsed.branding();
        var ui = parsed.faces().ui().orElseThrow();
        var data = parsed.faces().data().orElseThrow();
        // These historical mocks contain the original emblem. Shared Shell Prompt selection is covered by
        // ApplicationBootstrapTest; keep this renderer comparison on the exact reference assets.
        byte[] icon64 = Files.readAllBytes(EnterpriseResources.RESOURCES.resolve("presentation/icons/icon-64.png"));
        byte[] icon128 = Files.readAllBytes(EnterpriseResources.RESOURCES.resolve("presentation/icons/icon-128.png"));
        return new SplashPainter.Content(branding.product(), VERSION, COPYRIGHT, icon64, icon128, branding.splashArt(),
            Optional.of(ui.semibold().bytes()), Optional.of(ui.regular().bytes()), Optional.of(data.regular().bytes()));
    }

    static BufferedImage render(SplashPainter painter, String step, double fraction) {
        var image = new BufferedImage(SplashPainter.WIDTH, SplashPainter.HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { painter.paint(g, step, fraction); }
        finally { g.dispose(); }
        return image;
    }

    static BufferedImage mock(String file) throws Exception {
        BufferedImage source = ImageIO.read(FRAMES_DIRECTORY.resolve(file).toFile());
        var image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.drawImage(source, 0, 0, null); }
        finally { g.dispose(); }
        return image;
    }

    static int channel(int rgb, int c) { return (rgb >> (16 - 8 * c)) & 255; }

    /** The largest of the three channels' mean absolute differences over {@code region}. */
    static double mean(BufferedImage a, BufferedImage b, Rectangle region) {
        long[] sums = new long[3];
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++)
                for (int c = 0; c < 3; c++) sums[c] += Math.abs(channel(a.getRGB(x, y), c) - channel(b.getRGB(x, y), c));
        long area = (long) region.width * region.height;
        return Math.max(sums[0], Math.max(sums[1], sums[2])) / (double) area;
    }

    /** The largest absolute channel difference over {@code region}. */
    static int largest(BufferedImage a, BufferedImage b, Rectangle region) {
        int largest = 0;
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++)
                for (int c = 0; c < 3; c++) largest = Math.max(largest, Math.abs(channel(a.getRGB(x, y), c) - channel(b.getRGB(x, y), c)));
        return largest;
    }

    static int distance(int rgb, Color color) {
        int largest = 0;
        for (int c = 0; c < 3; c++) largest = Math.max(largest, Math.abs(channel(rgb, c) - channel(color.getRGB(), c)));
        return largest;
    }

    /** The inclusive box of the pixels in {@code region} whose mean of R, G and B is above {@code threshold}. */
    static Box ink(BufferedImage image, Rectangle region, int threshold) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++) {
                int rgb = image.getRGB(x, y);
                if ((channel(rgb, 0) + channel(rgb, 1) + channel(rgb, 2)) / 3.0 > threshold) {
                    left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
                }
            }
        return right < 0 ? new Box(-1, -1, -1, -1) : new Box(left, top, right, bottom);
    }

    /** The last column of the fill on row 435 (the track's blue is 60, the fill's above 120); 0 for none. */
    static int fillEnd(BufferedImage image) {
        int end = 0;
        for (int x = 1; x < 719; x++) if (channel(image.getRGB(x, 435), 2) > 120) end = x;
        return end;
    }

    static BufferedImage difference(BufferedImage a, BufferedImage b) {
        var image = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) {
                int rgb = 0;
                for (int c = 0; c < 3; c++) rgb = rgb << 8 | Math.min(255, 4 * Math.abs(channel(a.getRGB(x, y), c) - channel(b.getRGB(x, y), c)));
                image.setRGB(x, y, rgb);
            }
        return image;
    }

    static void ink(SoftAssertions soft, String what, Box render, Box mock, Box measured, int left, int top, int right, int bottom) {
        soft.assertThat(mock).as(what + ": the mock's ink is the measured one").isEqualTo(measured);
        soft.assertThat(render.left()).as(what + " left").isBetween(measured.left() - left, measured.left() + left);
        soft.assertThat(render.top()).as(what + " top").isBetween(measured.top() - top, measured.top() + top);
        soft.assertThat(render.right()).as(what + " right").isBetween(measured.right() - right, measured.right() + right);
        soft.assertThat(render.bottom()).as(what + " bottom").isBetween(measured.bottom() - bottom, measured.bottom() + bottom);
    }

    static SplashPainter painter() throws Exception {
        return SplashPainter.of(content(EnterpriseResources.facts().parsed().orElseThrow()), 1);
    }

    /** The border, rule, gradient, art, icon and bar of every frame: no text, so the same on every OS. */
    @Test void everyFrameMatchesItsMock() throws Exception {
        var painter = painter();
        Files.createDirectories(OUT);
        BufferedImage settled = mock(FRAMES.getLast().file());
        var soft = new SoftAssertions();
        for (Frame frame : FRAMES) {
            BufferedImage mock = mock(frame.file());
            BufferedImage render = render(painter, frame.step(), frame.fraction());
            ImageIO.write(render, "png", OUT.resolve(frame.id() + ".png").toFile());
            ImageIO.write(difference(render, mock), "png", OUT.resolve(frame.id() + "-diff.png").toFile());
            String at = "frame " + frame.id() + ": ";
            int border = 0, mockBorder = 0, rule = 0;
            for (int x = 0; x < 720; x++) for (int y : new int[] {0, 439}) {
                if (distance(render.getRGB(x, y), SplashColors.BORDER) != 0) border++;
                if (distance(mock.getRGB(x, y), SplashColors.BORDER) != 0) mockBorder++;
            }
            for (int y = 0; y < 440; y++) for (int x : new int[] {0, 719}) {
                if (distance(render.getRGB(x, y), SplashColors.BORDER) != 0) border++;
                if (distance(mock.getRGB(x, y), SplashColors.BORDER) != 0) mockBorder++;
            }
            for (int x = 1; x < 719; x++) if (distance(render.getRGB(x, 251), SplashColors.BORDER) != 0) rule++;
            soft.assertThat(border).as(at + "border pixels that are not the border colour").isZero();
            soft.assertThat(mockBorder).as(at + "the mock's border pixels that are not the border colour").isZero();
            soft.assertThat(rule).as(at + "rule pixels under the art that are not the border colour").isZero();
            soft.assertThat(mean(render, mock, ART)).as(at + "art").isLessThanOrEqualTo(6.0);
            for (Rectangle ground : GROUND) {
                soft.assertThat(mean(render, mock, ground)).as(at + "gradient " + ground).isLessThanOrEqualTo(2.0);
                soft.assertThat(largest(render, mock, ground)).as(at + "gradient " + ground + " at worst").isLessThanOrEqualTo(6);
            }
            soft.assertThat(mean(render, mock, ICON)).as(at + "icon").isLessThanOrEqualTo(4.0);
            soft.assertThat(distance(render.getRGB(360, 432), SplashColors.TRACK_RULE)).as(at + "the bar's rule").isZero();
            soft.assertThat(distance(mock.getRGB(360, 432), SplashColors.TRACK_RULE)).as(at + "the mock's bar rule").isLessThanOrEqualTo(3);
            int mockEnd = fillEnd(mock);
            soft.assertThat(fillEnd(render)).as(at + "the fill's end").isBetween(mockEnd - 1, mockEnd + 1);
            for (int y = 433; y < 439; y++)
                soft.assertThat(distance(render.getRGB(20, y), new Color(settled.getRGB(20, y)))).as(at + "fill row " + y).isLessThanOrEqualTo(10);
            int track = mockEnd + 6;
            if (track < 718) for (int y = 433; y < 439; y++) {
                soft.assertThat(distance(render.getRGB(track, y), SplashColors.TRACK)).as(at + "track row " + y).isZero();
                soft.assertThat(distance(mock.getRGB(track, y), SplashColors.TRACK)).as(at + "the mock's track row " + y).isLessThanOrEqualTo(2);
            }
        }
        soft.assertAll();
    }

    /** The product name, version line, step and copyright line of every frame: macOS only (plan Rulings 18). */
    @Test @EnabledOnOs(OS.MAC) void everyFramesTextMatchesItsMock() throws Exception {
        var painter = painter();
        var soft = new SoftAssertions();
        for (Frame frame : FRAMES) {
            BufferedImage mock = mock(frame.file());
            BufferedImage render = render(painter, frame.step(), frame.fraction());
            String at = "frame " + frame.id() + ": ";
            ink(soft, at + "product name", ink(render, NAME, 140), ink(mock, NAME, 140), NAME_INK, 1, 1, 2, 1);
            ink(soft, at + "version line", ink(render, VERSION_LINE, 100), ink(mock, VERSION_LINE, 100), VERSION_INK, 1, 1, 2, 1);
            ink(soft, at + "step", ink(render, STEP, 100), ink(mock, STEP, 100), frame.stepInk(), 1, 1, 2, 1);
            ink(soft, at + "copyright line", ink(render, COPYRIGHT_LINE, 90), ink(mock, COPYRIGHT_LINE, 90), COPYRIGHT_INK, 2, 1, 1, 1);
            for (Rectangle text : List.of(NAME, VERSION_LINE, STEP, COPYRIGHT_LINE))
                soft.assertThat(mean(render, mock, text)).as(at + "text " + text).isLessThanOrEqualTo(8.0);
        }
        soft.assertAll();
    }
}
