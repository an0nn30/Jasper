package dev.jasper.app.splash;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;

/** One decoded still landscape, with native runtime lettering and progress. */
final class ThemeSplash {
    private static final Set<String> IDS = Set.of("jasper-meadow", "slate-cobalt", "ubuntu-yaru", "catppuccin-latte",
        "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha");
    private final BufferedImage art;
    private final String version;
    private final SplashColors.Palette colors;
    private final Color background;

    ThemeSplash(String id, String version) {
        this.version = Objects.requireNonNull(version, "version");
        String bundled = IDS.contains(id) ? id : "jasper-meadow";
        colors = SplashColors.palette(bundled);
        try (var input = ThemeSplash.class.getResourceAsStream("themes/" + bundled + ".png")) {
            art = decode(input);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        background = new Color(art.getRGB(art.getWidth() / 2, 0));
    }

    static BufferedImage decode(InputStream input) {
        if (input == null) throw new IllegalArgumentException("Missing theme splash illustration");
        try {
            var image = ImageIO.read(input);
            if (image == null || image.getWidth() < 1440 || image.getHeight() < 880
                    || Math.abs(image.getWidth() / (double) image.getHeight() - 720d / 440) > .002)
                throw new IllegalArgumentException("Theme splash requires a landscape of at least 1440 by 880 pixels at the splash aspect ratio");
            return image;
        } catch (IOException failure) { throw new IllegalArgumentException("Unreadable theme splash illustration", failure); }
    }

    Color background() { return background; }

    void paint(Graphics2D g, String step, double fraction) {
        g.setColor(background); g.fillRect(0, 0, 720, 440);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        var clip = g.getClip();
        g.clipRect(0, 0, 720, 354);
        // Preserve the full-canvas illustration's proportions; generated dimensions can round by one pixel.
        double factor = Math.max(720d / art.getWidth(), 440d / art.getHeight());
        var transform = AffineTransform.getTranslateInstance((720 - art.getWidth() * factor) / 2,
            (440 - art.getHeight() * factor) / 2);
        transform.scale(factor, factor);
        g.drawImage(art, transform, null);
        g.setClip(clip);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        text(g, "Jasper", Font.SANS_SERIF, Font.BOLD, 58, colors.ink(), 262, 151, 420);
        text(g, "Make yourself at", Font.SANS_SERIF, Font.PLAIN, 18, colors.muted(), 262, 191, 420);
        text(g, "127.0.0.1", Font.MONOSPACED, Font.PLAIN, 22, colors.accent(), 262, 219, 420);
        text(g, version, Font.MONOSPACED, Font.PLAIN, 11, colors.muted(), 262, 257, 420);
        g.setColor(colors.panel()); g.fillRect(0, 354, 720, 81);
        g.setColor(colors.border()); g.fillRect(0, 354, 720, 1);
        g.setColor(colors.accent()); g.fillOval(32, 391, 5, 5);
        text(g, step, Font.SANS_SERIF, Font.PLAIN, 12, colors.ink(), 46, 399, 420);
        text(g, "Your infrastructure. One workspace.", Font.SANS_SERIF, Font.PLAIN, 10, colors.muted(), 505, 399, 183);
        g.setColor(colors.track()); g.fillRect(1, 434, 718, 5);
        g.setColor(colors.accent()); g.fillRect(1, 434, SplashPainter.fillWidth(fraction), 5);
        g.setColor(colors.border()); g.drawRect(0, 0, 719, 439);
    }

    private static void text(Graphics2D g, String value, String family, int style, int size, Color color,
                             int x, int baseline, int width) {
        var face = SplashPainter.displayable(new Font(family, style, size), value, family, style);
        g.setFont(face); g.setColor(color);
        g.drawString(SplashPainter.fit(face, g.getFontRenderContext(), value, width), x, baseline);
    }
}
