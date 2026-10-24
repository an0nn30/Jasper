package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Objects;
import javax.swing.JPanel;

/**
 * One pane of a tab's workspace (tabs and panes spec 4): it shows the pane's one item, or nothing in the empty
 * workspace, and no strip (spec 5); the window's strip lists its tabs. The item's component enters and leaves as the
 * layout puts the item in and out of the pane, so an item that moves to another pane or window keeps its session
 * (workspace tabs spec 2.3). It dims while its tab holds two or more panes and it is not the active one (spec 5). The
 * code calls a pane a group and an item a tab (workspace plan 8a Rulings 1). EDT only.
 */
final class GroupView extends JPanel {
    private final GroupId id;
    private Tab tab;
    private float dim;
    private Color over;
    private boolean closed;

    GroupView(GroupId id) {
        super(new BorderLayout());
        this.id = Objects.requireNonNull(id, "id");
        setName("group");
        setOpaque(false);
    }

    GroupId id() { return id; }

    /** The pane's item; null in the empty workspace. */
    Tab tab() { return tab; }

    /** Takes the pane's item from the layout and shows it, or nothing for the empty workspace's pane. */
    void display(Tab next) {
        if (closed || next == tab && (next == null || next.component().getParent() == this)) return;
        if (tab != null && tab != next && tab.component().getParent() == this) remove(tab.component());
        tab = next;
        if (next != null) {
            if (next.component().getParent() != this) add(next.component(), BorderLayout.CENTER);
            next.component().setVisible(true);
        }
        revalidate();
        repaint();
    }

    /**
     * Dims the pane (tabs and panes spec 5; workspace plan 8a Rulings 12): {@code dimmed} while its tab holds two or more
     * panes and it is not the active one. A terminal dims through its own view, which already dims every terminal but
     * its window's active item (Rulings 13), so it takes no overlay; any other item dims under an overlay of
     * {@code colour} at {@code amount}, the configured {@code terminal.dim_inactive_panes}. Nothing animates.
     *
     * @param dimmed whether the pane dims
     * @param amount how much, 0 to 1
     * @param colour the overlay's colour, the workspace's surface; null while the pane shows clear
     */
    void dim(boolean dimmed, float amount, Color colour) {
        float next = dimmed && tab != null && !(tab.content() instanceof TerminalContent) ? amount : 0f;
        if (next == dim && Objects.equals(colour, over)) return;
        dim = next;
        over = colour;
        repaint();
    }

    /** The overlay's amount over the pane's item; 0 while it shows clear, and always for a terminal. */
    float dim() { return dim; }

    /** While the overlay shows, the item's own repaints paint it too. */
    @Override protected boolean isPaintingOrigin() { return dim > 0; }

    @Override public void paint(Graphics graphics) {
        super.paint(graphics);
        if (dim <= 0 || over == null) return;
        var g = (Graphics2D) graphics.create();
        try {
            g.setColor(new Color(over.getRed(), over.getGreen(), over.getBlue(), Math.round(255 * dim)));
            g.fillRect(0, 0, getWidth(), getHeight());
        } finally { g.dispose(); }
    }

    /** The pane left the layout: it keeps no item. */
    void close() {
        if (closed) return;
        closed = true;
        tab = null;
        removeAll();
    }
}
