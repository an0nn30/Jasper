package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;

/** Paints one bubble: a dark translucent rounded rectangle with a title, an optional detail line and a glyph. */
final class BuddyBubblePanel extends JComponent {
    static final int PAD_X = 18;
    static final int PAD_Y = 14;
    static final int LINE_GAP = 4;
    static final int GLYPH_GAP = 16;
    static final int RADIUS = 14;
    static final int MIN_WIDTH = 120;
    static final int MAX_WIDTH = 320;

    private static final Color FILL = new Color(30, 30, 32, 235);
    private static final Color FILL_HIGHLIGHTED = new Color(58, 58, 62, 242);
    private static final Color BORDER = new Color(255, 255, 255, 26);
    private static final Color TITLE_COLOR = Color.WHITE;
    private static final Color DETAIL_COLOR = new Color(160, 160, 166);
    private static final String ELLIPSIS = "…";

    private BuddyBubbleContent content;
    private boolean highlighted;

    BuddyBubblePanel() {
        setOpaque(false);
    }

    static Font titleFont() { return font(Font.BOLD, 15f); }

    static Font detailFont() { return font(Font.PLAIN, 14f); }

    private static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        return base != null ? base.deriveFont(style, size) : new Font(Font.DIALOG, style, (int) size);
    }

    void setContent(BuddyBubbleContent content) {
        this.content = content;
        revalidate();
        repaint();
    }

    BuddyBubbleContent content() { return content; }

    void setHighlighted(boolean highlighted) {
        if (this.highlighted == highlighted) return;
        this.highlighted = highlighted;
        repaint();
    }

    boolean isHighlighted() { return highlighted; }

    @Override public Dimension getPreferredSize() {
        if (content == null) return new Dimension(MIN_WIDTH, 2 * PAD_Y);
        FontMetrics title = getFontMetrics(titleFont());
        int textWidth = title.stringWidth(content.title());
        int textHeight = title.getHeight();
        if (content.detail() != null) {
            FontMetrics detail = getFontMetrics(detailFont());
            textWidth = Math.max(textWidth, detail.stringWidth(content.detail()));
            textHeight += LINE_GAP + detail.getHeight();
        }
        int width = textWidth + 2 * PAD_X + glyphSpace();
        int height = Math.max(textHeight, glyphHeight()) + 2 * PAD_Y;
        return new Dimension(Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width)), height);
    }

    private int glyphSpace() {
        Icon glyph = content == null ? null : content.glyph();
        return glyph == null ? 0 : glyph.getIconWidth() + GLYPH_GAP;
    }

    private int glyphHeight() {
        Icon glyph = content == null ? null : content.glyph();
        return glyph == null ? 0 : glyph.getIconHeight();
    }

    @Override protected void paintComponent(Graphics g) {
        if (content == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = getWidth(), height = getHeight();
            g2.setColor(highlighted ? FILL_HIGHLIGHTED : FILL);
            g2.fillRoundRect(0, 0, width, height, RADIUS * 2, RADIUS * 2);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, width - 1, height - 1, RADIUS * 2, RADIUS * 2);

            Icon glyph = content.glyph();
            if (glyph != null) {
                glyph.paintIcon(this, g2, width - PAD_X - glyph.getIconWidth(),
                    (height - glyph.getIconHeight()) / 2);
            }
            int available = Math.max(0, width - 2 * PAD_X - glyphSpace());
            FontMetrics title = getFontMetrics(titleFont());
            int textHeight = title.getHeight();
            FontMetrics detail = content.detail() == null ? null : getFontMetrics(detailFont());
            if (detail != null) textHeight += LINE_GAP + detail.getHeight();
            int y = (height - textHeight) / 2;
            g2.setFont(titleFont());
            g2.setColor(TITLE_COLOR);
            g2.drawString(fit(title, content.title(), available), PAD_X, y + title.getAscent());
            if (detail != null) {
                g2.setFont(detailFont());
                g2.setColor(DETAIL_COLOR);
                g2.drawString(fit(detail, content.detail(), available),
                    PAD_X, y + title.getHeight() + LINE_GAP + detail.getAscent());
            }
        } finally { g2.dispose(); }
    }

    /** Truncates with an ellipsis instead of wrapping; the bubble is one line tall per text line. */
    private static String fit(FontMetrics metrics, String text, int available) {
        if (metrics.stringWidth(text) <= available) return text;
        int ellipsis = metrics.stringWidth(ELLIPSIS);
        int end = text.length();
        while (end > 0 && ellipsis + metrics.stringWidth(text.substring(0, end)) > available) end--;
        return text.substring(0, end) + ELLIPSIS;
    }
}
