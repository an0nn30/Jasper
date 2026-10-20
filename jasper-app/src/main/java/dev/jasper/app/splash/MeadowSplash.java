package dev.jasper.app.splash;

import dev.jasper.ui.icons.EncodedArtwork;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/** Jasper's standard splash: shared Meadow colours and the approved pixel companion artwork. */
final class MeadowSplash {
    private static final int[] POSES = {0,0,0,0,1,0,0,0,3,4,3,4,3,0,0,0,0,0,1,0};
    private final BufferedImage sprites;
    private final EncodedArtwork landscape;
    private final ConcurrentHashMap<Integer, BufferedImage> backgrounds = new ConcurrentHashMap<>();
    private final String version;
    private int column;

    MeadowSplash(String version, int scale) {
        this.version = java.util.Objects.requireNonNull(version, "version");
        try (var input = MeadowSplash.class.getResourceAsStream("jasper-pixel.png")) {
            if (input == null) throw new IllegalArgumentException("Missing splash sprite");
            sprites = ImageIO.read(input);
            if (sprites == null || sprites.getWidth() != 1680 || sprites.getHeight() != 96)
                throw new IllegalArgumentException("Invalid splash sprite strip");
            try (var art = MeadowSplash.class.getResourceAsStream("meadow.svg")) {
                if (art == null) throw new IllegalArgumentException("Missing meadow artwork");
                landscape = new EncodedArtwork(EncodedArtwork.Format.SVG, art.readAllBytes());
            }
            landscape.prepare();
            background(scale);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    void frame(int tick) { column = POSES[Math.floorMod(tick, POSES.length)]; }

    void paint(Graphics2D g, String step, double fraction) {
        int scale = Math.clamp((long) Math.ceil(Math.max(g.getTransform().getScaleX(), g.getTransform().getScaleY())), 1, 4);
        g.setColor(SplashColors.MEADOW_PAPER);
        g.fillRect(0, 0, 720, 440);
        g.drawImage(background(scale), 0, 0, 720, 354, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(sprites, 54, 90, 222, 282, column * 84, 0, (column + 1) * 84, 96, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        text(g, "Jasper", Font.SANS_SERIF, Font.BOLD, 58, SplashColors.MEADOW_INK, 262, 151, 420);
        text(g, "Make yourself at", Font.SANS_SERIF, Font.PLAIN, 18, SplashColors.MEADOW_MUTED, 262, 191, 420);
        text(g, "127.0.0.1", Font.MONOSPACED, Font.PLAIN, 22, SplashColors.MEADOW_MOSS, 262, 219, 420);
        text(g, version, Font.MONOSPACED, Font.PLAIN, 11, SplashColors.MEADOW_MUTED, 262, 257, 420);
        g.setColor(SplashColors.MEADOW_PANEL); g.fillRect(0, 354, 720, 81);
        g.setColor(SplashColors.MEADOW_BORDER); g.fillRect(0, 354, 720, 1);
        g.setColor(SplashColors.MEADOW_MOSS); g.fillOval(32, 391, 5, 5);
        text(g, step, Font.SANS_SERIF, Font.PLAIN, 12, SplashColors.MEADOW_INK, 46, 399, 420);
        text(g, "Your infrastructure. One workspace.", Font.SANS_SERIF, Font.PLAIN, 10,
            SplashColors.MEADOW_MUTED, 505, 399, 183);
        g.setColor(SplashColors.MEADOW_TRACK); g.fillRect(1, 434, 718, 5);
        g.setColor(SplashColors.MEADOW_MOSS); g.fillRect(1, 434, SplashPainter.fillWidth(fraction), 5);
        g.setColor(SplashColors.MEADOW_BORDER); g.drawRect(0, 0, 719, 439);
    }

    private BufferedImage background(int scale) {
        return backgrounds.computeIfAbsent(Math.clamp(scale, 1, 4), n -> landscape.raster(720 * n, 354 * n));
    }

    private static void text(Graphics2D g, String value, String family, int style, int size,
                             java.awt.Color color, int x, int y, int width) {
        var font = new Font(family, style, size);
        g.setFont(font); g.setColor(color);
        g.drawString(SplashPainter.fit(font, g.getFontRenderContext(), value, width), x, y);
    }
}
