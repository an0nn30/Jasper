package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import java.awt.BorderLayout;
import java.util.Objects;
import javax.swing.JPanel;

/**
 * One pane of a tab's workspace (tabs and panes spec 4): it shows the pane's one item, or nothing in the empty
 * workspace, and no strip (spec 5); the window's strip lists its tabs. The item's component enters and leaves as the
 * layout puts the item in and out of the pane, so an item that moves to another pane or window keeps its session
 * (workspace tabs spec 2.3). The code calls a pane a group and an item a tab (workspace plan 8a Rulings 1). EDT only.
 */
final class GroupView extends JPanel {
    private final GroupId id;
    private Tab tab;
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

    /** The pane left the layout: it keeps no item. */
    void close() {
        if (closed) return;
        closed = true;
        tab = null;
        removeAll();
    }
}
