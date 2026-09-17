package dev.jasper.app;


import java.awt.Dimension;
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

/**
 * The drawer: everything that happened this run, newest first, on demand. The column above his head
 * is the surface you watch; this is the one you go and ask. Every decision it makes comes from
 * {@link BuddyDeckLayout}, and every card is drawn by {@link BuddyCard}, so the two surfaces cannot
 * drift apart.
 *
 * <p>Input arrives through the package-private handlers, the way {@code TerminalView} takes keys, so
 * the tests drive it without a visible window or a real pointer.
 */
final class BuddyDeckPanel extends JComponent {
    /** Slack so a card at its largest scale is not clipped by its own window. */
    static final int MOTION_MARGIN = 10;
    static final int WHEEL_STEP = 24;
    private static final String CLEAR_ALL = "Clear all";

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private LongSupplier clock = System::nanoTime;
    /**
     * How tall the list may grow. Supplied by the window, which knows the screen: querying a
     * graphics device from here would throw in the headless test JVM.
     */
    private IntSupplier maxListHeight = () -> Integer.MAX_VALUE / 4;
    private int scroll;
    private int hovered = -1;
    /** The card the pointer just left, so only that one animates back rather than every card. */
    private int leaving = -1;
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

    int scroll() { return scroll; }

    int hovered() { return hovered; }

    int cardHeight() { return BuddyCard.height(this); }

    /** The drawer was closed: forget where you were, so it reopens at the top. */
    void reset() {
        if (scroll == 0 && hovered < 0) return;
        scroll = 0;
        hovered = -1;
        leaving = -1;
        changed();
    }

    /** A notice was posted, dismissed or cleared: re-size around the new list. */
    void refresh() { changed(); }

    @Override public Dimension getPreferredSize() {
        int count = deck.size();
        if (count == 0) return new Dimension(0, 0);
        int height = Math.min(BuddyDeckLayout.expandedHeight(count, cardHeight()),
            Math.max(cardHeight(), maxListHeight.getAsInt()));
        return new Dimension(BuddyCard.WIDTH + 2 * MOTION_MARGIN, height + 2 * MOTION_MARGIN);
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
        if (hovered < 0) return;
        leaving = hovered;
        hovered = -1;
        hoverChangedAt = clock.getAsLong();
        repaint();
    }

    void handleWheel(int units) {
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
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN);
        Rectangle clear = BuddyDeckLayout.clearRow(deck.size(), BuddyCard.WIDTH, cardHeight(), scroll);
        if (clear != null && clear.contains(local)) {
            deck.clear();
            reset();
            return false;
        }
        int index = BuddyDeckLayout.cardAt(local.y, deck.size(), cardHeight(), scroll);
        if (index < 0) return true;
        List<BuddyNotice> notices = deck.notices();
        BuddyNotice notice = notices.get(index);
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyCard.WIDTH, cardHeight(), scroll);
        if (BuddyDeckLayout.dismissTarget(card).contains(local)) {
            deck.dismiss(notice.source(), notice.key());
            hovered = -1;
            leaving = -1;
            if (deck.isEmpty()) { reset(); return false; }
            scroll = BuddyDeckLayout.clampScroll(scroll, contentHeight(), viewportHeight());
            changed();
            return true;
        }
        // An orphan has nowhere to go, so a click on it is not a miss - it is simply nothing.
        if (notice.orphaned()) return true;
        notice.activate().run();
        return false;
    }

    private int indexAt(Point point) {
        if (deck.isEmpty()) return -1;
        return BuddyDeckLayout.cardAt(point.y - MOTION_MARGIN, deck.size(), cardHeight(), scroll);
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
            g2.setClip(-MOTION_MARGIN, -MOTION_MARGIN, BuddyCard.WIDTH + 2 * MOTION_MARGIN, getHeight());
            int height = cardHeight();
            for (int index = 0; index < notices.size(); index++) {
                Rectangle card = BuddyDeckLayout.expanded(index, BuddyCard.WIDTH, height, scroll);
                if (card.y + card.height < 0 || card.y > getHeight()) continue;
                BuddyCard.paint(g2, this, notices.get(index), card, hovered == index,
                    hoverScale(index), 1f, true, 0);
            }
            Rectangle clear = BuddyDeckLayout.clearRow(notices.size(), BuddyCard.WIDTH, height, scroll);
            if (clear == null) return;
            g2.setFont(BuddyCard.detailFont());
            FontMetrics metrics = g2.getFontMetrics();
            g2.setColor(BuddyCard.MUTED_COLOR);
            g2.drawString(CLEAR_ALL, clear.x + BuddyCard.WIDTH - BuddyCard.PAD_X - metrics.stringWidth(CLEAR_ALL),
                clear.y + (clear.height - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally { g2.dispose(); }
    }

    /** Only the hovered card grows, and only the one just left animates back. */
    private float hoverScale(int index) {
        long elapsed = clock.getAsLong() - hoverChangedAt;
        if (index == hovered) return BubbleMotion.hoverScale(elapsed, true);
        if (index == leaving) return BubbleMotion.hoverScale(elapsed, false);
        return 1f;
    }
}
