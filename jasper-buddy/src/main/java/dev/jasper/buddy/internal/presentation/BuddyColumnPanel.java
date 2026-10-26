package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.internal.model.BuddyGroups;
import java.awt.*;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.swing.JComponent;

/** One quiet activity summary; all inspection happens in the drawer. */
final class BuddyColumnPanel extends JComponent {
    static final int MARGIN = BuddyCard.SHADOW_MARGIN;
    private BuddyOptions options;
    private final BuddyDeck deck;
    private final BuddyGroups groups = new BuddyGroups();
    private final Runnable onLayoutChanged;
    private final Runnable onOpenDrawer;
    private boolean below;
    private int hovered = -1;

    BuddyColumnPanel(BuddyOptions options, BuddyDeck deck, Runnable changed, Runnable open) {
        this.options = Objects.requireNonNull(options);
        this.deck = Objects.requireNonNull(deck);
        onLayoutChanged = Objects.requireNonNull(changed);
        onOpenDrawer = Objects.requireNonNull(open);
        setOpaque(false);
    }
    void applyOptions(BuddyOptions value) { options = Objects.requireNonNull(value); revalidate(); repaint(); }
    void setClock(LongSupplier clock) { Objects.requireNonNull(clock); }
    void setBelow(boolean value) { below = value; }
    void setBelow(boolean value, boolean snap) { setBelow(value); }
    boolean below() { return below; }
    void refresh() { groups.summary(deck); revalidate(); repaint(); onLayoutChanged.run(); }
    boolean animating() { return false; }
    boolean needsAnimationFrames() { return false; }
    boolean needsDetailUpdates() { return false; }
    void repaintFrame() { repaint(); }
    int hovered() { return hovered; }
    @Override public Dimension getPreferredSize() {
        return deck.isEmpty() ? new Dimension() : new Dimension(BuddyCard.WIDTH + 2 * MARGIN, BuddyCard.HEIGHT + 2 * MARGIN);
    }
    Rectangle cardBounds(int index) {
        if (index != 0) throw new IndexOutOfBoundsException(index);
        return new Rectangle(MARGIN, MARGIN, BuddyCard.WIDTH, BuddyCard.HEIGHT);
    }
    void handleMove(Point point) { hovered = !deck.isEmpty() && cardBounds(0).contains(point) ? 0 : -1; repaint(); }
    void handleExit() { hovered = -1; repaint(); }
    boolean handleClick(Point point) { if (!deck.isEmpty()) onOpenDrawer.run(); return true; }
    @Override protected void paintComponent(Graphics graphics) {
        var summary = groups.summary(deck);
        if (summary == null) return;
        var g = (Graphics2D) graphics.create();
        try { BuddyCard.paint(options, g, this, summary, cardBounds(0), hovered == 0 ? 1 : 0, 1, false, 0); }
        finally { g.dispose(); }
    }
}
