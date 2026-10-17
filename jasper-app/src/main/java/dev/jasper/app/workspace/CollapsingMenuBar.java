package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ClassicMenuRow;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * The window's menu bar. As its room shrinks the trailing menus fold into a "…" menu (File always stays), then every
 * menu folds into one ☰ menu. Folded menus are the same {@code JMenu} objects: they move into the fold menu while it is
 * open and back when it closes. With a header (the Windows and Linux title bar) the header spans the bar under the
 * menus and decides their room, so the search field stays centred on the window. In a classic row
 * ({@link ClassicMenuRow#styled}) the menus stand at their {@link ClassicMenuRow#titleWidth}, each edge rounded from the
 * running total.
 */
final class CollapsingMenuBar extends JMenuBar {
    enum Fold { NONE, MORE, ALL }
    private final List<JMenu> menus = new ArrayList<>();
    private final List<JMenu> opened = new ArrayList<>();
    private final JMenu more = new JMenu(), hamburger = new JMenu();
    private final Component glue = Box.createHorizontalGlue();
    private WindowHeader header;
    private Fold fold = Fold.NONE;
    private int kept;
    private boolean moving;

    CollapsingMenuBar() {
        setLayout(new Layout());
        control(more, "menuOverflow", "More menus", AppIcons.named("MORE"));
        control(hamburger, "menuHamburger", "Application menu", AppIcons.semantic("menu"));
        super.addImpl(more, null, -1);
        super.addImpl(hamburger, null, -1);
    }

    private void control(JMenu control, String name, String description, Icon icon) {
        dev.jasper.ui.swing.NativeStyles.installed().menu(control, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        control.setName(name);
        control.setIcon(icon);
        control.setToolTipText(description);
        control.getAccessibleContext().setAccessibleName(description);
        control.setVisible(false);
        control.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent event) { openInto(control); }
            // Later, so an item's action runs before its menu moves back.
            @Override public void menuDeselected(MenuEvent event) { SwingUtilities.invokeLater(CollapsingMenuBar.this::restoreMenus); }
            @Override public void menuCanceled(MenuEvent event) { SwingUtilities.invokeLater(CollapsingMenuBar.this::restoreMenus); }
        });
    }

    Fold fold() { return fold; }
    List<JMenu> menus() { return List.copyOf(menus); }
    JMenu overflowMenu() { return more; }
    JMenu hamburgerMenu() { return hamburger; }

    /** Puts {@code value} under the menus, spanning the bar; the header then decides how many menus show. */
    void setHeader(WindowHeader value) {
        header = value;
        super.addImpl(value, null, -1);
        // FlatLaf stretches an embedded menu bar across the title bar only when it ends with a horizontal glue.
        super.addImpl(glue, null, -1);
        value.setLeading(this::fit, this::minimumMenusWidth);
        revalidate();
    }

    @Override public boolean isOptimizedDrawingEnabled() { return header == null; }

    @Override protected void addImpl(Component component, Object constraints, int index) {
        if (moving || !(component instanceof JMenu menu) || component == more || component == hamburger) {
            super.addImpl(component, constraints, index);
            return;
        }
        dev.jasper.ui.swing.NativeStyles.installed().menu(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu.getPopupMenu(), dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        menus.add(menu);
        super.addImpl(menu, constraints, getComponentIndex(more));
    }

    @Override public void remove(Component component) {
        if (!moving && component instanceof JMenu menu && menus.remove(menu)) {
            opened.remove(menu);
            Container parent = menu.getParent();
            if (parent != null && parent != this) { parent.remove(menu); return; }
        }
        super.remove(component);
    }

    /** F10 selects {@code getMenu(0)}: make that the first menu that is showing. */
    @Override public JMenu getMenu(int index) {
        if (index == 0)
            for (Component child : getComponents()) if (child instanceof JMenu menu && menu.isVisible()) return menu;
        return super.getMenu(index);
    }

    /** Shows as many menus as fit in {@code space} and returns the width they take. */
    int fit(int space) {
        if (!opened.isEmpty()) return shownWidth();
        double all = 0;
        for (JMenu menu : menus) all += width(menu);
        if (menus.isEmpty() || pixels(all) <= space) { apply(Fold.NONE, menus.size()); return pixels(all); }
        double used = 0;
        int keep = 0;
        for (JMenu menu : menus) {
            if (pixels(used + width(menu) + width(more)) > space) break;
            used += width(menu); keep++;
        }
        if (keep >= 1) { apply(Fold.MORE, keep); return pixels(used + width(more)); }
        apply(Fold.ALL, 0);
        return pixels(width(hamburger));
    }

    int minimumMenusWidth() { return menus.isEmpty() ? 0 : pixels(width(hamburger)); }

    private int shownWidth() {
        double total = 0;
        for (Component child : getComponents())
            if (child != header && child != glue && child.isVisible()) total += width(child);
        return pixels(total);
    }

    /** {@code component}'s width in the bar: its title width in a classic row, else its preferred width. */
    private double width(Component component) {
        return ClassicMenuRow.styled(this) ? ClassicMenuRow.titleWidth(component) : component.getPreferredSize().width;
    }

    /** The pixels a run of widths takes: their total, rounded, as every edge is placed. */
    private static int pixels(double width) { return (int) Math.round(width); }

    private void apply(Fold next, int keep) {
        fold = next; kept = keep;
        for (int i = 0; i < menus.size(); i++) show(menus.get(i), next == Fold.NONE || next == Fold.MORE && i < keep);
        show(more, next == Fold.MORE);
        show(hamburger, next == Fold.ALL);
    }

    private static void show(Component component, boolean visible) { if (component.isVisible() != visible) component.setVisible(visible); }

    private void openInto(JMenu control) {
        restoreMenus();
        List<JMenu> folded = control == hamburger ? menus : menus.subList(Math.min(kept, menus.size()), menus.size());
        moving = true;
        try {
            for (JMenu menu : List.copyOf(folded)) { control.add(menu); menu.setVisible(true); opened.add(menu); }
        } finally { moving = false; }
    }

    /** Puts folded menus back in the bar, in order; the next layout hides them again. */
    void restoreMenus() {
        if (opened.isEmpty()) return;
        moving = true;
        try {
            for (JMenu menu : opened) { Container parent = menu.getParent(); if (parent != null) parent.remove(menu); }
            for (JMenu menu : menus) {
                if (!opened.contains(menu)) continue;
                int index = 0;
                for (JMenu other : menus) { if (other == menu) break; if (other.getParent() == this) index++; }
                menu.setVisible(false);
                super.addImpl(menu, null, index);
            }
        } finally { moving = false; opened.clear(); }
        revalidate(); repaint();
    }

    private final class Layout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            double run = 0;
            int height = 0;
            for (Component child : getComponents()) {
                if (child == glue || child == header || !child.isVisible()) continue;
                run += width(child); height = Math.max(height, child.getPreferredSize().height);
            }
            int width = pixels(run);
            if (header != null) {
                Dimension size = header.getPreferredSize();
                width = Math.max(width, size.width); height = Math.max(height, size.height);
            }
            Insets insets = getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            int width = header != null ? header.getMinimumSize().width : minimumMenusWidth();
            return new Dimension(width, preferredLayoutSize(parent).height);
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = getInsets();
            int height = getHeight() - insets.top - insets.bottom;
            if (header != null) { header.setBounds(0, 0, getWidth(), getHeight()); header.doLayout(); }
            else fit(getWidth() - insets.left - insets.right);
            double run = 0;
            for (Component child : getComponents()) {
                if (child == header || child == glue || !child.isVisible()) continue;
                int x = insets.left + pixels(run);
                run += width(child);
                child.setBounds(x, insets.top, insets.left + pixels(run) - x, height);
            }
            glue.setBounds(getWidth(), 0, 0, 0);
        }
    }
}
