package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.internal.animation.BubbleSpring;
import dev.jasper.buddy.internal.animation.BubbleMotion;
import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.swing.JComponent;

/** Live capsules above the buddy. Motion is keyed by notice identity, never by list position. */
final class BuddyColumnPanel extends JComponent {
    private BuddyOptions options;

    void applyOptions(BuddyOptions value) {
        options = java.util.Objects.requireNonNull(value);
        revalidate(); repaint();
    }

    static final int MARGIN = BuddyCard.SHADOW_MARGIN;
    static final int ARRIVAL_ROOM = (int) BubbleMotion.IN_TRAVEL;

    private record Motion(BuddyNotice notice, float from, float to, long started) {
        float at(long now) { return to + (from - to) * BubbleMotion.remaining(now - started); }
        boolean active(long now) { return from != to && now - started < BubbleMotion.IN_NANOS; }
    }

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private final Runnable onOpenDrawer;
    private LongSupplier clock = System::nanoTime;
    private List<Motion> motions = List.of();
    private boolean below;
    private final BubbleSpring direction = new BubbleSpring(0);
    private int hovered = -1;
    private int leaving = -1;
    private long hoverChangedAt;
    private boolean lastFrameMoving = true;

    BuddyColumnPanel(BuddyOptions options, BuddyDeck deck, Runnable onLayoutChanged, Runnable onOpenDrawer) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.onLayoutChanged = Objects.requireNonNull(onLayoutChanged, "onLayoutChanged");
        this.onOpenDrawer = Objects.requireNonNull(onOpenDrawer, "onOpenDrawer");
        this.options = java.util.Objects.requireNonNull(options);
        setOpaque(false);
    }

    void setClock(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    void setBelow(boolean below) { setBelow(below, false); }

    void setBelow(boolean below, boolean snap) {
        direction.target(below ? 1 : 0, clock.getAsLong(), snap);
        if (this.below == below) return;
        this.below = below;
        hovered = -1;
        leaving = -1;
        repaint();
    }

    boolean below() { return below; }

    /** Sample interrupted motion before retargeting, keeping existing capsules continuous. */
    void refresh() {
        long now = clock.getAsLong();
        List<BuddyNotice> column = deck.column();
        List<Motion> next = new ArrayList<>();
        for (int i = 0; i < BuddyDeckLayout.visibleInColumn(column.size()); i++) {
            BuddyNotice notice = column.get(i);
            float target = i * (BuddyCard.HEIGHT + BuddyDeckLayout.COLUMN_GAP);
            Motion old = motionFor(notice);
            if (old != null && old.to == target) next.add(new Motion(notice, old.from, old.to, old.started));
            else next.add(new Motion(notice, old == null ? target + BubbleMotion.IN_TRAVEL : old.at(now), target, now));
        }
        if (motions.size() != next.size() || !sameOrder(motions, next)) {
            hovered = -1;
            leaving = -1;
        }
        motions = List.copyOf(next);
        revalidate();
        repaint();
        onLayoutChanged.run();
    }

    private static boolean sameOrder(List<Motion> first, List<Motion> second) {
        for (int i = 0; i < first.size(); i++)
            if (!first.get(i).notice.sameAs(second.get(i).notice.id())) return false;
        return true;
    }

    private Motion motionFor(BuddyNotice notice) {
        for (Motion motion : motions) if (motion.notice.sameAs(notice.id())) return motion;
        return null;
    }

    boolean animating() {
        long now = clock.getAsLong();
        return direction.moving(now) || motions.stream().anyMatch(m -> m.active(now))
            || (hovered >= 0 || leaving >= 0) && now - hoverChangedAt < BubbleMotion.HOVER_NANOS;
    }

    boolean needsAnimationFrames() { return animating() || deck.column().stream().anyMatch(BuddyCard::shimmers); }

    boolean needsDetailUpdates() { return deck.column().stream().anyMatch(BuddyNotice::live); }

    /** Settled shimmer changes only the subtext, not the full translucent shadow surface. */
    void repaintFrame() {
        boolean moving = animating();
        if (moving || lastFrameMoving) repaint();
        else {
            List<BuddyNotice> column = deck.column();
            for (int i = 0; i < BuddyDeckLayout.visibleInColumn(column.size()); i++) {
                if (!column.get(i).live()) continue;
                Rectangle bounds = cardBounds(i);
                repaint(bounds.x + BuddyCard.PAD_X, bounds.y + 28,
                    bounds.width - 2 * BuddyCard.PAD_X, 18);
            }
        }
        lastFrameMoving = moving;
    }

    int hovered() { return hovered; }

    @Override public Dimension getPreferredSize() {
        int count = deck.column().size();
        if (count == 0) return new Dimension(0, 0);
        return new Dimension(BuddyCard.WIDTH + 2 * MARGIN,
            BuddyDeckLayout.columnHeight(count, BuddyCard.HEIGHT) + 2 * MARGIN + ARRIVAL_ROOM);
    }

    /** Actual painted rectangle, used for input too while a capsule is moving. */
    Rectangle cardBounds(int index) {
        List<BuddyNotice> column = deck.column();
        Rectangle card = BuddyDeckLayout.column(index, column.size(), BuddyCard.WIDTH, BuddyCard.HEIGHT, false);
        Motion motion = motionFor(column.get(index));
        float target = index * (BuddyCard.HEIGHT + BuddyDeckLayout.COLUMN_GAP);
        float offset = motion == null ? 0 : motion.at(clock.getAsLong()) - target;
        double blend = direction.at(clock.getAsLong());
        int belowY = BuddyDeckLayout.column(index, column.size(), BuddyCard.WIDTH, BuddyCard.HEIGHT, true).y;
        double top = card.y + ARRIVAL_ROOM;
        card.setLocation(MARGIN, MARGIN + (int) Math.round(top + (belowY - top) * blend + (2 * blend - 1) * offset));
        return card;
    }

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

    boolean handleClick(Point point) {
        List<BuddyNotice> column = deck.column();
        if (column.isEmpty()) return true;
        int index = indexAt(point);
        if (index < 0) {
            onOpenDrawer.run();
            return true;
        }
        BuddyNotice notice = column.get(index);
        if (notice.orphaned()) return true;
        notice.activate().run();
        return false;
    }

    private int indexAt(Point point) {
        for (int i = 0; i < BuddyDeckLayout.visibleInColumn(deck.column().size()); i++) {
            Rectangle card = cardBounds(i);
            if (new java.awt.geom.RoundRectangle2D.Float(card.x, card.y, card.width, card.height,
                card.height, card.height).contains(point)) return i;
        }
        return -1;
    }

    @Override protected void paintComponent(Graphics g) {
        List<BuddyNotice> column = deck.column();
        if (column.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int visible = BuddyDeckLayout.visibleInColumn(column.size());
            for (int index = visible - 1; index >= 0; index--) {
                int count = index == 0 && column.size() > visible ? column.size() : 0;
                Rectangle bounds = cardBounds(index);
                Motion motion = motionFor(column.get(index));
                float offset = motion == null ? 0 : motion.at(clock.getAsLong()) - motion.to;
                float signed = (float) ((2 * direction.at(clock.getAsLong()) - 1) * offset);
                Graphics2D card = (Graphics2D) g2.create();
                try {
                    // Keep subpixel motion when the recording advances by half a logical pixel.
                    card.translate(0, signed - Math.round(signed));
                    BuddyCard.paint(options, card, this, column.get(index), bounds, hoverAmount(index), 1f, false, count, clock.getAsLong());
                } finally { card.dispose(); }
            }
        } finally { g2.dispose(); }
    }

    private float hoverAmount(int index) {
        long elapsed = clock.getAsLong() - hoverChangedAt;
        if (index == hovered) return BubbleMotion.hover(elapsed, true);
        if (index == leaving) return BubbleMotion.hover(elapsed, false);
        return 0f;
    }
}
