package dev.jasper.app.splash;

import dev.jasper.app.startup.StartupSteps;
import dev.jasper.ui.icons.EncodedArtwork;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Standard splash composition. All mutable state is owned by Splash's paint lock. */
final class ProteanSplash {
    static final int WIDTH = 840, HEIGHT = 480;
    private static final int TEXT_X = 348, TEXT_WIDTH = 450, QUEUE_BASELINE = 294, ROW_HEIGHT = 20;
    private static final int BAR_X = 29, BAR_Y = 441, BAR_WIDTH = 783, BAR_HEIGHT = 6;
    private static final Color BACKGROUND = SplashColors.PROTEAN_BACKGROUND, RED = SplashColors.PROTEAN_RED,
        WHITE = SplashColors.PROTEAN_WHITE, DIM = SplashColors.PROTEAN_DIM;
    private final SplashQueue queue;
    private final String build;
    private final Font mono;
    private final EncodedArtwork mark;
    private final Map<Integer, BufferedImage> rasters = new HashMap<>();
    private long now;

    ProteanSplash(String build, List<String> labels, int scale) {
        this.build = java.util.Objects.requireNonNull(build, "build");
        queue = new SplashQueue(labels);
        try (var art = ProteanSplash.class.getResourceAsStream("protean-mark.svg");
             var face = ProteanSplash.class.getResourceAsStream("/dev/jasper/app/fonts/cousine/CousineNerdFontMono-Regular.ttf")) {
            if (art == null || face == null) throw new IllegalStateException("Missing Protean splash resources");
            mark = new EncodedArtwork(EncodedArtwork.Format.SVG, art.readAllBytes());
            mark.prepare();
            mono = Font.createFont(Font.TRUETYPE_FONT, face);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (java.awt.FontFormatException failure) {
            throw new IllegalArgumentException("Invalid bundled splash font", failure);
        }
        raster(Math.clamp(scale, 1, 4));
    }

    void show(StartupSteps.Step step, long time, boolean animations) {
        now = time;
        queue.show(step, time, animations);
    }

    boolean time(long time) {
        boolean moving = queue.frame(now).previousIndex() != 0;
        now = time;
        return moving;
    }

    private BufferedImage raster(int scale) {
        return rasters.computeIfAbsent(scale, factor -> mark.raster(212 * factor, 168 * factor));
    }

    void paint(Graphics2D g, double fraction) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setPaint(new RadialGradientPaint(240, 240, 260, new float[] {0, 1},
            new Color[] {SplashColors.PROTEAN_GLOW, SplashColors.PROTEAN_CLEAR}));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        int scale = Math.clamp((int) Math.ceil(Math.max(g.getTransform().getScaleX(), g.getTransform().getScaleY())), 1, 4);
        g.drawImage(raster(scale), 80, 134, 212, 168, null);
        text(g, "Protean", new Font(Font.SANS_SERIF, Font.BOLD, 96), WHITE, TEXT_X, 215, TEXT_WIDTH);
        text(g, "The terminal, transformed.", new Font(Font.SANS_SERIF, Font.PLAIN, 32), WHITE, TEXT_X, 255, TEXT_WIDTH);
        paintQueue(g);
        text(g, build, mono.deriveFont(12f), DIM, TEXT_X, 379, TEXT_WIDTH);
        g.setColor(SplashColors.PROTEAN_TRACK);
        g.fillRoundRect(BAR_X, BAR_Y, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
        g.setColor(RED);
        g.fillRoundRect(BAR_X, BAR_Y, (int) Math.round(BAR_WIDTH * Math.clamp(fraction, 0, 1)),
            BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
    }

    private void paintQueue(Graphics2D g) {
        var frame = queue.frame(now);
        var labels = queue.labels();
        double eased = 1 - Math.pow(1 - frame.progress(), 3);
        var rows = (Graphics2D) g.create();
        try {
            rows.clipRect(TEXT_X, 277, TEXT_WIDTH, 81);
            if (frame.previousIndex() != 0) {
                var outgoing = (Graphics2D) rows.create();
                try {
                    outgoing.setComposite(AlphaComposite.SrcOver.derive((float) (1 - eased)));
                    row(outgoing, labels.get(frame.previousIndex() - 1), QUEUE_BASELINE - ROW_HEIGHT * eased, true);
                } finally {
                    outgoing.dispose();
                }
            }
            double offset = frame.previousIndex() == 0 ? 0 : ROW_HEIGHT * (1 - eased);
            for (int i = 0; i < 4 && frame.index() - 1 + i < labels.size(); i++) {
                row(rows, labels.get(frame.index() - 1 + i), QUEUE_BASELINE + ROW_HEIGHT * i + offset, i == 0);
            }
        } finally {
            rows.dispose();
        }
    }

    private void row(Graphics2D g, String label, double baseline, boolean active) {
        g.setColor(active ? RED : DIM);
        int dotY = (int) Math.round(baseline) - 8;
        if (active) g.fillOval(357, dotY, 7, 7);
        else g.drawOval(357, dotY, 6, 6);
        text(g, "[ ]", mono.deriveFont(13f), active ? WHITE : DIM, TEXT_X, (float) baseline, 38);
        text(g, label, mono.deriveFont(13f), active ? WHITE : DIM, 390, (float) baseline, 408);
    }

    private static void text(Graphics2D g, String value, Font font, Color color, float x, float baseline, float width) {
        font = SplashPainter.displayable(font, value, Font.SANS_SERIF, font.getStyle());
        if (font.getSize2D() > 30) {
            float advance = SplashPainter.advance(font, g.getFontRenderContext(), value);
            if (advance > width) font = font.deriveFont(font.getSize2D() * width / advance);
        }
        g.setFont(font);
        g.setColor(color);
        g.drawString(SplashPainter.fit(font, g.getFontRenderContext(), value, width), x, baseline);
    }
}
