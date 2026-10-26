package dev.jasper.buddy.internal.presentation;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyFrameBufferTest {
    @Test void movedTranslucentArtworkReplacesOpaqueDestinationAndErasesItsPreviousPosition() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        BufferedImage target = opaqueImage(32, 24);
        paint(buffer, target, 32, 24, 1, 1, g -> {
            g.setColor(new Color(64, 128, 192, 128));
            g.fillRect(2, 3, 8, 7);
        });
        assertThat(target.getRGB(3, 4) >>> 24).isEqualTo(128);
        assertThat(target.getRGB(20, 20) >>> 24).isZero();

        Consumer<Graphics2D> moved = g -> {
            g.setColor(new Color(192, 64, 128, 128));
            g.fillRect(16, 10, 8, 7);
        };
        paint(buffer, target, 32, 24, 1, 1, moved);

        assertThat(target.getRGB(3, 4) >>> 24).isZero();
        assertThat(target.getRGB(17, 11) >>> 24).isEqualTo(128);
        assertPixels(target, reference(32, 24, 1, 1, moved));
    }

    @Test void destinationClipPreservesEveryPixelOutsideTheDamageRegion() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        BufferedImage target = opaqueImage(24, 20);
        Rectangle damage = new Rectangle(5, 6, 7, 8);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.clip(damage);
            buffer.paint(graphics, 24, 20, g -> {
                g.setColor(new Color(32, 160, 224, 128));
                g.fillRect(7, 8, 3, 4);
            });
            assertThat(graphics.getClip().getBounds()).isEqualTo(damage);
            assertThat(graphics.getComposite()).isEqualTo(AlphaComposite.SrcOver);
        } finally { graphics.dispose(); }

        for (int y = 0; y < 20; y++) for (int x = 0; x < 24; x++) {
            if (!damage.contains(x, y)) assertThat(target.getRGB(x, y)).as("outside damage (%d,%d)", x, y)
                .isEqualTo(Color.MAGENTA.getRGB());
        }
        assertThat(target.getRGB(5, 6) >>> 24).isZero();
        assertThat(target.getRGB(8, 9) >>> 24).isEqualTo(128);
    }

    @Test void resizedFramesMatchFreshDeviceResolutionRenderingAtIntegerAndFractionalScales() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        for (double scale : new double[] {1, 2, 1.25}) {
            for (int[] size : new int[][] {{17, 13}, {29, 23}, {11, 9}}) {
                BufferedImage target = opaqueImage((int) Math.ceil(size[0] * scale),
                    (int) Math.ceil(size[1] * scale));
                paint(buffer, target, size[0], size[1], scale, scale, BuddyFrameBufferTest::paintArtwork);
                assertPixels(target, reference(size[0], size[1], scale, scale, BuddyFrameBufferTest::paintArtwork));
            }
        }
    }

    @Test void changedScaleIsAppliedEvenWhenThePixelDimensionsStayTheSame() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        BufferedImage target = opaqueImage(24, 24);
        paint(buffer, target, 24, 24, 1, 1, BuddyFrameBufferTest::paintArtwork);
        paint(buffer, target, 12, 12, 2, 2, BuddyFrameBufferTest::paintArtwork);
        assertPixels(target, reference(12, 12, 2, 2, BuddyFrameBufferTest::paintArtwork));
    }

    @Test void independentDeviceScalesAndDeviceTranslationPreserveArtworkAndWindowBounds() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        BufferedImage target = opaqueImage(30, 35);
        BufferedImage expected = opaqueImage(30, 35);
        Graphics2D actualGraphics = target.createGraphics();
        Graphics2D referenceGraphics = expected.createGraphics();
        try {
            actualGraphics.translate(3, 4);
            actualGraphics.scale(1.25, 2);
            referenceGraphics.translate(3, 4);
            referenceGraphics.scale(1.25, 2);
            buffer.paint(actualGraphics, 17, 13, BuddyFrameBufferTest::paintArtwork);
            paintReference(referenceGraphics, 17, 13, BuddyFrameBufferTest::paintArtwork);
        } finally {
            actualGraphics.dispose();
            referenceGraphics.dispose();
        }
        assertPixels(target, expected);
    }

    @Test void painterCannotDrawOutsideLogicalWindowBounds() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        BufferedImage target = opaqueImage(30, 30);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.translate(5, 6);
            buffer.paint(graphics, 12, 10, g -> {
                g.setColor(Color.CYAN);
                g.fillRect(-100, -100, 200, 200);
            });
        } finally { graphics.dispose(); }
        Rectangle bounds = new Rectangle(5, 6, 12, 10);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) {
            assertThat(target.getRGB(x, y)).as("window bounds (%d,%d)", x, y)
                .isEqualTo((bounds.contains(x, y) ? Color.CYAN : Color.MAGENTA).getRGB());
        }
    }

    @Test void emptyDimensionsLeaveDestinationUntouchedAndDoNotInvokePainter() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        BufferedImage target = opaqueImage(12, 10);
        for (int[] size : new int[][] {{0, 10}, {12, 0}, {0, 0}}) {
            paint(buffer, target, size[0], size[1], 1, 1, g -> {
                throw new AssertionError("An empty frame must not paint");
            });
        }
        assertPixels(target, opaqueImage(12, 10));
    }

    @Test void disposingIsRepeatableAndTheNextPaintRecreatesACleanFrame() {
        BuddyFrameBuffer buffer = new BuddyFrameBuffer();
        BufferedImage target = opaqueImage(20, 16);
        paint(buffer, target, 20, 16, 1, 1, BuddyFrameBufferTest::paintArtwork);
        buffer.dispose();
        buffer.dispose();
        paint(buffer, target, 20, 16, 1, 1, g -> {});
        assertPixels(target, new BufferedImage(20, 16, BufferedImage.TYPE_INT_ARGB_PRE));
    }

    private static void paintArtwork(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(32, 96, 224, 128));
        graphics.fill(new RoundRectangle2D.Double(1, 2, 9, 6, 3, 3));
        graphics.setColor(new Color(192, 64, 32, 192));
        graphics.fillRect(4, 4, 1, 5);
    }

    private static void paint(BuddyFrameBuffer buffer, BufferedImage target, int width, int height,
                              double scaleX, double scaleY, Consumer<Graphics2D> painter) {
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.scale(scaleX, scaleY);
            buffer.paint(graphics, width, height, painter);
        } finally { graphics.dispose(); }
    }

    private static BufferedImage reference(int width, int height, double scaleX, double scaleY,
                                           Consumer<Graphics2D> painter) {
        BufferedImage image = opaqueImage((int) Math.ceil(width * scaleX), (int) Math.ceil(height * scaleY));
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(scaleX, scaleY);
            paintReference(graphics, width, height, painter);
        } finally { graphics.dispose(); }
        return image;
    }

    private static void paintReference(Graphics2D graphics, int width, int height, Consumer<Graphics2D> painter) {
        graphics.clipRect(0, 0, width, height);
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, width, height);
        graphics.setComposite(AlphaComposite.SrcOver);
        painter.accept(graphics);
    }

    private static BufferedImage opaqueImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.MAGENTA);
            graphics.fillRect(0, 0, width, height);
        } finally { graphics.dispose(); }
        return image;
    }

    private static void assertPixels(BufferedImage actual, BufferedImage expected) {
        assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++) {
            assertThat(actual.getRGB(x, y)).as("pixel (%d,%d) in %dx%d", x, y, actual.getWidth(), actual.getHeight())
                .isEqualTo(expected.getRGB(x, y));
        }
    }
}
