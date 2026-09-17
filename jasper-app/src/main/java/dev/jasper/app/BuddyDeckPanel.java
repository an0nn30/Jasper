package dev.jasper.app;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Paints the drawer and decides what a click landed on. Every decision it makes comes from
 * {@link BuddyDeckLayout} or {@link BubbleMotion}, so this class holds only the state a component
 * must hold — expanded, scrolled, hovered, and when the top card arrived.
 *
 * <p>Input arrives through the package-private handlers, the way {@code TerminalView} takes keys and
 * mouse events, so the tests drive it without a visible window or a real pointer.
 */
final class BuddyDeckPanel extends JComponent {
    static final int CARD_WIDTH = 300;
    /**
     * Slack around the cards so one at its largest scale is not clipped by its own window. A card
     * grows about its centre-left, so the widest overshoot is half the extra width.
     */
    static final int MOTION_MARGIN = 10;
    static final int PAD_X = 14;
    static final int PAD_Y = 10;
    static final int LINE_GAP = 3;
    static final int GLYPH_SIZE = 12;
    static final int GLYPH_GAP = 10;
    static final int WHEEL_STEP = 24;
    private static final String ELLIPSIS = "…";
    private static final String DISMISS = "×";
    private static final String CLEAR_ALL = "Clear all";

    private static final Color FILL = new Color(30, 30, 32, 235);
    private static final Color FILL_HIGHLIGHTED = new Color(58, 58, 62, 242);
    private static final Color BORDER = new Color(255, 255, 255, 26);
    private static final Color TITLE_COLOR = Color.WHITE;
    private static final Color DETAIL_COLOR = new Color(160, 160, 166);
    private static final Color ORPHAN_TITLE_COLOR = new Color(255, 255, 255, 140);
    private static final Color COUNT_COLOR = new Color(190, 190, 196);

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private LongSupplier clock = System::nanoTime;
    /**
     * How tall the expanded list may grow. Supplied by the window, which knows the screen: querying
     * the screen from here would throw in the headless test JVM and would put a device lookup inside
     * {@code getPreferredSize}.
     */
    private IntSupplier maxListHeight = () -> Integer.MAX_VALUE / 4;
    private boolean expanded;
    private int scroll;
    private int hovered = -1;
    /** The card the pointer just left, so only that one animates back rather than every card. */
    private int leaving = -1;
    private long topArrivedAt;
    private long hoverChangedAt;

    BuddyDeckPanel(BuddyDeck deck, Runnable onLayoutChanged) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.onLayoutChanged = Objects.requireNonNull(onLayoutChanged, "onLayoutChanged");
        setOpaque(false);
    }

    /** Tests advance time instead of sleeping; the window leaves this as {@code System::nanoTime}. */
    void setClock(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    void setMaxListHeight(IntSupplier maxListHeight) {
        this.maxListHeight = Objects.requireNonNull(maxListHeight, "maxListHeight");
    }

    /** A notice was posted: the top card bounces in and the window re-sizes around the new stack. */
    void arrived() {
        topArrivedAt = clock.getAsLong();
        changed();
    }

    boolean expanded() { return expanded; }

    int scroll() { return scroll; }

    int hovered() { return hovered; }

    void collapse() {
        if (!expanded && scroll == 0 && hovered < 0) return;
        expanded = false;
        scroll = 0;
        hovered = -1;
        leaving = -1;
        changed();
    }

    /** The scale of the newest card right now, from its arrival bounce alone. */
    float topScale() {
        return BubbleMotion.inScale(clock.getAsLong() - topArrivedAt);
    }

    int cardHeight() {
        FontMetrics title = getFontMetrics(titleFont());
        FontMetrics detail = getFontMetrics(detailFont());
        return Math.max(title.getHeight() + LINE_GAP + detail.getHeight(), GLYPH_SIZE) + 2 * PAD_Y;
    }

    @Override public Dimension getPreferredSize() {
        int count = deck.size();
        if (count == 0) return new Dimension(0, 0);
        int height = expanded
            ? Math.min(BuddyDeckLayout.expandedHeight(count, cardHeight()), Math.max(cardHeight(), maxListHeight.getAsInt()))
            : BuddyDeckLayout.collapsedHeight(count, cardHeight());
        return new Dimension(CARD_WIDTH + 2 * MOTION_MARGIN, height + 2 * MOTION_MARGIN);
    }

    private int viewportHeight() { return Math.max(0, getHeight() - 2 * MOTION_MARGIN); }

    private int contentHeight() { return BuddyDeckLayout.expandedHeight(deck.size(), cardHeight()); }

    // --- input -------------------------------------------------------------------------------

    void handleMove(Point point) {
        int index = indexAt(point);
        if (index == hovered) return;
        leaving = hovered;
        hovered = index;
        hoverChangedAt = clock.getAsLong();
        repaint();
    }

    void handleExit() {
        leaving = hovered;
        hovered = -1;
        hoverChangedAt = clock.getAsLong();
        collapse();
    }

    void handleWheel(int units) {
        if (!expanded) return;
        int next = BuddyDeckLayout.clampScroll(scroll + units * WHEEL_STEP, contentHeight(), viewportHeight());
        if (next == scroll) return;
        scroll = next;
        repaint();
    }

    /**
     * Returns false when the drawer should now be hidden — it was emptied, or a card was activated
     * and the window it points at is about to come forward.
     */
    boolean handleClick(Point point) {
        if (deck.isEmpty()) return false;
        if (!expanded) {
            expanded = true;
            scroll = 0;
            hovered = -1;
            leaving = -1;
            changed();
            return true;
        }
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN);
        Rectangle clear = BuddyDeckLayout.clearRow(deck.size(), CARD_WIDTH, cardHeight(), scroll);
        if (clear != null && clear.contains(local)) {
            deck.clear();
            collapse();
            return false;
        }
        int index = BuddyDeckLayout.cardAt(local.y, deck.size(), cardHeight(), scroll);
        if (index < 0) return true;
        List<BuddyNotice> notices = deck.notices();
        BuddyNotice notice = notices.get(index);
        Rectangle card = BuddyDeckLayout.expanded(index, CARD_WIDTH, cardHeight(), scroll);
        if (BuddyDeckLayout.dismissTarget(card).contains(local)) {
            deck.dismiss(notice.source(), notice.key());
            hovered = -1;
            leaving = -1;
            if (deck.isEmpty()) { collapse(); return false; }
            scroll = BuddyDeckLayout.clampScroll(scroll, contentHeight(), viewportHeight());
            changed();
            return true;
        }
        // An orphan has nowhere to go, so a click on it is not a miss - it is simply nothing.
        if (notice.orphaned()) return true;
        collapse();
        notice.activate().run();
        return false;
    }

    /** The card index under a point, or -1; collapsed, only the top card is a target. */
    private int indexAt(Point point) {
        if (deck.isEmpty()) return -1;
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN);
        if (!expanded) {
            return new Rectangle(0, 0, CARD_WIDTH, cardHeight()).contains(local) ? 0 : -1;
        }
        return BuddyDeckLayout.cardAt(local.y, deck.size(), cardHeight(), scroll);
    }

    private void changed() {
        revalidate();
        repaint();
        onLayoutChanged.run();
    }

    // --- painting ----------------------------------------------------------------------------

    @Override protected void paintComponent(Graphics g) {
        List<BuddyNotice> notices = deck.notices();
        if (notices.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(MOTION_MARGIN, MOTION_MARGIN);
            if (expanded) paintExpanded(g2, notices); else paintCollapsed(g2, notices);
        } finally { g2.dispose(); }
    }

    /** Deepest first, so the newest card lands on top of the ones peeking out behind it. */
    private void paintCollapsed(Graphics2D g2, List<BuddyNotice> notices) {
        int height = cardHeight();
        int drawn = Math.min(notices.size(), BuddyDeckLayout.MAX_PEEKED);
        // Backing cards are shape only. The top card's fill is translucent, so their text would read
        // straight through it - which no assertion here caught and one look at a render did.
        for (int index = drawn - 1; index >= 1; index--) {
            paintBacking(g2, BuddyDeckLayout.collapsed(index, CARD_WIDTH, height));
        }
        Rectangle top = BuddyDeckLayout.collapsed(0, CARD_WIDTH, height);
        int count = notices.size() > BuddyDeckLayout.MAX_PEEKED ? notices.size() : 0;
        paintCard(g2, notices.getFirst(), top, hovered == 0, topScale() * hoverScale(0),
            BubbleMotion.inOpacity(clock.getAsLong() - topArrivedAt), count, false);
    }

    /** The suggestion of a card under the top one: its edge, and nothing that could read through. */
    private void paintBacking(Graphics2D g2, Rectangle card) {
        int arc = BuddyBubblePanel.radiusFor(card.height) * 2;
        g2.setColor(FILL);
        g2.fillRoundRect(card.x, card.y, card.width, card.height, arc, arc);
        g2.setColor(BORDER);
        g2.drawRoundRect(card.x, card.y, card.width - 1, card.height - 1, arc, arc);
    }

    private void paintExpanded(Graphics2D g2, List<BuddyNotice> notices) {
        int height = cardHeight();
        g2.setClip(-MOTION_MARGIN, -MOTION_MARGIN, CARD_WIDTH + 2 * MOTION_MARGIN, getHeight());
        for (int index = 0; index < notices.size(); index++) {
            Rectangle card = BuddyDeckLayout.expanded(index, CARD_WIDTH, height, scroll);
            if (card.y + card.height < 0 || card.y > getHeight()) continue;
            paintCard(g2, notices.get(index), card, hovered == index, hoverScale(index), 1f, 0, true);
        }
        Rectangle clear = BuddyDeckLayout.clearRow(notices.size(), CARD_WIDTH, height, scroll);
        if (clear == null) return;
        g2.setFont(detailFont());
        FontMetrics metrics = g2.getFontMetrics();
        g2.setColor(COUNT_COLOR);
        g2.drawString(CLEAR_ALL, clear.x + CARD_WIDTH - PAD_X - metrics.stringWidth(CLEAR_ALL),
            clear.y + (clear.height - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    /** Only the hovered card grows, and only the one just left animates back. */
    private float hoverScale(int index) {
        long elapsed = clock.getAsLong() - hoverChangedAt;
        if (index == hovered) return BubbleMotion.hoverScale(elapsed, true);
        if (index == leaving) return BubbleMotion.hoverScale(elapsed, false);
        return 1f;
    }

    /**
     * One card. {@code count}, when non-zero, is drawn as a remainder badge on the collapsed top
     * card; {@code dismissable} adds the × the expanded list hit-tests.
     */
    private void paintCard(Graphics2D g2, BuddyNotice notice, Rectangle card, boolean highlighted,
                           float scale, float opacity, int count, boolean dismissable) {
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.min(1f, Math.max(0f, opacity))));
            // Grow about the card's centre-left, so it expands away from the buddy rather than over him.
            c.translate(card.x, card.y + card.height / 2f);
            c.scale(scale, scale);
            c.translate(0, -card.height / 2f);
            int arc = BuddyBubblePanel.radiusFor(card.height) * 2;
            c.setColor(highlighted ? FILL_HIGHLIGHTED : FILL);
            c.fillRoundRect(0, 0, card.width, card.height, arc, arc);
            c.setColor(BORDER);
            c.drawRoundRect(0, 0, card.width - 1, card.height - 1, arc, arc);

            int right = card.width - PAD_X;
            c.setFont(detailFont());
            FontMetrics small = c.getFontMetrics();
            if (dismissable) {
                c.setColor(highlighted ? COUNT_COLOR : DETAIL_COLOR);
                c.drawString(DISMISS, card.width - BuddyDeckLayout.DISMISS_INSET
                        - BuddyDeckLayout.DISMISS_SIZE + 2,
                    BuddyDeckLayout.DISMISS_INSET + small.getAscent());
                right = card.width - BuddyDeckLayout.DISMISS_INSET - BuddyDeckLayout.DISMISS_SIZE - 4;
            } else if (count > 0) {
                String badge = Integer.toString(count);
                c.setColor(COUNT_COLOR);
                c.drawString(badge, card.width - PAD_X - small.stringWidth(badge),
                    card.height - PAD_Y - small.getHeight() + small.getAscent());
                right = card.width - PAD_X - small.stringWidth(badge) - GLYPH_GAP;
            }
            if (notice.state() != BuddyNotice.State.ACTIVE) {
                paintGlyph(c, notice.state() == BuddyNotice.State.DONE,
                    right - GLYPH_SIZE, (card.height - GLYPH_SIZE) / 2);
                right -= GLYPH_SIZE + GLYPH_GAP;
            }

            int available = Math.max(0, right - PAD_X);
            FontMetrics title = c.getFontMetrics(titleFont());
            int textHeight = title.getHeight() + LINE_GAP + small.getHeight();
            int y = (card.height - textHeight) / 2;
            c.setFont(titleFont());
            c.setColor(notice.orphaned() ? ORPHAN_TITLE_COLOR : TITLE_COLOR);
            c.drawString(fit(title, notice.title(), available), PAD_X, y + title.getAscent());
            c.setFont(detailFont());
            c.setColor(DETAIL_COLOR);
            c.drawString(fit(small, text(notice), available),
                PAD_X, y + title.getHeight() + LINE_GAP + small.getAscent());
        } finally { c.dispose(); }
    }

    /** A producer's supplier is arbitrary code; one that throws must not take the drawer down. */
    private static String text(BuddyNotice notice) {
        try {
            String detail = notice.detail().get();
            return detail == null ? "" : detail;
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /** A check or a cross in the colours the status bar already uses for success and failure. */
    private static void paintGlyph(Graphics2D g2, boolean succeeded, int x, int y) {
        Color configured = UIManager.getColor(
            succeeded ? "Jasper.configSuccessForeground" : "Jasper.configErrorForeground");
        Color ink = configured != null ? configured
            : succeeded ? new Color(0x5A, 0xB0, 0x7A) : new Color(0xC4, 0x5A, 0x53);
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setColor(ink);
            c.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (succeeded) {
                c.drawLine(x + 2, y + 6, x + 5, y + 9);
                c.drawLine(x + 5, y + 9, x + 10, y + 3);
            } else {
                c.drawLine(x + 3, y + 3, x + 9, y + 9);
                c.drawLine(x + 9, y + 3, x + 3, y + 9);
            }
        } finally { c.dispose(); }
    }

    private static Font titleFont() { return BuddyFonts.system(Font.BOLD, 14f); }

    private static Font detailFont() { return BuddyFonts.system(Font.PLAIN, 12f); }

    /** Truncates with an ellipsis instead of wrapping; a card is one line tall per text line. */
    private static String fit(FontMetrics metrics, String text, int available) {
        if (metrics.stringWidth(text) <= available) return text;
        int ellipsis = metrics.stringWidth(ELLIPSIS);
        int end = text.length();
        while (end > 0 && ellipsis + metrics.stringWidth(text.substring(0, end)) > available) end--;
        return text.substring(0, end) + ELLIPSIS;
    }
}
