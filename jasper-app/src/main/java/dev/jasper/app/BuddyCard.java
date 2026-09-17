package dev.jasper.app;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.UIManager;

/** One card, wherever it is drawn. The column and the drawer share this so they cannot drift apart. */
final class BuddyCard {
    static final int WIDTH = 300;
    static final int PAD_X = 14;
    static final int PAD_Y = 10;
    static final int LINE_GAP = 3;
    static final int GLYPH_SIZE = 12;
    static final int GLYPH_GAP = 10;
    private static final String ELLIPSIS = "…";
    private static final String DISMISS = "×";

    private static final Color FILL = new Color(30, 30, 32, 235);
    private static final Color FILL_HIGHLIGHTED = new Color(58, 58, 62, 242);
    private static final Color BORDER = new Color(255, 255, 255, 26);
    private static final Color TITLE_COLOR = Color.WHITE;
    private static final Color DETAIL_COLOR = new Color(160, 160, 166);
    private static final Color ORPHAN_TITLE_COLOR = new Color(255, 255, 255, 140);
    static final Color MUTED_COLOR = new Color(190, 190, 196);

    private BuddyCard() { }

    static Font titleFont() { return BuddyFonts.system(Font.BOLD, 14f); }

    static Font detailFont() { return BuddyFonts.system(Font.PLAIN, 12f); }

    static int height(JComponent owner) {
        FontMetrics title = owner.getFontMetrics(titleFont());
        FontMetrics detail = owner.getFontMetrics(detailFont());
        return Math.max(title.getHeight() + LINE_GAP + detail.getHeight(), GLYPH_SIZE) + 2 * PAD_Y;
    }

    /** A producer's supplier is arbitrary code; one that throws must not take the surface down. */
    static String detailOf(BuddyNotice notice) {
        try {
            String detail = notice.detail().get();
            return detail == null ? "" : detail;
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /**
     * {@code count}, when non-zero, is drawn as a remainder badge; {@code dismissable} adds the ×
     * that {@link BuddyDeckLayout#dismissTarget} hit-tests.
     */
    static void paint(Graphics2D g2, JComponent owner, BuddyNotice notice, Rectangle bounds,
                      boolean highlighted, float scale, float opacity, boolean dismissable, int count) {
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.min(1f, Math.max(0f, opacity))));
            // Grow about the card's centre-left, so it expands away from the buddy rather than over him.
            c.translate(bounds.x, bounds.y + bounds.height / 2f);
            c.scale(scale, scale);
            c.translate(0, -bounds.height / 2f);
            int arc = BuddyBubblePanel.radiusFor(bounds.height) * 2;
            c.setColor(highlighted ? FILL_HIGHLIGHTED : FILL);
            c.fillRoundRect(0, 0, bounds.width, bounds.height, arc, arc);
            c.setColor(BORDER);
            c.drawRoundRect(0, 0, bounds.width - 1, bounds.height - 1, arc, arc);

            c.setFont(detailFont());
            FontMetrics small = c.getFontMetrics();
            int right = bounds.width - PAD_X;
            if (dismissable) {
                c.setColor(highlighted ? MUTED_COLOR : DETAIL_COLOR);
                c.drawString(DISMISS, bounds.width - BuddyDeckLayout.DISMISS_INSET
                        - BuddyDeckLayout.DISMISS_SIZE + 2,
                    BuddyDeckLayout.DISMISS_INSET + small.getAscent());
                right = bounds.width - BuddyDeckLayout.DISMISS_INSET - BuddyDeckLayout.DISMISS_SIZE - 4;
            } else if (count > 0) {
                String badge = Integer.toString(count);
                c.setColor(MUTED_COLOR);
                c.drawString(badge, bounds.width - PAD_X - small.stringWidth(badge),
                    bounds.height - PAD_Y - small.getHeight() + small.getAscent());
                right = bounds.width - PAD_X - small.stringWidth(badge) - GLYPH_GAP;
            }
            if (notice.wantsAttention()) {
                paintGlyph(c, notice.state(), right - GLYPH_SIZE, (bounds.height - GLYPH_SIZE) / 2);
                right -= GLYPH_SIZE + GLYPH_GAP;
            }

            int available = Math.max(0, right - PAD_X);
            FontMetrics title = c.getFontMetrics(titleFont());
            int textHeight = title.getHeight() + LINE_GAP + small.getHeight();
            int y = (bounds.height - textHeight) / 2;
            c.setFont(titleFont());
            c.setColor(notice.orphaned() ? ORPHAN_TITLE_COLOR : TITLE_COLOR);
            c.drawString(fit(title, notice.title(), available), PAD_X, y + title.getAscent());
            c.setFont(detailFont());
            c.setColor(DETAIL_COLOR);
            c.drawString(fit(small, detailOf(notice), available),
                PAD_X, y + title.getHeight() + LINE_GAP + small.getAscent());
        } finally { c.dispose(); }
    }

    /**
     * A check for a good ending, a cross for a bad one, a dot for anything still unresolved —
     * waiting on you, or a connection that is not healthy.
     */
    private static void paintGlyph(Graphics2D g2, BuddyNotice.State state, int x, int y) {
        boolean good = state == BuddyNotice.State.DONE;
        boolean bad = state == BuddyNotice.State.FAILED || state == BuddyNotice.State.DOWN;
        Color configured = UIManager.getColor(
            good ? "Jasper.configSuccessForeground" : "Jasper.configErrorForeground");
        Color ink = good || bad
            ? (configured != null ? configured
                : good ? new Color(0x5A, 0xB0, 0x7A) : new Color(0xC4, 0x5A, 0x53))
            : MUTED_COLOR;
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setColor(ink);
            c.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (good) {
                c.drawLine(x + 2, y + 6, x + 5, y + 9);
                c.drawLine(x + 5, y + 9, x + 10, y + 3);
            } else if (bad) {
                c.drawLine(x + 3, y + 3, x + 9, y + 9);
                c.drawLine(x + 9, y + 3, x + 3, y + 9);
            } else {
                c.fillOval(x + 3, y + 3, GLYPH_SIZE - 6, GLYPH_SIZE - 6);
            }
        } finally { c.dispose(); }
    }

    /** Truncates with an ellipsis instead of wrapping; a card is one line tall per text line. */
    private static String fit(FontMetrics metrics, String text, int available) {
        if (metrics.stringWidth(text) <= available) return text;
        int ellipsis = metrics.stringWidth(ELLIPSIS);
        int end = text.length();
        while (end > 0 && ellipsis + metrics.stringWidth(text.substring(0, end)) > available) end--;
        return text.substring(0, end) + ELLIPSIS;
    }
}
