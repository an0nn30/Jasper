package dev.jasper.app;

import dev.jasper.buddy.notice.BuddyNotice;

import com.formdev.flatlaf.FlatLaf;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.UIManager;

/** The same measured capsule and typography in the live column and the history drawer. */
final class BuddyCard {
    static final int WIDTH = 330;
    static final int HEIGHT = 56;
    static final int PAD_X = 16;
    static final int PAD_Y = 12;
    static final int LINE_HEIGHT = 16;
    static final int GLYPH_SIZE = 12;
    static final int GLYPH_GAP = 10;
    static final int SHADOW_MARGIN = 32;
    private static final String ELLIPSIS = "…";

    // A recording over a flat background determines the composited color, not a unique alpha.
    // These translucent paints reproduce the measured #414141 / #f9f9f9 over the reference white.
    private static final Color DARK_FILL = new Color(37, 37, 37, 222);
    private static final Color LIGHT_FILL = new Color(248, 248, 248, 230);
    private static final BufferedImage SHADOW = shadow();
    private record Material(int width, int height, boolean dark) { }
    private static final java.util.Map<Material, BufferedImage> MATERIALS = new java.util.LinkedHashMap<>();

    private BuddyCard() { }

    static Font titleFont() {
        return SystemFonts.system(Font.BOLD, 13f);
    }

    static Font detailFont() { return SystemFonts.system(Font.PLAIN, 13f); }

    static int height(JComponent owner) { return HEIGHT; }

    static Color detailColor() { return FlatLaf.isLafDark() ? new Color(171, 171, 171) : new Color(109, 109, 109); }

    /** A producer's supplier is arbitrary code; one that throws must not take the surface down. */
    static String detailOf(BuddyNotice notice) {
        try {
            String detail = notice.detail().get();
            return detail == null ? "" : detail;
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /** Hover reveals an open affordance without scaling the capsule or moving its text. */
    static void paint(Graphics2D g2, JComponent owner, BuddyNotice notice, Rectangle bounds,
                      float hover, float opacity, boolean dismissable, int count) {
        paint(g2, owner, notice, bounds, hover, opacity, dismissable, count, System.nanoTime());
    }

    static boolean shimmers(BuddyNotice notice) {
        return notice.state() == BuddyNotice.State.RUNNING && !notice.orphaned();
    }

    static void paint(Graphics2D g2, JComponent owner, BuddyNotice notice, Rectangle bounds,
                      float hover, float opacity, boolean dismissable, int count, long now) {
        Graphics2D c = (Graphics2D) g2.create();
        try {
            boolean dark = FlatLaf.isLafDark();
            float alpha = Math.min(1f, Math.max(0f, opacity));
            c.translate(bounds.x, bounds.y);
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            c.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            c.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            c.setComposite(AlphaComposite.SrcOver.derive(alpha));
            c.drawImage(material(bounds.width, bounds.height, dark), -SHADOW_MARGIN, -SHADOW_MARGIN,
                bounds.width + 2 * SHADOW_MARGIN, bounds.height + 2 * SHADOW_MARGIN, null);

            int right = bounds.width - PAD_X;
            if (dismissable) {
                Rectangle dismiss = BuddyDeckLayout.dismissTarget(new Rectangle(0, 0, bounds.width, bounds.height));
                paintAction(c, dismiss.x, dismiss.y, dark, hover, true);
                right = dismiss.x - 6;
            }
            if (count > 0) {
                c.setFont(detailFont());
                String badge = Integer.toString(count);
                c.setColor(detailColor());
                c.drawString(badge, right - c.getFontMetrics().stringWidth(badge), 40);
                right -= c.getFontMetrics().stringWidth(badge) + GLYPH_GAP;
            }
            if (notice.wantsAttention()) {
                paintGlyph(c, notice.state(), right - GLYPH_SIZE, (bounds.height - GLYPH_SIZE) / 2);
                right -= GLYPH_SIZE + GLYPH_GAP;
            }
            // Reserve the affordance's space only while it is visible. Text never changes position.
            if (hover > 0 && !notice.orphaned()) {
                paintAction(c, right - 32, (bounds.height - 32) / 2, dark, hover, false);
                right -= Math.round(38 * hover);
            }
            Graphics2D text = (Graphics2D) c.create();
            try {
                text.clipRect(PAD_X, 0, Math.max(0, right - PAD_X), bounds.height);
                text.setFont(titleFont());
                text.setColor(notice.orphaned() ? detailColor() : dark ? Color.WHITE : new Color(30, 30, 30));
                paintText(text, notice.title(), right, 25, hover);
                text.setFont(detailFont());
                text.setColor(detailColor());
                String detail = detailOf(notice);
                paintText(text, detail, right, 41, hover);
                if (shimmers(notice)) paintShimmer(text, detail, right, hover, dark, now);
            } finally { text.dispose(); }
        } finally { c.dispose(); }
    }

    /** Rasterize the unchanged material once, rather than clipping its shadow on every 60 Hz frame. */
    private static synchronized BufferedImage material(int width, int height, boolean dark) {
        Material key = new Material(width, height, dark);
        BufferedImage cached = MATERIALS.get(key);
        if (cached != null) return cached;
        BufferedImage image = new BufferedImage((width + 2 * SHADOW_MARGIN) * 2,
            (height + 2 * SHADOW_MARGIN) * 2, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D c = image.createGraphics();
        try {
            c.scale(2, 2); c.translate(SHADOW_MARGIN, SHADOW_MARGIN);
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            c.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            RoundRectangle2D body = new RoundRectangle2D.Float(0, 0, width, height, height, height);
            Graphics2D shadow = (Graphics2D) c.create();
            try {
                java.awt.geom.Area outside = new java.awt.geom.Area(new Rectangle(-SHADOW_MARGIN,
                    -SHADOW_MARGIN, width + 2 * SHADOW_MARGIN, height + 2 * SHADOW_MARGIN));
                outside.subtract(new java.awt.geom.Area(body)); shadow.clip(outside);
                shadow.setComposite(AlphaComposite.SrcOver.derive(dark ? .025f : .105f));
                shadow.drawImage(SHADOW, -SHADOW_MARGIN, -SHADOW_MARGIN,
                    width + 2 * SHADOW_MARGIN, height + 2 * SHADOW_MARGIN, null);
            } finally { shadow.dispose(); }
            c.setColor(dark ? DARK_FILL : LIGHT_FILL); c.fill(body);
            c.setColor(new Color(255, 255, 255, dark ? 35 : 215)); c.setStroke(new BasicStroke(.75f));
            c.draw(new RoundRectangle2D.Float(.375f, .375f, width - .75f, height - .75f, height - .75f, height - .75f));
        } finally { c.dispose(); }
        // Normal surfaces need two entries. Bound unusual preview/test sizes as well.
        if (MATERIALS.size() >= 4) MATERIALS.clear();
        MATERIALS.put(key, image);
        return image;
    }

    /** A soft moving highlight confined to the detail glyphs; no card glow or opacity pulsing. */
    private static void paintShimmer(Graphics2D g, String detail, int right, float hover, boolean dark, long now) {
        String text = hover > 0 ? detail : fit(g.getFontMetrics(), detail, right - PAD_X);
        if (text.isEmpty()) return;
        int width = Math.min(right - PAD_X, g.getFontMetrics().stringWidth(text));
        if (width <= 0) return;
        // Sweep over the text, then leave a short quiet interval before the next pass.
        double phase = Math.floorMod(now, 2_400_000_000L) / 1_000_000_000d;
        float half = 24f;
        float center = PAD_X - half + (width + 2 * half) * (float) (phase / 1.65);
        if (center - half > PAD_X + width) return;
        Color clear = new Color(dark ? 255 : 0, dark ? 255 : 0, dark ? 255 : 0, 0);
        Color peak = new Color(dark ? 255 : 0, dark ? 255 : 0, dark ? 255 : 0, dark ? 150 : 105);
        g.setPaint(new java.awt.LinearGradientPaint(center - half, 0, center + half, 0,
            new float[]{0, .25f, .5f, .75f, 1}, new Color[]{clear, new Color(peak.getRed(), peak.getGreen(), peak.getBlue(), peak.getAlpha()/3),
                peak, new Color(peak.getRed(), peak.getGreen(), peak.getBlue(), peak.getAlpha()/3), clear}));
        g.drawString(text, PAD_X, 41);
    }

    private static void paintText(Graphics2D g, String value, int right, int baseline, float hover) {
        if (hover > 0) {
            Color color = g.getColor();
            g.setPaint(new java.awt.GradientPaint(Math.max(PAD_X, right - 20), 0, color,
                Math.max(PAD_X + 1, right), 0,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(255 * (1 - hover)))));
            g.drawString(value, PAD_X, baseline);
        } else g.drawString(fit(g.getFontMetrics(), value, right - PAD_X), PAD_X, baseline);
    }

    private static void paintAction(Graphics2D g, int x, int y, boolean dark, float hover, boolean dismiss) {
        Graphics2D c = (Graphics2D) g.create();
        try {
            c.translate(x, y);
            c.setColor(new Color(dark ? 255 : 0, dark ? 255 : 0, dark ? 255 : 0,
                (int) (hover * (dark ? 28 : 18))));
            c.fillOval(0, 0, 32, 32);
            c.setColor(new Color(dark ? 220 : 120, dark ? 220 : 120, dark ? 220 : 120,
                (int) (255 * (dismiss ? 0.5f + hover * 0.5f : hover))));
            c.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (dismiss) {
                c.drawLine(12, 12, 20, 20);
                c.drawLine(20, 12, 12, 20);
            } else {
                c.drawLine(22, 12, 22, 17);
                c.drawLine(22, 17, 11, 17);
                c.drawLine(11, 17, 15, 13);
                c.drawLine(11, 17, 15, 21);
            }
        } finally { c.dispose(); }
    }

    private static void paintGlyph(Graphics2D g2, BuddyNotice.State state, int x, int y) {
        boolean good = state == BuddyNotice.State.DONE;
        boolean bad = state == BuddyNotice.State.FAILED || state == BuddyNotice.State.DOWN;
        Color configured = UIManager.getColor(good ? "Jasper.configSuccessForeground" : "Jasper.configErrorForeground");
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setColor(good || bad ? (configured != null ? configured
                : good ? new Color(0x5AB07A) : new Color(0xC45A53)) : detailColor());
            c.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (good) {
                c.drawLine(x + 2, y + 6, x + 5, y + 9);
                c.drawLine(x + 5, y + 9, x + 10, y + 3);
            } else if (bad) {
                c.drawLine(x + 3, y + 3, x + 9, y + 9);
                c.drawLine(x + 9, y + 3, x + 3, y + 9);
            } else c.fillOval(x + 3, y + 3, GLYPH_SIZE - 6, GLYPH_SIZE - 6);
        } finally { c.dispose(); }
    }

    /** Cached Gaussian shadow at Retina resolution; no blur allocation or convolution per frame. */
    private static BufferedImage shadow() {
        int width = (WIDTH + 2 * SHADOW_MARGIN) * 2;
        int height = (HEIGHT + 2 * SHADOW_MARGIN) * 2;
        float[] mask = new float[width * height];
        double radius = HEIGHT;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double cx = Math.max(SHADOW_MARGIN * 2 + radius,
                Math.min(x, (SHADOW_MARGIN + WIDTH) * 2 - radius));
            double cy = (SHADOW_MARGIN + HEIGHT / 2 + 8) * 2;
            if (Math.hypot(x - cx, y - cy) <= radius) mask[y * width + x] = 1;
        }
        int reach = 96;
        double[] kernel = new double[reach * 2 + 1];
        double sum = 0;
        for (int i = -reach; i <= reach; i++) sum += kernel[i + reach] = Math.exp(-i * i / (2d * 32 * 32));
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;
        float[] horizontal = new float[mask.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            for (int k = -reach; k <= reach; k++) {
                if (x + k >= 0 && x + k < width) horizontal[y * width + x] += (float) (mask[y * width + x + k] * kernel[k + reach]);
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double alpha = 0;
            for (int k = -reach; k <= reach; k++) {
                if (y + k >= 0 && y + k < height) alpha += horizontal[(y + k) * width + x] * kernel[k + reach];
            }
            image.setRGB(x, y, Math.min(255, (int) Math.round(alpha * 255)) << 24);
        }
        return image;
    }

    /** Remove whole code points, so truncation cannot split an emoji's surrogate pair. */
    private static String fit(FontMetrics metrics, String text, int available) {
        if (metrics.stringWidth(text) <= available) return text;
        int ellipsis = metrics.stringWidth(ELLIPSIS);
        if (available < ellipsis) return "";
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (ellipsis + metrics.stringWidth(text.substring(0, end)) <= available) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ELLIPSIS;
    }
}
