package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Compares a 2x render with a crop of an Enterprise mock (spec 16.1.4) in JUnit: the luminance-edge agreement of
 * {@code tools/visual/compare_mock.py} over the same shift search, colour bands down a sample column, and colour presence.
 * Images are compared at their own (device) pixels; bands report logical pixels (device pixels halved).
 */
public final class MockMatch {
    /** The preserved mocks. */
    public static final Path REFERENCE = EnterpriseResources.ROOT.resolve("docs/design/enterprise/reference");
    /** Where renders and side-by-side images go, for inspection and for {@code tools/visual/pairs.json}. */
    public static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-compositions");
    private static final int SEARCH = 12;

    /**
     * A crop of one mock, in its 2x pixels.
     *
     * @param mock the mock's path under {@link #REFERENCE}
     * @param x left edge
     * @param y top edge
     * @param width width in device pixels
     * @param height height in device pixels
     */
    public record Crop(String mock, int x, int y, int width, int height) {
        /** The crop as an opaque RGB image. */
        public BufferedImage read() {
            try {
                BufferedImage whole = ImageIO.read(REFERENCE.resolve(mock).toFile());
                var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                var graphics = copy.createGraphics();
                try { graphics.drawImage(whole.getSubimage(x, y, width, height), 0, 0, null); } finally { graphics.dispose(); }
                return copy;
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        /** The crop's logical width (device pixels halved). */
        public int logicalWidth() { return width / 2; }

        /** The crop's logical height (device pixels halved). */
        public int logicalHeight() { return height / 2; }
    }

    /**
     * One run down a column, in logical pixels: a single colour, or a smooth gradient from {@code first} to {@code last}.
     *
     * @param start first logical row
     * @param length logical rows
     * @param first the run's first colour
     * @param last the run's last colour
     */
    public record Band(int start, int length, Color first, Color last) {
        @Override public String toString() {
            String colours = hex(first) + (deltaE(first, last) <= 2 ? "" : "~" + hex(last));
            return start + "+" + length + " " + colours;
        }
    }

    private MockMatch() { }

    static String hex(Color colour) { return String.format("#%06X", colour.getRGB() & 0xFFFFFF); }

    /** {@code render} laid over {@code under} and made opaque, as the surface below a panel body shows it. */
    public static BufferedImage onto(BufferedImage render, Color under) {
        var opaque = new BufferedImage(render.getWidth(), render.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = opaque.createGraphics();
        try {
            graphics.setColor(under); graphics.fillRect(0, 0, render.getWidth(), render.getHeight());
            graphics.drawImage(render, 0, 0, null);
        } finally { graphics.dispose(); }
        return opaque;
    }

    /**
     * The bands down device column {@code x}: consecutive pixels within ΔE76 1.5 of each other form one run (so a gradient
     * is one band), runs under 2 device pixels (antialiasing) are dropped, and equal neighbours left by a dropped run merge.
     */
    public static List<Band> bands(BufferedImage image, int x) { return bands(image, x, 0, image.getHeight()); }

    /** The bands down device column {@code x} between device rows {@code top} (inclusive) and {@code bottom}, from {@code top}. */
    public static List<Band> bands(BufferedImage image, int x, int top, int bottom) {
        record Run(int start, int length, Color first, Color last) { }
        var runs = new ArrayList<Run>();
        int start = top; Color first = new Color(image.getRGB(x, top)), previous = first;
        for (int y = top + 1; y <= bottom; y++) {
            Color colour = y < bottom ? new Color(image.getRGB(x, y)) : null;
            if (colour == null || deltaE(colour, previous) > 1.5) {
                runs.add(new Run(start, y - start, first, previous));
                if (colour != null) { start = y; first = colour; }
            }
            if (colour != null) previous = colour;
        }
        var kept = new ArrayList<Run>();
        for (var run : runs) {
            if (run.length() < 2) continue;
            if (!kept.isEmpty() && deltaE(kept.getLast().last(), run.first()) <= 1.5) {
                var merged = kept.removeLast();
                kept.add(new Run(merged.start(), run.start() + run.length() - merged.start(), merged.first(), run.last()));
            } else kept.add(run);
        }
        var bands = new ArrayList<Band>();
        for (var run : kept) bands.add(new Band((run.start() - top) / 2, Math.max(1, (run.start() + run.length() - top) / 2 - (run.start() - top) / 2), run.first(), run.last()));
        return List.copyOf(bands);
    }

    /** Parses bands as the plan's measurement tables write them: {@code "0+36 #F7F7F8, 7+20 #FFFFFF~#F1F2F4"}. */
    public static List<Band> parse(String text) {
        var bands = new ArrayList<Band>();
        for (String part : text.split(",")) {
            String[] cells = part.strip().split("[+ ]");
            String[] colours = cells[2].split("~");
            Color first = Color.decode(colours[0]), last = colours.length > 1 ? Color.decode(colours[1]) : first;
            bands.add(new Band(Integer.parseInt(cells[0]), Integer.parseInt(cells[1]), first, last));
        }
        return List.copyOf(bands);
    }

    /**
     * Empty when {@code actual} matches {@code expected} band for band, each length within {@code tolerance} logical
     * pixels and each colour within ΔE76 4; otherwise both lists, for the assertion message.
     */
    public static String mismatch(List<Band> expected, List<Band> actual, int tolerance) {
        boolean same = expected.size() == actual.size();
        for (int i = 0; same && i < expected.size(); i++) {
            var e = expected.get(i); var a = actual.get(i);
            same = Math.abs(e.length() - a.length()) <= tolerance && deltaE(e.first(), a.first()) <= 4 && deltaE(e.last(), a.last()) <= 4;
        }
        return same ? "" : "expected " + expected + "\n  actual " + actual;
    }

    /** CIE76 colour difference under D65. */
    public static double deltaE(Color a, Color b) {
        double[] p = lab(a), q = lab(b);
        return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
    }

    private static double[] lab(Color colour) {
        double[] rgb = {colour.getRed() / 255.0, colour.getGreen() / 255.0, colour.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= 0.04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        double x = (0.4124 * rgb[0] + 0.3576 * rgb[1] + 0.1805 * rgb[2]) / 0.95047;
        double y = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
        double z = (0.0193 * rgb[0] + 0.1192 * rgb[1] + 0.9505 * rgb[2]) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116; }

    /**
     * Whether a 2x2 block of {@code image} lies within ΔE76 6 of {@code colour}: {@code check_pairs.py}'s mock tolerance,
     * used here for the render too, where {@code check_pairs.py} asks 1.5, so neighbouring Enterprise neutrals can pass
     * for each other.
     */
    public static boolean present(BufferedImage image, Color colour) {
        int w = image.getWidth(), h = image.getHeight();
        var close = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) close[y * w + x] = deltaE(new Color(image.getRGB(x, y)), colour) <= 6;
        for (int y = 0; y + 1 < h; y++) for (int x = 0; x + 1 < w; x++)
            if (close[y * w + x] && close[y * w + x + 1] && close[(y + 1) * w + x] && close[(y + 1) * w + x + 1]) return true;
        return false;
    }

    /**
     * compare_mock.py's score: both images on one canvas (magenta outside each), their luminance edges (central
     * differences above max(12, half the 90th percentile)), and the best symmetric fraction of edge pixels within one pixel
     * of the other's edges over render shifts of up to 12 pixels (a coarse 2-pixel search, then the 1-pixel neighbours).
     */
    public static double agreement(BufferedImage mock, BufferedImage render) {
        int w = Math.max(mock.getWidth(), render.getWidth()), h = Math.max(mock.getHeight(), render.getHeight());
        boolean[] a = edges(canvas(mock, w, h), w, h), b = edges(canvas(render, w, h), w, h);
        boolean[] da = dilate(a, w, h), db = dilate(b, w, h);
        double best = -1; int bx = 0, by = 0;
        for (int dy = -SEARCH; dy <= SEARCH; dy += 2) for (int dx = -SEARCH; dx <= SEARCH; dx += 2) {
            double score = score(a, da, shift(b, w, h, dx, dy), shift(db, w, h, dx, dy));
            if (score > best) { best = score; bx = dx; by = dy; }
        }
        int cx = bx, cy = by;
        for (int dy = cy - 1; dy <= cy + 1; dy++) for (int dx = cx - 1; dx <= cx + 1; dx++) {
            double score = score(a, da, shift(b, w, h, dx, dy), shift(db, w, h, dx, dy));
            if (score > best) best = score;
        }
        return best;
    }

    private static int[] canvas(BufferedImage image, int w, int h) {
        var pixels = new int[w * h];
        Arrays.fill(pixels, 0xFF00FF);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) pixels[y * w + x] = image.getRGB(x, y) & 0xFFFFFF;
        return pixels;
    }

    private static boolean[] edges(int[] pixels, int w, int h) {
        var gray = new float[w * h];
        for (int i = 0; i < gray.length; i++) {
            int p = pixels[i];
            gray[i] = (((p >> 16) & 0xFF) * 299 + ((p >> 8) & 0xFF) * 587 + (p & 0xFF) * 114) / 1000f;
        }
        var magnitude = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float gx = x > 0 && x < w - 1 ? gray[y * w + x + 1] - gray[y * w + x - 1] : 0;
            float gy = y > 0 && y < h - 1 ? gray[(y + 1) * w + x] - gray[(y - 1) * w + x] : 0;
            magnitude[y * w + x] = (float) Math.hypot(gx, gy);
        }
        var sorted = magnitude.clone();
        Arrays.sort(sorted);
        double threshold = Math.max(12.0, sorted[(int) Math.round(0.9 * (sorted.length - 1))] * 0.5);
        var edges = new boolean[w * h];
        for (int i = 0; i < edges.length; i++) edges[i] = magnitude[i] > threshold;
        return edges;
    }

    private static boolean[] shift(boolean[] mask, int w, int h, int dx, int dy) {
        var result = new boolean[w * h];
        for (int y = Math.max(0, dy); y < Math.min(h, h + dy); y++)
            for (int x = Math.max(0, dx); x < Math.min(w, w + dx); x++) result[y * w + x] = mask[(y - dy) * w + x - dx];
        return result;
    }

    private static boolean[] dilate(boolean[] mask, int w, int h) {
        var out = mask.clone();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            var moved = shift(mask, w, h, dx, dy);
            for (int i = 0; i < out.length; i++) out[i] |= moved[i];
        }
        return out;
    }

    private static double score(boolean[] a, boolean[] da, boolean[] b, boolean[] db) {
        long na = 0, nb = 0, ab = 0, ba = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i]) { na++; if (db[i]) ab++; }
            if (b[i]) { nb++; if (da[i]) ba++; }
        }
        return na == 0 || nb == 0 ? 0 : ((double) ab / na + (double) ba / nb) / 2;
    }

    /** Writes {@code <OUT>/<name>@2x.png} and a mock | render side-by-side {@code <OUT>/<name>-side.png}. */
    public static void save(String name, BufferedImage mock, BufferedImage render) {
        try {
            Path file = OUT.resolve(name + "@2x.png");
            Files.createDirectories(file.getParent());
            ImageIO.write(render, "png", file.toFile());
            var side = new BufferedImage(mock.getWidth() + render.getWidth() + 10, Math.max(mock.getHeight(), render.getHeight()), BufferedImage.TYPE_INT_RGB);
            var graphics = side.createGraphics();
            try { graphics.drawImage(mock, 0, 0, null); graphics.drawImage(render, mock.getWidth() + 10, 0, null); } finally { graphics.dispose(); }
            ImageIO.write(side, "png", OUT.resolve(name + "-side.png").toFile());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
