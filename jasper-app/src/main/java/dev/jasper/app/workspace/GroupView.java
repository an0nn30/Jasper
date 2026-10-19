package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * One tab group of a window's workspace (workspace tabs spec 10): the look's tab strip, always shown, over a card area
 * showing the group's selected tab. The strip lists, selects, closes, reorders and opens tabs in this group only. A tab's
 * component enters and leaves the card area as the layout puts the tab in and out of the group, so a tab moved to
 * another group leaves one group view for the other with its session untouched (spec 2.3). EDT only.
 */
final class GroupView extends JPanel {
    private final WindowContent owner;
    private final WorkspaceView workspace;
    private final GroupId id;
    private final TabStrip strip;
    private final JPanel cards = new JPanel(new Cards());
    private List<Tab> tabs = List.of();
    private Tab selected;
    private boolean closed;

    GroupView(WindowContent owner, WorkspaceView workspace, GroupId id) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.id = Objects.requireNonNull(id, "id");
        setName("group");
        setOpaque(false);
        cards.setName("groupCards");
        cards.setOpaque(false);
        strip = owner.look().tabs(this);
        strip.setAnimations(owner.animations());
        WorkspaceTheme.apply(strip.component(), owner.theme());
        add(strip.component(), BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
    }

    GroupId id() { return id; }
    TabStrip strip() { return strip; }
    WindowContent owner() { return owner; }
    /** The group's tabs in strip order. */
    List<Tab> tabs() { return tabs; }
    /** The group's selected tab; null in the empty workspace. */
    Tab selected() { return selected; }
    /** Whether this is its window's active group. */
    boolean activeGroup() { return workspace.model().activeGroup().equals(id); }

    /** Takes the group's tabs and selection from the layout: mounts the tabs it gained, drops the others, shows the selected one. */
    void display(List<Tab> next, Tab chosen) {
        if (closed) return;
        for (Component child : cards.getComponents())
            if (next.stream().noneMatch(tab -> tab.component() == child)) cards.remove(child);
        for (Tab tab : next) if (tab.component().getParent() != cards) cards.add(tab.component());
        for (Tab tab : next) tab.component().setVisible(tab == chosen);
        tabs = List.copyOf(next);
        selected = chosen;
        cards.revalidate();
        cards.repaint();
        refresh();
    }

    /** Refreshes the strip, which shows as active while the window is active and this is its active group. */
    void refresh() { if (!closed) strip.setActive(owner.windowActive() && activeGroup()); }

    String title(Tab tab) { return owner.tabTitle(tab); }
    Icon icon(Tab tab) { return owner.tabIcon(tab); }
    String tooltip(Tab tab) { return owner.tabTooltip(tab); }
    /** The Select Tab shortcut for the tab at {@code index} of this group, or empty. */
    String shortcut(int index) { return owner.tabShortcut(index); }
    /** Whether the tab is a terminal tab, which the classic tabs mark with a status dot. */
    boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }
    boolean running(Tab tab) { return owner.running(tab); }
    JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }
    void select(Tab tab) { owner.selectTab(tab); }
    void close(Tab tab) { owner.closeTab(tab); }
    /** A strip drag: moves the tab to {@code index} of this group, keeping the selection. */
    void reorder(Tab tab, int index) { workspace.reorder(tab, id, index); }
    /** The classic tabs' "+": a new terminal tab in this group. */
    void newTab() { workspace.activate(id); owner.invoke(ActionId.NEW_TAB); }
    /** Whether {@code tab} joined this group by a move: the strip shows it at once, without the open motion (Rulings 27). */
    boolean moved(Tab tab) { return workspace.moved(tab); }

    /** The strip's rectangle in {@code target}'s coordinates (workspace plan 5 resolves drops over it). */
    Rectangle stripBounds(Component target) { return SwingUtilities.convertRectangle(this, strip.component().getBounds(), target); }
    /** The card area's rectangle in {@code target}'s coordinates. */
    Rectangle contentBounds(Component target) { return SwingUtilities.convertRectangle(this, cards.getBounds(), target); }

    /** The group left the layout: its strip is released and its card area keeps no tab. */
    void close() {
        if (closed) return;
        closed = true;
        strip.close();
        cards.removeAll();
    }

    /** Every tab fills the card area, as a tabbed pane's cards do; only the selected one is visible. */
    private static final class Cards implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return largest(parent, false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return largest(parent, true); }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            for (Component child : parent.getComponents())
                child.setBounds(insets.left, insets.top, parent.getWidth() - insets.left - insets.right,
                    parent.getHeight() - insets.top - insets.bottom);
        }
        private static Dimension largest(Container parent, boolean minimum) {
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                width = Math.max(width, size.width);
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }
}
