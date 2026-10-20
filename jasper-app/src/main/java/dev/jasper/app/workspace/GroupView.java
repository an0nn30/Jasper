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
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * One tab group of a window's workspace (workspace tabs spec 10): the look's tab strip over a card area showing the
 * group's selected tab. The strip shows unless its workspace hides it (parent tabs spec 7: a pane's single tab in the
 * standard look), and the card area then fills the group. The strip lists, selects, closes and opens tabs in this group
 * only and starts no drag: a tab drags from its window's parent-tab bar (tabs and panes spec 6). A tab's component
 * enters and leaves the card area as the layout puts the tab in
 * and out of the group, so a tab moved to another group leaves one group view for the other with its session untouched
 * (spec 2.3). EDT only.
 */
final class GroupView extends JPanel implements StripSource {
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
    @Override public WindowContent owner() { return owner; }
    /** The group's tabs in strip order. */
    @Override public List<Tab> tabs() { return tabs; }
    /** The group's selected tab; null in the empty workspace. */
    @Override public Tab selected() { return selected; }
    /** Whether this is its window's active group. */
    boolean activeGroup() { return workspace.model().activeGroup().equals(id); }

    /**
     * Takes the group's tabs and selection from the layout: mounts the tabs it gained, drops the others, shows the selected
     * one. The strip shows when {@code stripShown}, at once: it takes the tabs while hidden, so the tab that brings it
     * back has no open motion and the tab that hides it no close motion.
     */
    void display(List<Tab> next, Tab chosen, boolean stripShown) {
        if (closed) return;
        for (Component child : cards.getComponents())
            if (next.stream().noneMatch(tab -> tab.component() == child)) cards.remove(child);
        for (Tab tab : next) if (tab.component().getParent() != cards) cards.add(tab.component());
        for (Tab tab : next) tab.component().setVisible(tab == chosen);
        tabs = List.copyOf(next);
        selected = chosen;
        cards.revalidate();
        cards.repaint();
        if (!stripShown) strip.component().setVisible(false);
        refresh();
        strip.component().setVisible(stripShown);
    }

    /** Refreshes the strip, which shows as active while the window is active and this is its active group. */
    void refresh() { if (!closed) strip.setActive(owner.windowActive() && activeGroup()); }

    @Override public String title(Tab tab) { return owner.tabTitle(tab); }
    @Override public Icon icon(Tab tab) { return owner.tabIcon(tab); }
    @Override public String tooltip(Tab tab) { return owner.tabTooltip(tab); }
    /** The Select Tab shortcut for the tab at {@code index} of this group while it is the active group, or empty. */
    @Override public String shortcut(int index) { return activeGroup() ? owner.tabShortcut(index) : ""; }
    /** Whether the tab is a terminal tab, which the classic tabs mark with a status dot. */
    @Override public boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }
    @Override public boolean running(Tab tab) { return owner.running(tab); }
    @Override public JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }
    @Override public void select(Tab tab) { owner.selectTab(tab); }
    @Override public void close(Tab tab) { owner.closeTab(tab); }
    /**
     * A left press on {@code tab}'s entry in the strip: a dock drag still running lost its release, so it ends. A pane's
     * strip starts no tab drag (tabs and panes spec 6).
     */
    @Override public void pressed(Tab tab, MouseEvent event) { owner.dockDrag().cancel(); }
    /** A pane's strip drags nothing. */
    @Override public void dragged(MouseEvent event) { }
    /** A pane's strip drags nothing. */
    @Override public void released(MouseEvent event) { }
    /** The classic tabs' "+": New Tab, a new terminal tab (tabs and panes spec 5). */
    @Override public void newTab() { owner.invoke(ActionId.NEW_TAB); }
    /** New Tab: the "+" opens a tab. */
    @Override public String newTabName() { return ActionId.NEW_TAB.label(); }
    /** An entry's × closes its tab. */
    @Override public String closeName() { return "Close tab"; }
    /** Whether {@code tab} joined this group by a move: the strip shows it at once, without the open motion (Rulings 27). */
    @Override public boolean moved(Tab tab) { return workspace.moved(tab); }
    /** The look's motion, which the equal tabs follow as tabs open and close. */
    @Override public boolean animates() { return owner.lookAnimates(); }

    /** The strip's rectangle in {@code target}'s coordinates; empty while it is hidden. */
    Rectangle stripBounds(Component target) {
        return stripShown() ? SwingUtilities.convertRectangle(this, strip.component().getBounds(), target) : new Rectangle();
    }
    /** The card area's rectangle in {@code target}'s coordinates. */
    Rectangle contentBounds(Component target) { return SwingUtilities.convertRectangle(this, cards.getBounds(), target); }
    /** Each tab's rectangle in the strip, in strip order, in {@code target}'s coordinates; none while the strip is hidden. */
    List<Rectangle> tabBounds(Component target) {
        if (!stripShown()) return List.of();
        return strip.tabBounds().stream().map(bounds -> SwingUtilities.convertRectangle(strip.component(), bounds, target)).toList();
    }
    private boolean stripShown() { return strip.component().isVisible(); }

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
