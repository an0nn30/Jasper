package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/**
 * Compares a render with a preserved mock, as {@code tools/visual/compare_mock.py} does: luminance edges (the 90th
 * percentile's half, at least 12, of the central-difference gradient), agreement as the mean of both directions' share
 * of edge pixels within one pixel of the other's, CIE76 colour distance, and first-change probes along a line. Pure.
 */
public final class MockComparison {
    private MockComparison() { }

    /** CIE76 distance between two sRGB colours (D65). */
    public static double deltaE(Color a, Color b) {
        double[] p = lab(a), q = lab(b);
        return Math.sqrt(Math.pow(p[0] - q[0], 2) + Math.pow(p[1] - q[1], 2) + Math.pow(p[2] - q[2], 2));
    }

    /** CIE L*a*b* of an sRGB colour under D65; alpha is ignored. */
    public static double[] lab(Color color) {
        double r = linear(color.getRed()), g = linear(color.getGreen()), b = linear(color.getBlue());
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, y = 0.2126 * r + 0.7152 * g + 0.0722 * b, z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) { return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116; }

    /**
     * Steps from {@code (x, y)} by {@code (dx, dy)} and returns how many steps it took to reach a pixel more than 6 ΔE76
     * from {@code background}; -1 when none is reached in {@code steps}.
     */
    public static int edge(BufferedImage image, int x, int y, int dx, int dy, int steps, Color background) {
        for (int i = 0; i < steps; i++) {
            int px = x + i * dx, py = y + i * dy;
            if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) return -1;
            if (deltaE(new Color(image.getRGB(px, py)), background) > 6) return i;
        }
        return -1;
    }

    /** The edge mask of {@code area}, false inside every mask rectangle (given in the area's coordinates). */
    public static boolean[][] edges(BufferedImage image, Rectangle area, List<Rectangle> masks) {
        int w = area.width, h = area.height;
        double[][] gray = new double[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int rgb = image.getRGB(area.x + x, area.y + y);
            gray[y][x] = ((rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114) / 1000.0;
        }
        double[] magnitudes = new double[w * h];
        double[][] magnitude = new double[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double gx = x > 0 && x < w - 1 ? gray[y][x + 1] - gray[y][x - 1] : 0;
            double gy = y > 0 && y < h - 1 ? gray[y + 1][x] - gray[y - 1][x] : 0;
            magnitude[y][x] = Math.hypot(gx, gy);
            magnitudes[y * w + x] = magnitude[y][x];
        }
        double threshold = Math.max(12.0, percentile(magnitudes, 90) * 0.5);
        boolean[][] mask = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean masked = false;
            for (Rectangle m : masks) if (m.contains(x, y)) { masked = true; break; }
            mask[y][x] = !masked && magnitude[y][x] > threshold;
        }
        return mask;
    }

    /** numpy's default (linear) percentile. */
    static double percentile(double[] values, double percent) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double rank = percent / 100.0 * (sorted.length - 1);
        int low = (int) Math.floor(rank), high = (int) Math.ceil(rank);
        return sorted[low] + (sorted[high] - sorted[low]) * (rank - low);
    }

    private static boolean[][] dilate(boolean[][] mask) {
        int h = mask.length, w = h == 0 ? 0 : mask[0].length;
        boolean[][] out = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (mask[y][x])
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int ny = y + dy, nx = x + dx;
                if (ny >= 0 && ny < h && nx >= 0 && nx < w) out[ny][nx] = true;
            }
        return out;
    }

    /** Mean of both directions' share of edge pixels within one pixel of the other's edges; 0 when either has none. */
    public static double agreement(boolean[][] a, boolean[][] b) {
        boolean[][] da = dilate(a), db = dilate(b);
        long ca = 0, cb = 0, ab = 0, ba = 0;
        for (int y = 0; y < a.length; y++) for (int x = 0; x < a[0].length; x++) {
            if (a[y][x]) { ca++; if (db[y][x]) ab++; }
            if (b[y][x]) { cb++; if (da[y][x]) ba++; }
        }
        return ca == 0 || cb == 0 ? 0 : ((double) ab / ca + (double) ba / cb) / 2;
    }

    /** The best agreement of the two areas with the render shifted by at most {@code search} pixels each way. */
    public static double bestAgreement(BufferedImage mock, Rectangle mockArea, BufferedImage render, Rectangle renderArea,
                                       List<Rectangle> masks, int search) {
        var reference = edges(mock, mockArea, masks);
        double best = 0;
        for (int dy = -search; dy <= search; dy++) for (int dx = -search; dx <= search; dx++) {
            var shifted = new Rectangle(renderArea.x + dx, renderArea.y + dy, renderArea.width, renderArea.height);
            if (shifted.x < 0 || shifted.y < 0 || shifted.x + shifted.width > render.getWidth() || shifted.y + shifted.height > render.getHeight()) continue;
            best = Math.max(best, agreement(reference, edges(render, shifted, masks)));
        }
        return best;
    }

    /** Mock | render, side by side, for inspection. */
    public static BufferedImage side(BufferedImage mock, Rectangle mockArea, BufferedImage render, Rectangle renderArea) {
        var image = new BufferedImage(mockArea.width + renderArea.width + 8, Math.max(mockArea.height, renderArea.height), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(Color.MAGENTA); g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(mock.getSubimage(mockArea.x, mockArea.y, mockArea.width, mockArea.height), 0, 0, null);
            g.drawImage(render.getSubimage(renderArea.x, renderArea.y, renderArea.width, renderArea.height), mockArea.width + 8, 0, null);
        } finally { g.dispose(); }
        return image;
    }
}
