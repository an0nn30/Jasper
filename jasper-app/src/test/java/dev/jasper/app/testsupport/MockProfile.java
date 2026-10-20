package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * Compares a 2× render with a 2× reference mock by profile (Enterprise presentation plans 6 to 10): runs of one colour
 * along a column or a row, and clusters of ink along a row. Colours compare as CIE76 ΔE in L*a*b* (D65). A profile that
 * differs is a chrome defect or a recorded residual.
 */
public final class MockProfile {
    /** Where chrome renders are written for inspection and the opt-in pairs. */
    public static final Path RENDERS = EnterpriseResources.ROOT.resolve("jasper-app/build/chrome-renders");

    private MockProfile() { }

    /**
     * Pixels of one colour in a row or a column.
     *
     * @param start the first pixel
     * @param length how many
     * @param color the first pixel's colour
     */
    public record Run(int start, int length, Color color) { }

    /**
     * Adjacent columns holding ink (a pixel unlike the region's background).
     *
     * @param start the first column
     * @param end one past the last column
     * @param top the first inked row
     * @param bottom one past the last inked row
     */
    public record Ink(int start, int end, int top, int bottom) {
        /** The cluster's width. */
        public int width() { return end - start; }
    }

    /** The mock at {@code relative} under {@link EnterpriseResources#REFERENCE}. */
    public static BufferedImage mock(String relative) {
        try { return ImageIO.read(EnterpriseResources.REFERENCE.resolve(relative).toFile()); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** The opaque colour at {@code (x, y)}. */
    public static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y) & 0xFFFFFF); }

    /** Runs down column {@code x} from {@code from} (inclusive) to {@code to} (exclusive); a pixel within ΔE 1 joins its run. */
    public static List<Run> column(BufferedImage image, int x, int from, int to) { return runs(image, x, from, to, true); }

    /** Runs along row {@code y} from {@code from} to {@code to}. */
    public static List<Run> row(BufferedImage image, int y, int from, int to) { return runs(image, y, from, to, false); }

    private static List<Run> runs(BufferedImage image, int fixed, int from, int to, boolean vertical) {
        var runs = new ArrayList<Run>();
        int start = from;
        Color first = vertical ? at(image, fixed, from) : at(image, from, fixed);
        for (int i = from + 1; i <= to; i++) {
            Color next = i == to ? null : vertical ? at(image, fixed, i) : at(image, i, fixed);
            if (next != null && deltaE(next, first) <= 1) continue;
            runs.add(new Run(start, i - start, first));
            start = i; first = next;
        }
        return runs;
    }

    /** The runs at least {@code minimum} long: antialiased edges and text drop out. */
    public static List<Run> solid(List<Run> runs, int minimum) { return runs.stream().filter(run -> run.length() >= minimum).toList(); }

    /**
     * The ink clusters in {@code region}: a column holds ink where a pixel is more than {@code threshold} ΔE from the
     * region's top-left pixel, and columns at most {@code gap} apart join a cluster.
     */
    public static List<Ink> inks(BufferedImage image, Rectangle region, int gap, double threshold) {
        Color background = at(image, region.x, region.y);
        var inks = new ArrayList<Ink>();
        int start = -1, last = -1, top = Integer.MAX_VALUE, bottom = -1;
        for (int x = region.x; x < region.x + region.width; x++) {
            int columnTop = -1, columnBottom = -1;
            for (int y = region.y; y < region.y + region.height; y++)
                if (deltaE(at(image, x, y), background) > threshold) { if (columnTop < 0) columnTop = y; columnBottom = y + 1; }
            if (columnTop < 0) continue;
            if (start >= 0 && x - last > gap) { inks.add(new Ink(start, last + 1, top, bottom)); start = -1; top = Integer.MAX_VALUE; bottom = -1; }
            if (start < 0) start = x;
            last = x; top = Math.min(top, columnTop); bottom = Math.max(bottom, columnBottom);
        }
        if (start >= 0) inks.add(new Ink(start, last + 1, top, bottom));
        return inks;
    }

    /**
     * A probe that follows the UI face's advance widths (plan 6b Execution notes R36 and R47), asserted within
     * {@code tolerance} under the shipped kit's IBM Plex; printed, not asserted, where an installed IBM Plex hides the
     * plugin's files, so that the render draws the platform face (plan 5b Execution notes, ruling 3).
     *
     * @param what the probe
     * @param actual the render's value, in the mock's pixels
     * @param expected the mock's value
     * @param tolerance the tolerance it is asserted within under Plex
     */
    public static void fonts(String what, int actual, int expected, int tolerance) {
        if (!EnterpriseResources.plexResolves()) {
            System.out.printf("FONTS unchecked (an installed IBM Plex hides the plugin's files): %s %d, mock %d (tolerance %d)%n", what, actual, expected, tolerance);
            return;
        }
        org.assertj.core.api.Assertions.assertThat(actual).as(what + " under IBM Plex, mock " + expected)
            .isCloseTo(expected, org.assertj.core.api.Assertions.within(tolerance));
    }

    /** {@code key}'s colour in the shipped Enterprise theme's Light or Dark variant ({@link dev.jasper.ui.testsupport.KitFixtures#colour}). */
    public static Color role(boolean dark, String key) { return dev.jasper.ui.testsupport.KitFixtures.colour(EnterpriseLook.theme(dark), key); }

    /** What a face paints for {@code pair} in the shipped Enterprise theme's Light or Dark variant. */
    public static Color painted(boolean dark, dev.jasper.ui.theme.PaintPair pair) {
        return dev.jasper.ui.testsupport.KitFixtures.painted(EnterpriseLook.theme(dark), pair);
    }

    /** CIE76 ΔE between two opaque sRGB colours ({@link MockComparison#deltaE}). */
    public static double deltaE(Color a, Color b) { return MockComparison.deltaE(a, b); }

    /** {@code root} laid out at {@code width} × {@code height} logical pixels and printed at 2×. EDT. */
    public static BufferedImage print(JComponent root, int width, int height) {
        root.setSize(width, height);
        LayoutTestSupport.layoutTree(root);
        LayoutTestSupport.layoutTree(root);
        var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); root.printAll(graphics); } finally { graphics.dispose(); }
        return image;
    }

    /** Writes {@code image} as {@code <name>.png} under {@link #RENDERS}. */
    public static Path write(BufferedImage image, String name) {
        try {
            Files.createDirectories(RENDERS);
            Path file = RENDERS.resolve(name + ".png");
            ImageIO.write(image, "png", file.toFile());
            return file;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
