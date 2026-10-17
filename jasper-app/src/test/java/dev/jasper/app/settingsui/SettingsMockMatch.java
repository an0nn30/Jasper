package dev.jasper.app.settingsui;

import dev.jasper.app.testsupport.MockProfile;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Compares a headless Settings render at 2x with a crop of its Enterprise mock (Enterprise presentation spec 16.1.4, plan
 * 9a): the share of pixels within a CIE76 distance after the best small alignment, and single-pixel probes. Reads the mocks
 * and measures colour through plan 6a's {@link MockProfile}; writes the render, the crop and a difference image under
 * build/ for inspection. Tests only.
 */
final class SettingsMockMatch {
    private SettingsMockMatch() { }

    /**
     * The best alignment found.
     *
     * @param matching the share of compared pixels within the tolerance, 0 to 1
     * @param dx the render's horizontal offset that matched best, in pixels
     * @param dy the render's vertical offset that matched best, in pixels
     */
    record Result(double matching, int dx, int dy) { }

    /** The mock {@code name}, a path under {@link MockProfile#REFERENCE}, as opaque RGB over white. */
    static BufferedImage mock(String name) {
        var image = MockProfile.mock(name);
        var copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        try { g.setColor(Color.WHITE); g.fillRect(0, 0, copy.getWidth(), copy.getHeight()); g.drawImage(image, 0, 0, null); } finally { g.dispose(); }
        return copy;
    }

    /** A copy of {@code area} of {@code image}. */
    static BufferedImage crop(BufferedImage image, Rectangle area) {
        var copy = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        try { g.drawImage(image, -area.x, -area.y, null); } finally { g.dispose(); }
        return copy;
    }

    /** The CIE76 distance between two sRGB colours, as {@link MockProfile#deltaE} measures it; alpha is ignored. */
    static double deltaE(int a, int b) { return MockProfile.deltaE(new Color(a & 0xFFFFFF), new Color(b & 0xFFFFFF)); }

    private static double linear(double channel) { return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4); }
    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0; }

    /** CIE L*a*b* of an sRGB colour under D65, MockProfile's conversion, kept here so {@link #compare} converts each colour once. */
    private static double[] lab(int rgb) {
        double r = linear(((rgb >> 16) & 0xFF) / 255.0), g = linear(((rgb >> 8) & 0xFF) / 255.0), b = linear((rgb & 0xFF) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, y = 0.2126 * r + 0.7152 * g + 0.0722 * b,
            z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        return new double[] {116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))};
    }

    private static float[] labs(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        var out = new float[3 * w * h];
        var cache = new java.util.HashMap<Integer, double[]>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            var value = cache.computeIfAbsent(image.getRGB(x, y) & 0xFFFFFF, SettingsMockMatch::lab);
            int i = 3 * (y * w + x);
            out[i] = (float) value[0]; out[i + 1] = (float) value[1]; out[i + 2] = (float) value[2];
        }
        return out;
    }

    /**
     * The best alignment of {@code render} over {@code mock}, both the same size, within {@code shift} pixels each way: the
     * share of the pixels inside a {@code shift}-wide border whose distance is at most {@code tolerance}.
     */
    static Result compare(BufferedImage render, BufferedImage mock, int shift, double tolerance) {
        if (render.getWidth() != mock.getWidth() || render.getHeight() != mock.getHeight())
            throw new IllegalArgumentException("render " + render.getWidth() + "x" + render.getHeight() + " against mock " + mock.getWidth() + "x" + mock.getHeight());
        int w = mock.getWidth(), h = mock.getHeight();
        float[] a = labs(render), b = labs(mock);
        double limit = tolerance * tolerance;
        var best = new Result(-1, 0, 0);
        for (int dy = -shift; dy <= shift; dy++) for (int dx = -shift; dx <= shift; dx++) {
            long hits = 0, all = 0;
            for (int y = shift; y < h - shift; y++) for (int x = shift; x < w - shift; x++) {
                int i = 3 * ((y + dy) * w + x + dx), j = 3 * (y * w + x);
                float l = a[i] - b[j], p = a[i + 1] - b[j + 1], q = a[i + 2] - b[j + 2];
                if (l * l + p * p + q * q <= limit) hits++;
                all++;
            }
            double share = all == 0 ? 0 : (double) hits / all;
            if (share > best.matching()) best = new Result(share, dx, dy);
        }
        return best;
    }

    /** Writes {@code render}, {@code mock} and their difference (mismatches magenta over a faded render) as {@code name}-*.png. */
    static void inspect(Path directory, String name, BufferedImage render, BufferedImage mock, double tolerance) {
        try {
            Files.createDirectories(directory);
            ImageIO.write(render, "png", directory.resolve(name + "-render.png").toFile());
            ImageIO.write(mock, "png", directory.resolve(name + "-mock.png").toFile());
            var diff = new BufferedImage(mock.getWidth(), mock.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < mock.getHeight(); y++) for (int x = 0; x < mock.getWidth(); x++) {
                int r = render.getRGB(x, y), m = mock.getRGB(x, y);
                int faded = 0xC0C0C0 | ((r >> 2) & 0x3F3F3F);
                diff.setRGB(x, y, deltaE(r, m) <= tolerance ? faded : 0xFF00FF);
            }
            ImageIO.write(diff, "png", directory.resolve(name + "-diff.png").toFile());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
