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
 * The drawer: retained live work in stable groups, on demand. The summary above his head
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
    private static final String CLEAR_ALL = "Dismiss all";

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private final Runnable onNoticesChanged;
    private LongSupplier clock = System::nanoTime;
    /**
     * How tall the list may grow. Supplied by the window, which knows the screen: querying a
     * graphics device from here would throw in the headless test JVM.
     */
    private IntSupplier maxListHeight = () -> Integer.MAX_VALUE / 4;
    private final dev.jasper.buddy.internal.model.BuddyGroups groups = new dev.jasper.buddy.internal.model.BuddyGroups();
    private final java.util.Map<BuddyNoticeId, Integer> discriminators = new java.util.LinkedHashMap<>();
    private int nextDiscriminator;
    private final java.util.Set<BuddyNoticeId> duplicateIds = new java.util.HashSet<>();
    record Row(dev.jasper.buddy.internal.model.BuddyGroups.Entry section, BuddyNotice notice, Rectangle bounds) { }
    List<BuddyNotice> displayed() {
        var retained = deck.retainedNotices();
        var ids = retained.stream().map(BuddyNotice::id).toList();
        discriminators.keySet().retainAll(ids); duplicateIds.retainAll(ids);
        for (BuddyNotice n : retained) discriminators.computeIfAbsent(n.id(), ignored -> ++nextDiscriminator);
        var visible = groups.reconcile(deck).stream().flatMap(e -> e.members().stream()).toList();
        return visible.stream().map(n -> {
            if (n.group() != null && visible.stream().anyMatch(other -> !other.id().equals(n.id())
                && other.group() != null && other.group().id().equals(n.group().id()) && other.title().equals(n.title())))
                duplicateIds.add(n.id());
            String title = n.title() + (duplicateIds.contains(n.id()) ? " · " + discriminators.get(n.id()) : "");
            return new BuddyNotice(n.id(), n.kind(), title, n.state(), n.detail(), n.activate(), n.group());
        }).toList();
    }
    List<Row> rows() {
        var labels = displayed().stream().collect(java.util.stream.Collectors.toMap(BuddyNotice::id, n -> n));
        var rows = new java.util.ArrayList<Row>();
        int y = -scroll;
        for (var entry : groups.reconcile(deck)) {
            if (entry.group() != null) {
                rows.add(new Row(entry, null, new Rectangle(0, y, BuddyCard.WIDTH, 28)));
                y += 28 + BuddyDeckLayout.ROW_GAP;
            }
            if (entry.group() == null || groups.expanded(entry.identity())) {
                for (var n : entry.members()) {
                    rows.add(new Row(null, labels.get(n.id()), new Rectangle(0, y, BuddyCard.WIDTH, cardHeight())));
                    y += cardHeight() + BuddyDeckLayout.ROW_GAP;
                }
            }
        }
        if (labels.size() > 1) rows.add(new Row(null, null, new Rectangle(0, y, BuddyCard.WIDTH, BuddyDeckLayout.CLEAR_ROW_HEIGHT)));
        return java.util.List.copyOf(rows);
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
        setFocusable(true);
    }

    /** Tests advance time instead of sleeping; the window leaves this as {@code System::nanoTime}. */
    void setClock(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    void setMaxListHeight(IntSupplier maxListHeight) {
        this.maxListHeight = Objects.requireNonNull(maxListHeight, "maxListHeight");
    }

    int scroll() { return scroll; }

    int hovered() { return hovered; }

    boolean needsAnimationFrames() { return animating(); }

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
        int nextViewport = Math.max(0, size.height - 2 * MOTION_MARGIN);
        scroll = BuddyDeckLayout.clampScroll(scroll, contentHeight(), nextViewport);
        return size;
    }

    @Override public Dimension getPreferredSize() {
        int count = displayed().size();
        if (count == 0) return new Dimension(0, 0);
        int height = Math.min(contentHeight(),
            Math.max(cardHeight(), maxListHeight.getAsInt()));
        return new Dimension(BuddyCard.WIDTH + 2 * MOTION_MARGIN, height + 2 * MOTION_MARGIN);
    }

    private int viewportHeight() { return Math.max(0, getHeight() - 2 * MOTION_MARGIN); }

    private int contentHeight() {
        var rows = rows();
        return rows.isEmpty() ? 0 : rows.getLast().bounds().y + rows.getLast().bounds().height + scroll;
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
        int index = indexAt(point);
        if (index < 0) return true;
        Row row = rows().get(index);
        if (row.section() != null) {
            groups.toggle(row.section().identity()); refresh(); return true;
        }
        if (row.notice() == null) {
            for (var notice : displayed()) deck.dismiss(notice.id());
            onNoticesChanged.run(); reset(); return false;
        }
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN);
        if (BuddyDeckLayout.dismissTarget(row.bounds()).contains(local)) {
            deck.dismiss(row.notice().id()); onNoticesChanged.run();
            refresh(); return !displayed().isEmpty();
        }
        row.notice().activate().run(); return false;
    }

    private int indexAt(Point point) {
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN);
        if (local.y < 0 || local.y >= viewportHeight()) return -1;
        var rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); var b = row.bounds();
            if (row.notice() == null ? b.contains(local) : new java.awt.geom.RoundRectangle2D.Float(
                b.x, b.y, b.width, b.height, b.height, b.height).contains(local)) return i;
        }
        return -1;
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
            g2.clipRect(-MOTION_MARGIN, 0, BuddyCard.WIDTH + 2 * MOTION_MARGIN, viewportHeight());
            var rows = rows();
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index); Rectangle bounds = row.bounds();
                if (bounds.y + bounds.height <= 0 || bounds.y >= viewportHeight()) continue;
                if (row.notice() != null) {
                    BuddyCard.paint(options, g2, this, row.notice(), bounds, hoverAmount(index), 1f, true, 0, clock.getAsLong());
                } else {
                    String text = row.section() == null ? CLEAR_ALL :
                        (groups.expanded(row.section().identity()) ? "▾ " : "▸ ") + row.section().group().title()
                            + " · " + row.section().members().size();
                    g2.setFont(BuddyCard.detailFont(options));
                    g2.setColor(BuddyCard.detailColor(options));
                    text = BuddyCard.fit(g2.getFontMetrics(), text, BuddyCard.WIDTH - 2 * BuddyCard.PAD_X);
                    g2.drawString(text, BuddyCard.PAD_X, bounds.y + 19);
                }
            }
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
