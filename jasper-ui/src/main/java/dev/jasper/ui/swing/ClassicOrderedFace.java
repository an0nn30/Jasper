package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.ScrollPaneLayout;
import javax.swing.border.Border;

/**
 * The classic ordered list (spec 5.2 and 6.4, the Project Index and Add Host mocks; plan 4c Rulings 13 to 16): a square
 * box of striped rows with a muted data-face index, and a side column of equal buttons beside it, top-aligned, spanning
 * an entry row its VERTICAL parent holds right after it.
 */
final class ClassicOrderedFace implements OrderedFace {
    /** The rows a list with an entry row keeps room for: an empty one shows its empty line in a box two rows tall (plan 9b Task 6). */
    static final int ENTRY_LIST_ROWS = 2;

    private final PaintPair box, index, zebraIndex;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicOrderedFace(SemanticColor surface) {
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        index = new PaintPair(SemanticColor.TEXT_TERTIARY, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.TEXT);
        zebraIndex = new PaintPair(SemanticColor.TEXT_TERTIARY, DerivedColor.SURFACE_ZEBRA, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(box, index, zebraIndex); }
    @Override public boolean rowCommands() { return false; }
    @Override public Nodes.TextRole indexRole() { return Nodes.TextRole.MONO_CAPTION; }
    /** An even row's index on {@code surface.input}, an odd row's on {@code surface.zebra}. */
    @Override public void styleIndex(JLabel label, KitContext context, int row) {
        label.setForeground(ThemePainting.painted(context, row % 2 == 1 ? zebraIndex : index));
    }

    /** Odd rows in {@code surface.zebra}; the box and its fill are the pane's. */
    @Override public void paintCanvas(InteractiveRows.Canvas canvas, Graphics graphics) {
        graphics.setColor(ThemePainting.role(canvas.owner.context(), DerivedColor.SURFACE_ZEBRA));
        for (var row : canvas.owner.mounted.values())
            if (row.index % 2 == 1) graphics.fillRect(0, row.getY(), canvas.getWidth(), row.getHeight());
    }

    /** The box's fill, inside the edge {@link Reserve} paints. */
    @Override public void paintPane(ItemLists.ListControl list, Graphics2D g) {
        int line = ThemePainting.metric(list.context(), DesignMetrics.Role.HAIRLINE);
        int width = list.scroll.getWidth() - column(list), height = box(list, list.scroll.getHeight());
        g.setColor(ThemePainting.color(list.context(), SemanticColor.SURFACE_INPUT));
        g.fillRect(line, line, Math.max(0, width - 2 * line), Math.max(0, height - 2 * line));
    }

    @Override public void mount(ItemLists.ListControl list, ControlRegistry.Control add) {
        if (list.side == null) {
            list.side = new ItemLists.SideColumn(list);
            list.scroll.setLayout(new SideLayout(list));
            list.scroll.setBorder(new Reserve(list, box));
            list.scroll.add(list.side);
        }
        list.side.addAction(add);
    }

    /**
     * Each ORDERED list of a VERTICAL stack that has a side column adopts the INLINE row right after it, which the stack
     * records as placed there.
     */
    @Override public void span(Stacks.StackControl stack, List<ControlRegistry.Control> controls) {
        if (((Nodes.Stack) stack.node).role() != Nodes.LayoutRole.VERTICAL) return;
        var adopted = new HashMap<String, Container>();
        for (int i = 0; i < controls.size(); i++)
            if (controls.get(i) instanceof ItemLists.ListControl list && list.sideColumn()) {
                var row = i + 1 < controls.size() && controls.get(i + 1) instanceof Stacks.StackControl next
                    && ((Nodes.Stack) next.node).role() == Nodes.LayoutRole.INLINE ? next : null;
                list.entry(row == null ? null : row.component);
                if (row != null) {
                    adopted.put(row.node.id(), list.scroll);
                    // The row keeps the face's layout: set here, and again by the row's own refresh while this list holds it.
                    row.entryOf = list;
                    if (!(row.component.getLayout() instanceof EntryLayout)) row.component.setLayout(new EntryLayout(list));
                }
            }
        stack.adopted = Map.copyOf(adopted);
    }

    /** The room right of the box: the side column and its gap. */
    static int column(ItemLists.ListControl list) {
        return list.side == null ? 0 : list.side.getPreferredSize().width + ThemePainting.metric(list.context(), DesignMetrics.Role.SIDE_COLUMN_GAP);
    }

    /** The room under the box: an adopted entry row and its gap, the side column's (the mocks' 6, plan 9b Task 6). */
    static int entry(ItemLists.ListControl list) {
        var entry = list.entry;
        return entry == null || !entry.isVisible() || entry.getParent() != list.scroll ? 0
            : entry.getPreferredSize().height + ThemePainting.metric(list.context(), DesignMetrics.Role.SIDE_COLUMN_GAP);
    }

    /**
     * The box's preferred height above an entry row, its edges included: its rows, at least {@link #ENTRY_LIST_ROWS}, and
     * the focus ring's width the Shell, Font &amp; Colors and Project Index mocks leave under the last row.
     */
    static int preferredBox(ItemLists.ListControl list) {
        int line = ThemePainting.metric(list.context(), DesignMetrics.Role.HAIRLINE);
        int rows = Math.max(list.canvas.getPreferredScrollableViewportSize().height, ENTRY_LIST_ROWS * list.rowHeight);
        return 2 * line + rows + ThemePainting.metric(list.context(), DesignMetrics.Role.FOCUS_RING);
    }

    /**
     * The box's height in a pane {@code height} tall: all of it but the entry row; with an entry row, at most its preferred
     * height, the entry row right below it, so a taller side column leaves room under the entry row instead of
     * stretching the box (plan 9b Task 6).
     */
    static int box(ItemLists.ListControl list, int height) {
        int entry = entry(list), room = height - entry;
        return entry == 0 ? room : Math.min(room, preferredBox(list));
    }

    /** The pane's border: the 1 px box around the rows, and the room for the side column and the entry row outside it. */
    static final class Reserve implements Border {
        final ItemLists.ListControl list;
        final PaintPair edge;

        Reserve(ItemLists.ListControl list, PaintPair edge) { this.list = list; this.edge = edge; }

        @Override public Insets getBorderInsets(Component c) {
            int line = ThemePainting.metric(list.context(), DesignMetrics.Role.HAIRLINE);
            return new Insets(line, line, Math.max(line + entry(list), c.getHeight() - box(list, c.getHeight()) + line), line + column(list));
        }

        @Override public boolean isBorderOpaque() { return false; }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            int line = ThemePainting.metric(list.context(), DesignMetrics.Role.HAIRLINE);
            int boxWidth = width - column(list), boxHeight = box(list, height);
            g.setColor(ThemePainting.painted(list.context(), edge));
            g.fillRect(x, y, boxWidth, line);
            g.fillRect(x, y + boxHeight - line, boxWidth, line);
            g.fillRect(x, y, line, boxHeight);
            g.fillRect(x + boxWidth - line, y, line, boxHeight);
        }
    }

    /**
     * An adopted entry row's layout, as the Shell, Font &amp; Colors and Project Index mocks draw it (plan 9b Task 6): its
     * last child, the add button, as wide as the side column at the row's end, the entry before it filling the rest at the
     * side column's gap; each child centred on the row's height.
     */
    static final class EntryLayout implements Composition.WidthLayout {
        final ItemLists.ListControl list;

        EntryLayout(ItemLists.ListControl list) { this.list = list; }

        int gap() { return ThemePainting.metric(list.context(), DesignMetrics.Role.SIDE_COLUMN_GAP); }

        /** The add button's width: the side column's, or its own without a side column. */
        int last(Component button) { return list.side == null ? button.getPreferredSize().width : Math.max(button.getPreferredSize().width, list.side.getPreferredSize().width); }

        @Override public int height(Container parent, int width) {
            int height = 0;
            for (var child : parent.getComponents()) if (child.isVisible()) height = Math.max(height, child.getPreferredSize().height);
            return height + parent.getInsets().top + parent.getInsets().bottom;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            var shown = shown(parent);
            int width = parent.getInsets().left + parent.getInsets().right + Math.max(0, shown.size() - 1) * gap();
            for (int i = 0; i < shown.size(); i++) width += i == shown.size() - 1 ? last(shown.get(i)) : shown.get(i).getPreferredSize().width;
            return new Dimension(width, height(parent, width));
        }

        @Override public void layoutContainer(Container parent) {
            var shown = shown(parent);
            if (shown.isEmpty()) return;
            Insets insets = parent.getInsets();
            int left = insets.left, right = parent.getWidth() - insets.right, height = parent.getHeight() - insets.top - insets.bottom;
            var button = shown.getLast();
            int width = shown.size() > 1 ? Math.min(last(button), Math.max(0, right - left)) : Math.max(0, right - left);
            place(button, right - width, width, insets.top, height);
            right -= width + gap();
            // The children between the entry and the button keep their own widths, packed before the button.
            for (int i = shown.size() - 2; i >= 1; i--) {
                int own = Math.min(shown.get(i).getPreferredSize().width, Math.max(0, right - left));
                place(shown.get(i), right - own, own, insets.top, height);
                right -= own + gap();
            }
            if (shown.size() > 1) place(shown.getFirst(), left, Math.max(0, right - left), insets.top, height);
        }

        private static void place(Component child, int x, int width, int top, int height) {
            int own = Math.min(height, child.getPreferredSize().height);
            child.setBounds(x, top + (height - own) / 2, width, own);
        }

        private static List<Component> shown(Container parent) {
            var shown = new java.util.ArrayList<Component>();
            for (var child : parent.getComponents()) if (child.isVisible()) shown.add(child);
            return shown;
        }
    }

    /** The pane's layout: the rows inside the reserve, the side column at the top right, the entry row under the box. */
    static final class SideLayout extends ScrollPaneLayout {
        final ItemLists.ListControl list;

        SideLayout(ItemLists.ListControl list) { this.list = list; }

        @Override public void layoutContainer(Container parent) {
            super.layoutContainer(parent);
            var side = list.side;
            if (side != null) {
                var size = side.getPreferredSize();
                side.setBounds(parent.getWidth() - size.width, 0, size.width, size.height);
            }
            var entry = list.entry;
            if (entry != null && entry.getParent() == parent && entry.isVisible()) {
                int height = entry.getPreferredSize().height, top = box(list, parent.getHeight()) + ThemePainting.metric(list.context(), DesignMetrics.Role.SIDE_COLUMN_GAP);
                entry.setBounds(0, top, Math.max(0, parent.getWidth() - column(list)), height);
            }
        }

        /** With an entry row, the box at its preferred height and the entry row below it; else the scroll pane's. */
        @Override public Dimension preferredLayoutSize(Container parent) {
            var size = super.preferredLayoutSize(parent);
            if (entry(list) > 0) size.height = preferredBox(list) + entry(list);
            return spanned(size);
        }

        @Override public Dimension minimumLayoutSize(Container parent) { return spanned(super.minimumLayoutSize(parent)); }

        /** {@code size}, at least as tall as the side column (the right inset already holds its width). */
        private Dimension spanned(Dimension size) {
            if (list.side != null) size.height = Math.max(size.height, list.side.getPreferredSize().height);
            return size;
        }
    }
}
