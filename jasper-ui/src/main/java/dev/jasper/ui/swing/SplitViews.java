package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;

import java.awt.*;
import java.util.List;

import javax.swing.*;

/** Semantic multi-column composition with proportionate narrow fitting. */
final class SplitViews {
    private SplitViews() {}

    static final class SplitControl extends ControlRegistry.Control {
        SplitControl(KitView view, Nodes.Split node) {
            super(view, node, new SplitSurface());
            ((SplitSurface) component).owner = this;
            component.setOpaque(false);
            refresh();
        }

        @Override
        void refresh() {
            state();
            component.setLayout(new Layout(this));
        }

        /** DOCUMENT: the children that may fill the remaining height (Custom, Empty and Split), in order. */
        final java.util.Set<Component> fills = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        private final java.util.Map<String, ActionBarScroll> actionBars = new java.util.HashMap<>();
        private boolean actionBar(Nodes.Node child) {
            if (!document() || !(child instanceof Nodes.Stack row) || row.role() != Nodes.LayoutRole.INLINE || row.children().size() < 2) return false;
            return row.children().getFirst() instanceof Nodes.Text text && text.role() == Nodes.TextRole.CAPTION && text.text().isEmpty()
                && row.children().subList(1, row.children().size()).stream().allMatch(Nodes.Button.class::isInstance);
        }
        private ActionBarScroll actionBarScroll(Nodes.Node child) {
            return actionBars.computeIfAbsent(child.id(), _ -> new ActionBarScroll());
        }
        @Override Container childParent(Nodes.Node child) {
            return actionBar(child) ? actionBarScroll(child).row : component;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            fills.clear();
            for (var c : controls) if (c.node instanceof Nodes.Custom || c.node instanceof Nodes.Empty || c.node instanceof Nodes.Split) fills.add(c.component);
            var mounted = new java.util.ArrayList<JComponent>();
            for (var control : controls) {
                if (actionBar(control.node)) {
                    var scroll = actionBarScroll(control.node);
                    Stacks.mount(scroll.row, List.of(control.component));
                    scroll.setVisible(control.component.isVisible());
                    mounted.add(scroll);
                } else mounted.add(control.component);
            }
            Stacks.mount(component, mounted);
            actionBars.keySet().removeIf(id -> controls.stream().noneMatch(c -> c.node.id().equals(id) && actionBar(c.node)));
        }

        boolean document() { return ((Nodes.Split) node).kind() == Nodes.SplitKind.DOCUMENT; }
    }

    /** Only a document's empty-caption/action row overflows horizontally; Find keeps its existing field layout. */
    static final class ActionBarScroll extends JScrollPane {
        final ActionRow row = new ActionRow();
        ActionBarScroll() {
            super(VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_AS_NEEDED);
            setViewportView(row); setBorder(null); setOpaque(false); getViewport().setOpaque(false);
            setFocusable(false); getHorizontalScrollBar().setFocusable(false);
            getHorizontalScrollBar().setUnitIncrement(16);
        }
        int heightAt(int width) {
            return row.getPreferredSize().height + (width < row.getPreferredSize().width ? getHorizontalScrollBar().getPreferredSize().height : 0);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, heightAt(getWidth())); }
        @Override public Dimension getPreferredSize() {
            var preferred = row.getPreferredSize();
            int width = getParent() == null ? preferred.width : getParent().getWidth();
            return new Dimension(preferred.width, heightAt(width));
        }
    }
    static final class ActionRow extends JPanel implements Scrollable {
        ActionRow() { super(new BorderLayout()); setOpaque(false); setFocusable(false); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(16, visible.width); }
        @Override public boolean getScrollableTracksViewportWidth() { return getParent() != null && getParent().getWidth() >= getPreferredSize().width; }
        @Override public boolean getScrollableTracksViewportHeight() { return true; }
    }

    /**
     * DOCUMENT: a hairline in the separator face's colour at every boundary between visible children, drawn inside the bar
     * side of the boundary (the document's own pixels and every child's bounds stay as laid out), or in the
     * {@code DOCUMENT_RULE_GAP} rows the layout leaves between them where the catalog has that gap.
     */
    static final class SplitSurface extends JPanel {
        SplitControl owner;
        @Override protected void paintChildren(Graphics graphics) {
            super.paintChildren(graphics);
            if (owner == null || !owner.document()) return;
            var children = java.util.Arrays.stream(getComponents()).filter(Component::isVisible).toList();
            if (children.size() < 2) return;
            var g = graphics.create();
            try {
                g.setColor(owner.context().faces().separators.rule(owner.context()));
                int stroke = ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE);
                int gap = ThemePainting.metric(owner.context(), DesignMetrics.Role.DOCUMENT_RULE_GAP);
                for (int i = 1; i < children.size(); i++) {
                    Component above = children.get(i - 1), below = children.get(i);
                    int y = gap > 0 ? above.getY() + above.getHeight() : owner.fills.contains(above) ? below.getY() : above.getY() + above.getHeight() - stroke;
                    g.fillRect(0, y, getWidth(), gap > 0 ? gap : stroke);
                }
            } finally { g.dispose(); }
        }
    }

    static final class Layout implements Composition.WidthLayout {
        final SplitControl owner;

        Layout(SplitControl owner) {
            this.owner = owner;
        }

        /** Manager columns (THREE_COLUMN, SIDEBAR_DETAIL) meet at their own hairline; other splits keep the column gap. */
        int gap() {
            var kind = ((Nodes.Split)owner.node).kind();
            return kind == Nodes.SplitKind.THREE_COLUMN || kind == Nodes.SplitKind.SIDEBAR_DETAIL ? 0 : ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        List<Component> visible(Container p) {
            return java.util.Arrays.stream(p.getComponents()).filter(Component::isVisible).toList();
        }

        /** DOCUMENT: the rows left for the rules between {@code count} visible children ({@code DOCUMENT_RULE_GAP} each). */
        int rules(int count) { return Math.max(0, count - 1) * ThemePainting.metric(owner.context(), DesignMetrics.Role.DOCUMENT_RULE_GAP); }

        int[] widths(int width, int n) {
            int[] result = new int[n];
            if (n == 0) return result;
            int available = Math.max(0, width - (n - 1) * gap());
            if (((Nodes.Split)owner.node).kind() == Nodes.SplitKind.THREE_COLUMN && n == 3) {
                int sidebar = ThemePainting.metric(owner.context(), DesignMetrics.Role.MANAGER_SIDEBAR_WIDTH), list = ThemePainting.metric(owner.context(), DesignMetrics.Role.MANAGER_LIST_WIDTH);
                double fit = Math.min(1.0, available / (double)(sidebar + list + sidebar));
                result[0] = (int)Math.floor(sidebar * fit); result[1] = (int)Math.floor(list * fit);
                result[2] = available - result[0] - result[1];
            } else if (((Nodes.Split)owner.node).kind() == Nodes.SplitKind.SIDEBAR_DETAIL && n == 2) {
                // The list column keeps the manager list width while the detail takes at least as much.
                int list = ThemePainting.metric(owner.context(), DesignMetrics.Role.MANAGER_LIST_WIDTH);
                result[0] = (int)Math.floor(list * Math.min(1.0, available / (2.0 * list)));
                result[1] = available - result[0];
            } else for (int i = 0; i < n; i++) result[i] = available / n + (i < available % n ? 1 : 0);
            return result;
        }

        /** DOCUMENT: the first visible Custom, Empty or Split child, which takes the height the others leave. */
        Component fill(List<Component> children) {
            for (Component c : children) if (owner.fills.contains(c)) return c;
            return null;
        }

        @Override
        public int height(Container p, int width) {
            if (owner.document()) {
                var children = visible(p); int h = rules(children.size());
                for (Component c : children) h += c instanceof ActionBarScroll scroll ? scroll.heightAt(width) : Composition.height(c, width);
                return h;
            }
            var children = visible(p); var widths = widths(width, children.size());
            int h = 0;
            for (int i = 0; i < children.size(); i++) h = Math.max(h, Composition.height(children.get(i), widths[i]));
            return h;
        }

        /** DOCUMENT: the bars at their height and the fill at its own minimum, so a long document never sets the window's. */
        @Override
        public Dimension minimumLayoutSize(Container p) {
            if (!owner.document()) return Composition.WidthLayout.super.minimumLayoutSize(p);
            var children = visible(p); Component fill = fill(children);
            int width = Math.max(1, p.getWidth()), h = rules(children.size());
            for (Component c : children) h += c == fill ? c.getMinimumSize().height : (c instanceof ActionBarScroll scroll ? scroll.heightAt(width) : Composition.height(c, width));
            return new Dimension(0, h);
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            if (owner.document()) {
                int w = 0;
                for (Component c : visible(p)) w = Math.max(w, c.getPreferredSize().width);
                return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
            }
            int w = 0;
            for (Component c : p.getComponents())
                if (c.isVisible()) w += c.getPreferredSize().width;
            w += Math.max(0, visible(p).size() - 1) * gap();
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container p) {
            if (owner.document()) {
                var children = visible(p); Component fill = fill(children);
                int width = p.getWidth(), gap = rules(2), others = rules(children.size());
                for (Component c : children) if (c != fill) others += (c instanceof ActionBarScroll scroll ? scroll.heightAt(width) : Composition.height(c, width));
                int y = 0;
                for (Component c : children) {
                    int h = c == fill ? Math.max(0, p.getHeight() - others) : (c instanceof ActionBarScroll scroll ? scroll.heightAt(width) : Composition.height(c, width));
                    c.setBounds(0, y, width, h);
                    y += h + gap;
                }
                return;
            }
            var children = visible(p); var widths = widths(p.getWidth(), children.size());
            int x = 0;
            for (int i = 0; i < children.size(); i++) {
                children.get(i).setBounds(x, 0, widths[i], p.getHeight());
                x += widths[i] + gap();
            }
        }
    }
}
