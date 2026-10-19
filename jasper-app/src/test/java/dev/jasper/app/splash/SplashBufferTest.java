package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.startup.StartupSteps;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.BitSet;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 10.11, no motion: a step changes the splash's step line and its bar and nothing else, and each pixel it changes
 * goes straight from the old splash's value to the new one's, so no cleared or partly drawn frame reaches the screen.
 * Windows and Linux show a window's pixels as they are drawn; the screen here records every pixel a paint writes.
 */
class SplashBufferTest {
    static final StartupSteps.Step FIRST = new StartupSteps.Step(StartupSteps.RUNTIME, 1, 8);
    static final StartupSteps.Step NEXT = new StartupSteps.Step(StartupSteps.PLUGIN + "Credential Vault", 4, 8);

    /** A window's screen at {@code scale} device pixels per window pixel, recording what each paint writes. */
    static final class Screen {
        final double scale;
        final int width, height;
        final Pixels pixels;
        final BufferedImage image;

        Screen(double scale) {
            this.scale = scale;
            width = (int) Math.ceil(SplashPainter.WIDTH * scale);
            height = (int) Math.ceil(SplashPainter.HEIGHT * scale);
            pixels = new Pixels(width * height);
            var model = new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, new int[] {0xFF0000, 0xFF00, 0xFF});
            image = new BufferedImage(new DirectColorModel(24, 0xFF0000, 0xFF00, 0xFF), Raster.createWritableRaster(model, pixels, null),
                false, null);
        }

        /** Runs {@code paint} on the window's graphics, at the screen's scale. */
        void paint(Consumer<Graphics2D> paint) {
            Graphics2D g = image.createGraphics();
            try { g.scale(scale, scale); paint.accept(g); }
            finally { g.dispose(); }
        }

        /** Runs {@code paint} and returns what it wrote. */
        Pixels.Writes watch(Consumer<Graphics2D> paint) {
            pixels.writes = new Pixels.Writes(new BitSet(), new BitSet(), new BitSet(), width);
            try { paint(paint); return pixels.writes; }
            finally { pixels.writes = null; }
        }

        /** The splash as one paint of {@code step} draws it at the screen's scale, over the window's first fill. */
        int[] expected(SplashPainter painter, StartupSteps.Step step) {
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(SplashColors.TOP);
                g.fillRect(0, 0, width, height);
                g.scale(scale, scale);
                painter.paint(g, step.text(), step.fraction());
            } finally {
                g.dispose();
            }
            return rgb(image);
        }

        /** Where the screen differs from {@code expected}: how many pixels and their box; empty when it does not. */
        String differences(int[] expected) {
            int[] shown = rgb(image);
            int count = 0, left = width, top = height, right = -1, bottom = -1;
            for (int i = 0; i < shown.length; i++) {
                if (shown[i] == expected[i]) continue;
                count++;
                left = Math.min(left, i % width); right = Math.max(right, i % width);
                top = Math.min(top, i / width); bottom = Math.max(bottom, i / width);
            }
            return count == 0 ? "" : count + " pixels in x " + left + ".." + right + ", y " + top + ".." + bottom;
        }

        /** {@code window}, a rectangle in window pixels, in device pixels: every device pixel it touches. */
        Rectangle device(Rectangle window) {
            int left = (int) Math.floor(window.x * scale), top = (int) Math.floor(window.y * scale);
            return new Rectangle(left, top, (int) Math.ceil(window.getMaxX() * scale) - left, (int) Math.ceil(window.getMaxY() * scale) - top);
        }

        private static int[] rgb(BufferedImage image) {
            int[] rgb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            for (int i = 0; i < rgb.length; i++) rgb[i] &= 0xFFFFFF;
            return rgb;
        }
    }

    /** A screen's pixels, which record each write while a paint is watched. */
    static final class Pixels extends DataBuffer {
        /**
         * What a paint wrote: each pixel written, each pixel whose value changed, and each pixel whose value changed twice
         * or more, as a clear followed by a draw does.
         */
        record Writes(BitSet written, BitSet changed, BitSet changedAgain, int width) {
            /** How many written pixels lie outside {@code allowed}, in device pixels. */
            int outside(Rectangle allowed) {
                return (int) written.stream().filter(i -> !allowed.contains(i % width, i / width)).count();
            }
        }

        final int[] data;
        Writes writes;

        Pixels(int size) {
            super(TYPE_INT, size);
            data = new int[size];
        }

        @Override public int getElem(int bank, int i) { return data[i]; }

        @Override public void setElem(int bank, int i, int value) {
            if (writes != null) {
                writes.written().set(i);
                if (value != data[i]) {
                    if (writes.changed().get(i)) writes.changedAgain().set(i);
                    writes.changed().set(i);
                }
            }
            data[i] = value;
        }
    }

    static SplashPainter painter() { return SplashPainter.of(SplashPainterTest.content(), 2); }

    /** 1.07 leaves the window a fraction of a device pixel past its last whole column and row. */
    @ParameterizedTest @ValueSource(doubles = {1, 1.07, 1.25, 1.5, 1.75, 2})
    void aStepRepaintsItsLineAndTheBarOnlyAndNoPixelPassesThroughAnotherValue(double scale) {
        var painter = painter();
        var buffer = new SplashBuffer(painter);
        var screen = new Screen(scale);
        screen.paint(g -> buffer.paint(g, FIRST));
        assertThat(screen.differences(screen.expected(painter, FIRST))).as("the first paint shows the whole splash").isEmpty();

        var writes = screen.watch(g -> buffer.show(g, NEXT));

        // The step line's band runs from below the icon (279 + 64) to the window's bottom border, the bar included.
        assertThat(writes.outside(screen.device(new Rectangle(1, 343, 718, 96))))
            .as("pixels a step wrote above its line, or on the border").isZero();
        assertThat(writes.changedAgain().cardinality()).as("pixels that passed through a third value: a cleared or partial frame").isZero();
        assertThat(writes.changed().isEmpty()).isFalse();
        assertThat(screen.differences(screen.expected(painter, NEXT))).as("the screen shows the new step's splash").isEmpty();
    }

    @Test void aStepThatOnlyMovesTheFillRepaintsTheBarOnly() {
        var painter = painter();
        var buffer = new SplashBuffer(painter);
        var screen = new Screen(1);
        var loading = new StartupSteps.Step(StartupSteps.PLUGIN + "Tunnels", 3, 8);
        var again = new StartupSteps.Step(loading.text(), 4, 8);
        screen.paint(g -> buffer.paint(g, loading));

        var writes = screen.watch(g -> buffer.show(g, again));

        assertThat(writes.outside(new Rectangle(1, 433, 718, 6))).as("pixels written outside the bar").isZero();
        assertThat(writes.changed().cardinality()).as("the fill grew by 90 columns of 6 pixels").isEqualTo(90 * 6);
        assertThat(writes.changedAgain().isEmpty()).isTrue();
        assertThat(screen.differences(screen.expected(painter, again))).isEmpty();
    }

    /** A system paint (an expose, after the toolkit cleared its area) copies its clip from the buffer and writes nothing else. */
    @ParameterizedTest @ValueSource(doubles = {1, 1.25, 1.5})
    void anExposeRepaintsItsClipFromTheBuffer(double scale) {
        var painter = painter();
        var buffer = new SplashBuffer(painter);
        var screen = new Screen(scale);
        screen.paint(g -> buffer.paint(g, FIRST));
        screen.paint(g -> buffer.show(g, NEXT));
        var exposed = new Rectangle(100, 200, 300, 220);
        screen.paint(g -> { g.setColor(SplashColors.TOP); g.fill(exposed); });

        var writes = screen.watch(g -> { g.clip(exposed); buffer.paint(g, NEXT); });

        assertThat(writes.outside(screen.device(exposed))).isZero();
        assertThat(writes.changedAgain().isEmpty()).isTrue();
        assertThat(screen.differences(screen.expected(painter, NEXT))).isEmpty();
    }

    /** A window moved to a screen of another scale gets the whole splash at that scale. */
    @Test void aPaintAtAnotherScaleDrawsTheWholeSplashAtIt() {
        var painter = painter();
        var buffer = new SplashBuffer(painter);
        new Screen(1).paint(g -> buffer.paint(g, FIRST));
        var retina = new Screen(2);

        retina.paint(g -> buffer.show(g, NEXT));

        assertThat(retina.differences(retina.expected(painter, NEXT))).isEmpty();
    }
}
