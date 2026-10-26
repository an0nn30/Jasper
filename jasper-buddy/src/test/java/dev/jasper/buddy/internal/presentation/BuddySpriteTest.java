package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.animation.BuddyFrame;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuddySpriteTest {
    @Test void transitionPosesAppendWithoutChangingExistingArtwork() throws Exception {
        assertThat(BuddyFrame.values()).hasSize(23);
        assertThat(BuddyFrame.valueOf("CLOSE_LAPTOP").column()).isEqualTo(20);
        assertThat(BuddyFrame.valueOf("LOWER_LAPTOP").column()).isEqualTo(21);
        assertThat(BuddyFrame.valueOf("RISE").column()).isEqualTo(22);
        BuddySprite sprite = BuddySprite.load();
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var in = BuddySprite.class.getResourceAsStream(BuddySprite.RESOURCE)) {
            var sheet = javax.imageio.ImageIO.read(in);
            for (int y = 0; y < 96; y++) for (int x = 0; x < 84 * 20; x++) {
                int pixel = sheet.getRGB(x, y);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(pixel).array());
            }
        }
        assertThat(java.util.HexFormat.of().formatHex(digest.digest()))
            .isEqualTo("07627f6cacddaa18c64a1ac3863621a97c6880dbfb20b56f416eb1417db90b40");
        for (int i = 19; i < 23; i++) for (int j = i + 1; j < 23; j++)
            assertThat(pixels(sprite.frame(BuddyFrame.values()[i])))
                .isNotEqualTo(pixels(sprite.frame(BuddyFrame.values()[j])));
    }

    @Test void pixelArtworkKeepsTheCompanionsLogicalSize() throws Exception {
        try (var in = BuddySprite.class.getResourceAsStream(BuddySprite.RESOURCE)) {
            var sheet = javax.imageio.ImageIO.read(in);
            assertThat(sheet.getWidth()).isEqualTo(84 * BuddyFrame.values().length);
            assertThat(sheet.getHeight()).isEqualTo(96);
        }
        assertThat(BuddySprite.size(2)).isEqualTo(new Dimension(84, 96));
    }

    @Test void committedSheetHasEveryFrameOpaqueInsideATransparentMargin() {
        BuddySprite sprite = BuddySprite.load();
        for (BuddyFrame frame : BuddyFrame.values()) {
            BufferedImage image = sprite.frame(frame);
            assertThat(image.getWidth()).isEqualTo(BuddySprite.ART_WIDTH);
            assertThat(image.getHeight()).isEqualTo(BuddySprite.ART_HEIGHT);
            boolean opaque = false;
            for (int y = 0; y < image.getHeight() && !opaque; y++)
                for (int x = 0; x < image.getWidth() && !opaque; x++) opaque = (image.getRGB(x, y) >>> 24) == 255;
            assertThat(opaque).as("%s has opaque pixels", frame).isTrue();
            for (int y = 0; y < image.getHeight(); y++) {
                assertThat(image.getRGB(0, y) >>> 24).as("%s left margin row %d", frame, y).isZero();
                assertThat(image.getRGB(image.getWidth() - 1, y) >>> 24).as("%s right margin row %d", frame, y).isZero();
            }
            for (int x = 0; x < image.getWidth(); x++)
                assertThat(image.getRGB(x, image.getHeight() - 1) >>> 24).as("%s bottom margin column %d", frame, x).isZero();
        }
        assertThat(BuddyFrame.values()).hasSize(BuddyFrame.values().length);
        assertThat(BuddyFrame.HOP.column()).isEqualTo(5);
        assertThat(BuddyFrame.SLEEP_C.column()).isEqualTo(13);
        assertThat(BuddyFrame.SPARKLE_C.column()).isEqualTo(16);
        assertThat(BuddySprite.size(2)).isEqualTo(new Dimension(84, 96));
    }

    @Test void sparkleFramesAreStarsOnlyAndLeaveTheBodyUncovered() {
        BuddySprite sprite = BuddySprite.load();
        int star = new Color(0xff, 0xf3, 0xb0).getRGB();
        for (BuddyFrame frame : List.of(BuddyFrame.SPARKLE_A, BuddyFrame.SPARKLE_B, BuddyFrame.SPARKLE_C)) {
            BufferedImage image = sprite.frame(frame);
            assertThat(image.getRGB(42, 60) >>> 24).as("%s must not cover the body's centre", frame).isZero();
            assertThat(pixels(image)).as("%s must use the star colour", frame).contains(star);
        }
        assertThat(pixels(sprite.frame(BuddyFrame.SPARKLE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.SPARKLE_B)));
        assertThat(pixels(sprite.frame(BuddyFrame.SPARKLE_B))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.SPARKLE_C)));
    }

    @Test void framesAreDistinctAndPixelArtHasShading() {
        BuddySprite sprite = BuddySprite.load();
        assertThat(pixels(sprite.frame(BuddyFrame.IDLE))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.BLINK)));
        assertThat(pixels(sprite.frame(BuddyFrame.WAVE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.WAVE_B)));
        assertThat(java.util.Arrays.stream(pixels(sprite.frame(BuddyFrame.IDLE)))
            .filter(argb -> (argb >>> 24) >= 240).distinct().count()).isGreaterThan(64);
    }

    @Test void paintingKeepsLogicalBoundsAndDoesNotLeakAnAdjacentFrame() {
        BufferedImage sheet = new BufferedImage(84 * BuddyFrame.values().length, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D art = sheet.createGraphics();
        art.setColor(Color.RED);
        art.fillRect(84 * BuddyFrame.WINK.column(), 0, 84, 96);
        art.setColor(Color.BLUE);
        art.fillRect(84 * BuddyFrame.WAVE_A.column(), 0, 84, 96);
        art.dispose();
        BuddySprite sprite = new BuddySprite(sheet);
        BufferedImage target = new BufferedImage(100, 112, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        sprite.paint(g, BuddyFrame.WINK, 2, 8, 8);
        g.dispose();
        for (int y = 0; y < 112; y++) for (int x = 0; x < 100; x++) {
            boolean inside = x >= 8 && x < 92 && y >= 8 && y < 104;
            assertThat(target.getRGB(x, y)).as("(%d,%d)", x, y).isEqualTo(inside ? Color.RED.getRGB() : 0);
        }
    }

    @Test void packedPixelArtHasNoTranslucentFringe() {
        BuddySprite sprite = BuddySprite.load();
        for (BuddyFrame frame : BuddyFrame.values())
            for (int argb : pixels(sprite.frame(frame))) assertThat(argb >>> 24).isIn(0, 255);
    }

    @Test void pixelScalingDoesNotInventBlendedColors() {
        int width = BuddySprite.ART_WIDTH, height = BuddySprite.ART_HEIGHT;
        BufferedImage sheet = new BufferedImage(width * BuddyFrame.values().length, height, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(10, 20, Color.RED.getRGB());
        BuddySprite sprite = new BuddySprite(sheet);
        var size = BuddySprite.size(8);
        BufferedImage target = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        sprite.paint(g, BuddyFrame.IDLE, 8, 0, 0);
        g.dispose();
        int factor = size.width / width;
        for (int y = 0; y < size.height; y++) for (int x = 0; x < size.width; x++) {
            boolean red = x / factor == 10 && y / factor == 20;
            assertThat(target.getRGB(x, y)).as("pixel at (%d,%d)", x, y).isEqualTo(red ? Color.RED.getRGB() : 0);
        }
    }

    @Test void wrongSheetDimensionsAreRejected() {
        assertThatThrownBy(() -> new BuddySprite(new BufferedImage(42, 48, BufferedImage.TYPE_INT_ARGB)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(String.valueOf(BuddySprite.ART_WIDTH * BuddyFrame.values().length));
    }

    @Test void sleepFramesDifferFromEachOtherAndFromIdle() {
        BuddySprite sprite = BuddySprite.load();
        int[] a = pixels(sprite.frame(BuddyFrame.SLEEP_A));
        int[] b = pixels(sprite.frame(BuddyFrame.SLEEP_B));
        int[] c = pixels(sprite.frame(BuddyFrame.SLEEP_C));
        assertThat(a).isNotEqualTo(b);
        assertThat(b).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(pixels(sprite.frame(BuddyFrame.IDLE)));
        assertThat(pixels(sprite.frame(BuddyFrame.TUCK))).isNotEqualTo(a);
    }

    @Test void sitFramesDifferFromStanding() {
        BuddySprite sprite = BuddySprite.load();
        int[] sit = pixels(sprite.frame(BuddyFrame.SIT));
        assertThat(sit).isNotEqualTo(pixels(sprite.frame(BuddyFrame.IDLE)));
        assertThat(sit).isNotEqualTo(pixels(sprite.frame(BuddyFrame.SIT_BLINK)));
        assertThat(pixels(sprite.frame(BuddyFrame.SIT_BLINK))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.BLINK)));
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test void everyFrameIncludingTheTypingPosesDrawsSomething() {
        BuddySprite sprite = BuddySprite.load();
        for (BuddyFrame frame : BuddyFrame.values()) {
            boolean anyOpaque = false;
            for (int argb : pixels(sprite.frame(frame))) if ((argb >>> 24) != 0) { anyOpaque = true; break; }
            assertThat(anyOpaque).as("%s draws something", frame).isTrue();
        }
        assertThat(BuddyFrame.values()).contains(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B, BuddyFrame.TYPE_REST);
    }

    /** Two identical poses would read as a frozen buddy, not a typing one. */
    @Test void theTypingPosesDifferFromOneAnotherAndFromSitting() {
        BuddySprite sprite = BuddySprite.load();
        assertThat(pixels(sprite.frame(BuddyFrame.TYPE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.TYPE_B)));
        assertThat(pixels(sprite.frame(BuddyFrame.TYPE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.TYPE_REST)));
        assertThat(pixels(sprite.frame(BuddyFrame.TYPE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.SIT)));
    }
}
