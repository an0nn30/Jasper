package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.swing.JComponent;

/**
 * What the buddy is thinking about: the live and unseen notices, stacked above his head with a
 * thought tail pointing back at him. Empty and invisible whenever nothing is happening, which is
 * most of the time and is the point — a permanent stack of finished commands is noise.
 *
 * <p>Input arrives through the package-private handlers, the way {@code TerminalView} takes keys, so
 * the tests drive it without a window or a pointer.
 */
final class BuddyColumnPanel extends JComponent {
    /** Slack so a bubble at its largest scale is not clipped by its own window. */
    static final int MARGIN = 10;
    private static final Color TAIL = new Color(38, 38, 41, 240);
    private static final Color TAIL_BORDER = new Color(255, 255, 255, 46);

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private final Runnable onOpenDrawer;
    private LongSupplier clock = System::nanoTime;
    private boolean below;
    private int hovered = -1;
    private int leaving = -1;
    private long topArrivedAt;
    private long hoverChangedAt;
    /** When the bubbles above the newest started sliding up over its place. */
    private long settleStartedAt;
    /** Zero until something has arrived, so an idle column does not claim to be mid-animation. */
    private boolean everArrived;
    /** The deck generation this panel has already played an arrival for. */
    private int drawnGeneration;

    BuddyColumnPanel(BuddyDeck deck, Runnable onLayoutChanged, Runnable onOpenDrawer) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.onLayoutChanged = Objects.requireNonNull(onLayoutChanged, "onLayoutChanged");
        this.onOpenDrawer = Objects.requireNonNull(onOpenDrawer, "onOpenDrawer");
        setOpaque(false);
    }

    void setClock(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /** Flipped when he is too near the top of the screen for the column to fit above him. */
    void setBelow(boolean below) {
        if (this.below == below) return;
        this.below = below;
        hovered = -1;
        leaving = -1;
        repaint();
    }

    boolean below() { return below; }

    /**
     * The deck changed. If something was genuinely posted the nearest bubble springs in and the ones
     * above it slide up over it; anything else — a dismissal, an acknowledgement — just re-lays out.
     *
     * <p>The panel works this out from the deck rather than being told, because the version that had
     * to be told had no production caller at all: the bubbles appeared instantly at full size while
     * a test that called the method directly passed.
     */
    void refresh() {
        if (deck.generation() != drawnGeneration) {
            drawnGeneration = deck.generation();
            topArrivedAt = clock.getAsLong();
            settleStartedAt = topArrivedAt;
            everArrived = true;
        }
        revalidate();
        repaint();
        onLayoutChanged.run();
    }

    /**
     * Whether anything is still moving. The window drives repaints while this is true and stops when
     * it is not, so a settled column costs nothing — which is why nothing here sways forever.
     */
    boolean animating() {
        long now = clock.getAsLong();
        if (everArrived && now - topArrivedAt < BubbleMotion.IN_NANOS) return true;
        if (everArrived && now - settleStartedAt < BubbleMotion.SETTLE_NANOS) return true;
        return (hovered >= 0 || leaving >= 0) && now - hoverChangedAt < BubbleMotion.HOVER_NANOS;
    }

    int hovered() { return hovered; }

    float topScale() { return BubbleMotion.inScale(clock.getAsLong() - topArrivedAt); }

    @Override public Dimension getPreferredSize() {
        int count = deck.column().size();
        if (count == 0) return new Dimension(0, 0);
        return new Dimension(BuddyCard.WIDTH + 2 * MARGIN,
            BuddyDeckLayout.columnHeight(count, BuddyCard.height(this)) + 2 * MARGIN);
    }

    // --- input -------------------------------------------------------------------------------

    void handleMove(Point point) {
        int index = indexAt(point);
        if (index == hovered) return;
        leaving = hovered;
        hovered = index;
        hoverChangedAt = clock.getAsLong();
        repaint();
        onLayoutChanged.run();
    }

    void handleExit() {
        if (hovered < 0) return;
        leaving = hovered;
        hovered = -1;
        hoverChangedAt = clock.getAsLong();
        repaint();
        onLayoutChanged.run();
    }

    /**
     * Returns false when the column should now go away, because a bubble was activated and the pane
     * it points at is about to come forward. A click that is not on a bubble opens the drawer.
     */
    boolean handleClick(Point point) {
        List<BuddyNotice> column = deck.column();
        if (column.isEmpty()) return true;
        int index = indexAt(point);
        if (index < 0) {
            onOpenDrawer.run();
            return true;
        }
        BuddyNotice notice = column.get(index);
        // An orphan has nowhere to go, so a click on it is not a miss - it is simply nothing.
        if (notice.orphaned()) return true;
        notice.activate().run();
        return false;
    }

    private int indexAt(Point point) {
        int count = deck.column().size();
        if (count == 0) return -1;
        Point local = new Point(point.x - MARGIN, point.y - MARGIN);
        if (local.x < 0 || local.x > BuddyCard.WIDTH) return -1;
        return BuddyDeckLayout.columnAt(local.y, count, BuddyCard.height(this), below);
    }

    // --- painting ----------------------------------------------------------------------------

    @Override protected void paintComponent(Graphics g) {
        List<BuddyNotice> column = deck.column();
        if (column.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(MARGIN, MARGIN);
            int cardHeight = BuddyCard.height(this);
            int height = BuddyDeckLayout.columnHeight(column.size(), cardHeight);
            paintTail(g2, height);
            int visible = BuddyDeckLayout.visibleInColumn(column.size());
            // Furthest first, so the newest lands on top of anything that overlaps it.
            // Everything except the newest starts one pitch away and springs into place over it.
            float settle = everArrived
                ? BubbleMotion.settleOffset(clock.getAsLong() - settleStartedAt, cardHeight + BuddyDeckLayout.COLUMN_GAP)
                : 0f;
            for (int index = visible - 1; index >= 0; index--) {
                Rectangle card = BuddyDeckLayout.column(index, column.size(), BuddyCard.WIDTH, cardHeight, below);
                boolean nearest = index == 0;
                if (!nearest) card.y += (int) (below ? -settle : settle);
                float scale = (nearest ? topScale() : 1f) * hoverScale(index);
                float opacity = nearest ? BubbleMotion.inOpacity(clock.getAsLong() - topArrivedAt) : 1f;
                int count = nearest && column.size() > visible ? column.size() : 0;
                BuddyCard.paint(g2, this, column.get(index), card, hovered == index, scale, opacity,
                    false, count);
            }
        } finally { g2.dispose(); }
    }

    private void paintTail(Graphics2D g2, int columnHeight) {
        for (Rectangle circle : BuddyDeckLayout.tail(BuddyCard.WIDTH, columnHeight, below)) {
            g2.setColor(TAIL);
            g2.fillOval(circle.x, circle.y, circle.width, circle.height);
            g2.setColor(TAIL_BORDER);
            g2.drawOval(circle.x, circle.y, circle.width - 1, circle.height - 1);
        }
    }

    /** Only the hovered bubble grows, and only the one just left animates back. */
    private float hoverScale(int index) {
        long elapsed = clock.getAsLong() - hoverChangedAt;
        if (index == hovered) return BubbleMotion.hoverScale(elapsed, true);
        if (index == leaving) return BubbleMotion.hoverScale(elapsed, false);
        return 1f;
    }
}
