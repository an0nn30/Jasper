package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.internal.animation.BubbleMotion;
import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.notice.BuddyNotice;


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
    private BuddyOptions options;

    void applyOptions(BuddyOptions value) {
        options = java.util.Objects.requireNonNull(value);
        revalidate(); repaint();
    }

    /** Space for the soft capsule shadow. */
    static final int MOTION_MARGIN = BuddyCard.SHADOW_MARGIN;
    static final int WHEEL_STEP = 24;
    private static final String CLEAR_ALL = "Clear all";

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private final Runnable onNoticesChanged;
    private LongSupplier clock = System::nanoTime;
    /**
     * How tall the list may grow. Supplied by the window, which knows the screen: querying a
     * graphics device from here would throw in the headless test JVM.
     */
    private IntSupplier maxListHeight = () -> Integer.MAX_VALUE / 4;
    private BuddyNoticeId groupFilter;
    private final java.util.Map<BuddyNoticeId, Integer> discriminators = new java.util.LinkedHashMap<>();
    private int nextDiscriminator;
    private final java.util.Set<BuddyNoticeId> duplicateIds = new java.util.HashSet<>();
    void setGroupFilter(BuddyNoticeId filter) { groupFilter = filter; reset(); changed(); }
    int headerHeight() { return groupFilter == null ? 0 : 28; }
    String header() {
        return deck.notices().stream().filter(n -> n.group() != null && n.group().id().equals(groupFilter))
            .map(n -> n.group().title()).findFirst().orElse("");
    }
    List<BuddyNotice> displayed() {
        List<BuddyNotice> visible = deck.notices();
        var retained = deck.retainedNotices();
        var retainedIds = retained.stream().map(BuddyNotice::id).toList();
        discriminators.keySet().retainAll(retainedIds);
        duplicateIds.retainAll(retainedIds);
        for (BuddyNotice n : retained) discriminators.computeIfAbsent(n.id(), ignored -> ++nextDiscriminator);
        return visible.stream().filter(n -> groupFilter == null || n.group() != null && n.group().id().equals(groupFilter))
            .map(n -> {
                boolean duplicate = n.group() != null && visible.stream().anyMatch(other -> !other.id().equals(n.id())
                    && other.group() != null && other.group().id().equals(n.group().id()) && other.title().equals(n.title()));
                if (duplicate) duplicateIds.add(n.id());
                String title = n.title() + (duplicateIds.contains(n.id()) ? " · " + discriminators.get(n.id()) : "");
                if (groupFilter == null && n.group() != null) title = n.group().title() + " · " + title;
                return new BuddyNotice(n.id(), n.kind(), title, n.state(), n.detail(), n.activate(), n.group());
            }).toList();
    }
    private int scroll;
    private int hovered = -1;
    /** The card the pointer just left, so only that one animates back rather than every card. */
    private int leaving = -1;
    private long hoverChangedAt;

    BuddyDeckPanel(BuddyOptions options, BuddyDeck deck, Runnable onLayoutChanged) {
        this(options, deck, onLayoutChanged, () -> { });
    }

    BuddyDeckPanel(BuddyOptions options, BuddyDeck deck, Runnable onLayoutChanged, Runnable onNoticesChanged) {
        this.options = java.util.Objects.requireNonNull(options);
        this.onNoticesChanged = Objects.requireNonNull(onNoticesChanged, "onNoticesChanged");
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

    boolean needsAnimationFrames() { return animating() || displayed().stream().anyMatch(BuddyCard::shimmers); }

    boolean needsDetailUpdates() { return !displayed().isEmpty(); }

    int cardHeight() { return BuddyCard.height(this); }

    /** Whether the hover affordance is still fading. */
    boolean animating() {
        return (hovered >= 0 || leaving >= 0)
            && clock.getAsLong() - hoverChangedAt < BubbleMotion.HOVER_NANOS;
    }

    /** The drawer was closed: forget where you were, so it reopens at the top. */
    void reset() {
        if (scroll == 0 && hovered < 0) return;
        scroll = 0;
        hovered = -1;
        leaving = -1;
        changed();
    }

    /** A notice was posted, dismissed or cleared: re-size around the new list. */
    void refresh() {
        reconcileSize();
        hovered = -1; leaving = -1;
        changed();
    }

    /** Reconcile external membership changes against the viewport the owner is about to install. */
    Dimension reconcileSize() {
        Dimension size = getPreferredSize();
        int nextViewport = Math.max(0, size.height - 2 * MOTION_MARGIN - headerHeight());
        scroll = BuddyDeckLayout.clampScroll(scroll, contentHeight(), nextViewport);
        return size;
    }

    @Override public Dimension getPreferredSize() {
        int count = displayed().size();
        if (count == 0) return new Dimension(0, 0);
        int height = Math.min(BuddyDeckLayout.expandedHeight(count, cardHeight()),
            Math.max(cardHeight(), maxListHeight.getAsInt() - headerHeight()));
        return new Dimension(BuddyCard.WIDTH + 2 * MOTION_MARGIN, height + 2 * MOTION_MARGIN + headerHeight());
    }

    private int viewportHeight() { return Math.max(0, getHeight() - 2 * MOTION_MARGIN - headerHeight()); }

    private int contentHeight() { return BuddyDeckLayout.expandedHeight(displayed().size(), cardHeight()); }

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
        if (displayed().isEmpty()) return false;
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN - headerHeight());
        Rectangle clear = BuddyDeckLayout.clearRow(displayed().size(), BuddyCard.WIDTH, cardHeight(), scroll);
        if (clear != null && clear.contains(local)) {
            for (BuddyNotice notice : displayed()) deck.dismiss(notice.id());
            onNoticesChanged.run();
            reset();
            return false;
        }
        int index = indexAt(point);
        if (index < 0) return true;
        List<BuddyNotice> notices = displayed();
        BuddyNotice notice = notices.get(index);
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyCard.WIDTH, cardHeight(), scroll);
        if (BuddyDeckLayout.dismissTarget(card).contains(local)) {
            deck.dismiss(new BuddyNoticeId(notice.id().source(), notice.id().key()));
            onNoticesChanged.run();
            hovered = -1;
            leaving = -1;
            if (displayed().isEmpty()) { reset(); return false; }
            scroll = BuddyDeckLayout.clampScroll(scroll, contentHeight(), viewportHeight());
            changed();
            return true;
        }
        notice.activate().run();
        return false;
    }

    private int indexAt(Point point) {
        if (displayed().isEmpty()) return -1;
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN - headerHeight());
        if (local.y < 0 || local.y >= viewportHeight()) return -1;
        int index = BuddyDeckLayout.cardAt(local.y, displayed().size(), cardHeight(), scroll);
        if (index < 0) return -1;
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyCard.WIDTH, cardHeight(), scroll);
        return new java.awt.geom.RoundRectangle2D.Float(card.x, card.y, card.width, card.height,
            card.height, card.height).contains(local) ? index : -1;
    }

    private void changed() {
        revalidate();
        repaint();
        onLayoutChanged.run();
    }

    // --- painting ----------------------------------------------------------------------------

    @Override protected void paintComponent(Graphics g) {
        List<BuddyNotice> notices = displayed();
        if (notices.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(MOTION_MARGIN, MOTION_MARGIN);
            if (headerHeight() > 0) {
                g2.setFont(BuddyCard.detailFont(options));
                g2.setColor(BuddyCard.detailColor(options));
                String title = header();
                while (!title.isEmpty() && g2.getFontMetrics().stringWidth(title) > BuddyCard.WIDTH - 2 * BuddyCard.PAD_X)
                    title = title.substring(0, title.length() - 1);
                g2.drawString(title, BuddyCard.PAD_X, 18);
                g2.translate(0, headerHeight());
            }
            g2.setClip(-MOTION_MARGIN, headerHeight() > 0 && scroll > 0 ? 0 : -MOTION_MARGIN,
                BuddyCard.WIDTH + 2 * MOTION_MARGIN, viewportHeight() + MOTION_MARGIN);
            int height = cardHeight();
            for (int index = 0; index < notices.size(); index++) {
                Rectangle card = BuddyDeckLayout.expanded(index, BuddyCard.WIDTH, height, scroll);
                if (card.y + card.height < 0 || card.y > getHeight()) continue;
                BuddyCard.paint(options, g2, this, notices.get(index), card, hoverAmount(index), 1f, true, 0, clock.getAsLong());
            }
            Rectangle clear = BuddyDeckLayout.clearRow(notices.size(), BuddyCard.WIDTH, height, scroll);
            if (clear == null) return;
            g2.setFont(BuddyCard.detailFont(options));
            FontMetrics metrics = g2.getFontMetrics();
            g2.setColor(BuddyCard.detailColor(options));
            g2.drawString(CLEAR_ALL, clear.x + BuddyCard.WIDTH - BuddyCard.PAD_X - metrics.stringWidth(CLEAR_ALL),
                clear.y + (clear.height - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally { g2.dispose(); }
    }

    /** Only the hovered card reveals its affordance; the one just left fades it away. */
    private float hoverAmount(int index) {
        long elapsed = clock.getAsLong() - hoverChangedAt;
        if (index == hovered) return BubbleMotion.hover(elapsed, true);
        if (index == leaving) return BubbleMotion.hover(elapsed, false);
        return 0f;
    }
}
