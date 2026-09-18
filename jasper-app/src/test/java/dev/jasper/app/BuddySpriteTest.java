package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuddySpriteTest {
    @Test void committedSheetHasEveryFrameOpaqueInsideATransparentMargin() {
        BuddySprite sprite = BuddySprite.load();
        for (BuddyFrame frame : BuddyFrame.values()) {
            BufferedImage image = sprite.frame(frame);
            assertThat(image.getWidth()).isEqualTo(BuddySprite.FRAME_WIDTH);
            assertThat(image.getHeight()).isEqualTo(BuddySprite.FRAME_HEIGHT);
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
            assertThat(image.getRGB(21, 30) >>> 24).as("%s must not cover the body's centre", frame).isZero();
            assertThat(pixels(image)).as("%s must use the star colour", frame).contains(star);
        }
        assertThat(pixels(sprite.frame(BuddyFrame.SPARKLE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.SPARKLE_B)));
        assertThat(pixels(sprite.frame(BuddyFrame.SPARKLE_B))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.SPARKLE_C)));
    }

    @Test void framesAreDistinctAndTheOutlineColorIsPresent() {
        BuddySprite sprite = BuddySprite.load();
        assertThat(pixels(sprite.frame(BuddyFrame.IDLE))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.BLINK)));
        assertThat(pixels(sprite.frame(BuddyFrame.WAVE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.WAVE_B)));
        assertThat(pixels(sprite.frame(BuddyFrame.IDLE))).contains(new Color(0x33, 0x2f, 0x27).getRGB());
    }

    @Test void paintingScalesEveryArtPixelIntoASolidBlock() {
        BufferedImage sheet = new BufferedImage(BuddySprite.FRAME_WIDTH * BuddyFrame.values().length,
            BuddySprite.FRAME_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int red = new Color(200, 30, 30).getRGB();
        sheet.setRGB(BuddySprite.FRAME_WIDTH * BuddyFrame.WINK.column() + 10, 20, red);
        BuddySprite sprite = new BuddySprite(sheet);

        BufferedImage target = new BufferedImage(84, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        sprite.paint(g, BuddyFrame.WINK, 2, 0, 0);
        g.dispose();

        for (int y = 0; y < 96; y++) for (int x = 0; x < 84; x++) {
            boolean inside = x >= 20 && x < 22 && y >= 40 && y < 42;
            assertThat(target.getRGB(x, y)).as("(%d,%d)", x, y).isEqualTo(inside ? red : 0);
        }
    }

    @Test void wrongSheetDimensionsAreRejected() {
        assertThatThrownBy(() -> new BuddySprite(new BufferedImage(42, 48, BufferedImage.TYPE_INT_ARGB)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(String.valueOf(BuddySprite.FRAME_WIDTH * BuddyFrame.values().length));
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
