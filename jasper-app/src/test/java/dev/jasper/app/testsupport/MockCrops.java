package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * Headless comparison of the dock chrome with the preserved Enterprise mocks (Enterprise presentation spec 16.1.4),
 * beside plan 6a's {@link MockProfile}, which compares by profile: 2x crops of the mocks and of renders at any scale, the
 * Unlock Vault mock's scrim, the share of agreeing pixels, and ink runs along either axis (a side stripe's labels run
 * down it). Test only.
 */
public final class MockCrops {
    /** Where the dock tests write their renders, mock crops and differences. */
    public static final Path OUT = MockProfile.ROOT.resolve("jasper-app/build/dock-renders");
    public static final String CONNECTED = "01 Workstation/Workstation - Connected (plugins in use).png";
    public static final String DISCONNECTED = "01 Workstation/Workstation - Disconnected.png";
    public static final String DARK = "08 Dark Mode/Workstation Dark - Connected (plugins in use).png";
    public static final String VAULT = "07 Credential Vault/Credential Vault 1 - Unlock Vault (over Workstation).png";
    /** The Unlock Vault mock's window lies this far into its image at 2x: Disconnected's (x, y) is its (x + 64, y + 64). */
    public static final int VAULT_OFFSET = 64;
    /** The Unlock Vault mock's scrim over the window: black at 21 % (spec 10.8). */
    public static final double VAULT_SCRIM = 0.21;
    private static final Map<String, BufferedImage> MOCKS = new ConcurrentHashMap<>();

    private MockCrops() { }

    /** A mock by its path under the reference folder, read once through plan 6a's {@link MockProfile}. */
    public static BufferedImage mock(String name) { return MOCKS.computeIfAbsent(name, MockProfile::mock); }

    /** An opaque copy of {@code image}'s rectangle. */
    public static BufferedImage crop(BufferedImage image, int x, int y, int width, int height) {
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < height; row++)
            for (int column = 0; column < width; column++) copy.setRGB(column, row, image.getRGB(x + column, y + row));
        return copy;
    }

    /** {@code component} laid out at {@code width} x {@code height} and printed at {@code scale}, opaque. EDT. */
    public static BufferedImage render(JComponent component, int width, int height, int scale) {
        component.setSize(width, height);
        for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(component);
        var image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { g.scale(scale, scale); component.printAll(g); } finally { g.dispose(); }
        return image;
    }

    /** {@code image} under black at {@code alpha}, as a modal scrim lays it. */
    public static BufferedImage scrim(BufferedImage image, double alpha) {
        var out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                out.setRGB(x, y, new Color((int) Math.round(c.getRed() * (1 - alpha)), (int) Math.round(c.getGreen() * (1 - alpha)),
                    (int) Math.round(c.getBlue() * (1 - alpha))).getRGB());
            }
        return out;
    }

    public static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y)); }

    /**
     * An agreeing share that follows the UI face's glyphs, asserted at least {@code floor} under the shipped kit's IBM
     * Plex; printed, not asserted, where an installed IBM Plex hides the plugin's files, as {@link MockProfile#fonts}
     * treats a face-width probe.
     */
    public static void fontsFloor(String what, double actual, double floor) {
        if (!EnterpriseResources.plexResolves()) {
            System.out.printf("FONTS unchecked (an installed IBM Plex hides the plugin's files): %s %.3f, floor %.2f%n", what, actual, floor);
            return;
        }
        System.out.printf("FONTS %s %.3f, floor %.2f%n", what, actual, floor);
        org.assertj.core.api.Assertions.assertThat(actual).as(what + " under IBM Plex").isGreaterThanOrEqualTo(floor);
    }

    /** Whether every channel of {@code a} is within {@code tolerance} of {@code b}'s. */
    public static boolean near(Color a, Color b, int tolerance) {
        return Math.abs(a.getRed() - b.getRed()) <= tolerance && Math.abs(a.getGreen() - b.getGreen()) <= tolerance
            && Math.abs(a.getBlue() - b.getBlue()) <= tolerance;
    }

    /** The share of pixels whose every channel is within {@code tolerance}, over two images of one size. */
    public static double agreement(BufferedImage a, BufferedImage b, int tolerance) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            throw new IllegalArgumentException("Sizes differ: " + a.getWidth() + "x" + a.getHeight() + " and " + b.getWidth() + "x" + b.getHeight());
        long agree = 0;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) if (near(at(a, x, y), at(b, x, y), tolerance)) agree++;
        return agree / (double) ((long) a.getWidth() * a.getHeight());
    }

    /**
     * One stretch of ink along an axis: positions {@code start} to {@code end} inclusive, and its darkest or most
     * different pixel.
     *
     * @param start the first position with ink
     * @param end the last position with ink
     * @param ink the pixel that differs most from the backgrounds
     */
    public record Run(int start, int end, Color ink) {
        /** Its extent in pixels. */
        public int length() { return end - start + 1; }
    }

    /**
     * The ink runs of {@code image} along x, or along y when {@code vertical}: a position has ink when any pixel across it
     * differs from every background by more than {@code threshold} (the sum of the channel differences); positions closer
     * than {@code gap} join one run.
     */
    public static List<Run> runs(BufferedImage image, boolean vertical, int threshold, int gap, Color... backgrounds) {
        int along = vertical ? image.getHeight() : image.getWidth(), across = vertical ? image.getWidth() : image.getHeight();
        var runs = new ArrayList<Run>();
        int start = -1, end = -1, best = -1;
        Color ink = null;
        for (int i = 0; i < along; i++) {
            Color strongest = null;
            int strength = -1;
            for (int j = 0; j < across; j++) {
                Color pixel = vertical ? at(image, j, i) : at(image, i, j);
                int distance = Integer.MAX_VALUE;
                for (Color background : backgrounds) distance = Math.min(distance, Math.abs(pixel.getRed() - background.getRed())
                    + Math.abs(pixel.getGreen() - background.getGreen()) + Math.abs(pixel.getBlue() - background.getBlue()));
                if (distance > threshold && distance > strength) { strength = distance; strongest = pixel; }
            }
            if (strongest == null) continue;
            if (start >= 0 && i - end > gap) { runs.add(new Run(start, end, ink)); start = -1; best = -1; }
            if (start < 0) start = i;
            end = i;
            if (strength > best) { best = strength; ink = strongest; }
        }
        if (start >= 0) runs.add(new Run(start, end, ink));
        return runs;
    }

    /** Writes the render, the mock crop and their difference (white where they agree within 24) under {@link #OUT}. */
    public static void write(String name, BufferedImage render, BufferedImage mock) {
        try {
            Files.createDirectories(OUT);
            ImageIO.write(render, "png", OUT.resolve(name + "-render.png").toFile());
            ImageIO.write(mock, "png", OUT.resolve(name + "-mock.png").toFile());
            if (render.getWidth() == mock.getWidth() && render.getHeight() == mock.getHeight()) {
                var diff = new BufferedImage(render.getWidth(), render.getHeight(), BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < diff.getHeight(); y++)
                    for (int x = 0; x < diff.getWidth(); x++) diff.setRGB(x, y, near(at(render, x, y), at(mock, x, y), 24) ? 0xFFFFFF : 0xD03A3A);
                ImageIO.write(diff, "png", OUT.resolve(name + "-diff.png").toFile());
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
