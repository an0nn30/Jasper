package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;

/**
 * Basic semantic composition pulled forward for stable siblings; larger containers compose this
 * layout.
 */
final class Stacks {
    private Stacks() {}

    static final class StackControl extends ControlRegistry.Control {
        final Tile tile;

        StackControl(KitView view, Nodes.Stack node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            tile = new Tile(view.context());
            KitOwnership.mark(tile, node.id() + ".tile");
            refresh();
        }

        @Override
        void refresh() {
            state();
            var stack = (Nodes.Stack) node;
            component.setLayout(
                    stack.role() == Nodes.LayoutRole.HERO
                            ? new HeroLayout(this)
                            : new StackLayout(context(), stack.role()));
            component.revalidate();
            component.repaint();
        }

        @Override
        Container childParent(Nodes.Node child) {
            return ((Nodes.Stack) node).role() == Nodes.LayoutRole.HERO
                            && child instanceof Nodes.Icon
                    ? tile
                    : component;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            var wanted = new java.util.ArrayList<JComponent>();
            for (var c : controls)
                if (childParent(c.node) == tile) {
                    mount(tile, List.of(c.component));
                    wanted.add(tile);
                } else wanted.add(c.component);
            mount(component, wanted);
        }
    }

    static void mount(Container component, List<JComponent> wanted) {
        for (Component child : component.getComponents())
            if (!wanted.contains(child)) component.remove(child);
        for (int i = 0; i < wanted.size(); i++) {
            JComponent child = wanted.get(i);
            if (child.getParent() != component) component.add(child, i);
            else if (component.getComponentZOrder(child) != i)
                component.setComponentZOrder(child, i);
        }
    }

    static final class Tile extends JPanel {
        final KitContext context;
        final DesignMetrics.Role sizeRole;

        Tile(KitContext context) {
            this(context, DesignMetrics.Role.HERO_TILE);
        }

        Tile(KitContext context, DesignMetrics.Role sizeRole) {
            super(new GridBagLayout());
            this.context = context;
            this.sizeRole = sizeRole;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            int size = ThemePainting.metric(context, sizeRole);
            return new Dimension(size, size);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
                int radius = ThemePainting.metric(context, DesignMetrics.Role.HERO_TILE_RADIUS);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            } finally {
                g.dispose();
            }
        }
    }

    static final class HeroLayout implements Composition.WidthLayout {
        final StackControl owner;

        HeroLayout(StackControl owner) {
            this.owner = owner;
        }

        int gap(Component previous) {
            return ThemePainting.metric(
                    owner.context(),
                    previous == owner.tile
                            ? DesignMetrics.Role.HERO_TILE_GAP
                            : DesignMetrics.Role.DESCRIPTION_GAP);
        }

        @Override
        public int height(Container parent, int width) {
            int h = 0;
            Component previous = null;
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    if (previous != null) h += gap(previous);
                    h += Composition.height(c, width);
                    previous = c;
                }
            return h;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int width = 0;
            for (Component c : parent.getComponents())
                if (c.isVisible()) width = Math.max(width, c.getPreferredSize().width);
            return new Dimension(
                    width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        @Override
        public void layoutContainer(Container parent) {
            int y = 0;
            Component previous = null;
            for (Component c : parent.getComponents())
                if (c.isVisible()) {
                    if (previous != null) y += gap(previous);
                    int width = Math.min(parent.getWidth(), c.getPreferredSize().width),
                            h = Composition.height(c, width);
                    c.setBounds((parent.getWidth() - width) / 2, y, width, h);
                    y += h;
                    previous = c;
                }
        }
    }

    static final class StackLayout implements LayoutManager {
        final KitContext context;
        final Nodes.LayoutRole role;

        StackLayout(KitContext context, Nodes.LayoutRole role) {
            this.context = context;
            this.role = role;
        }

        boolean horizontal() {
            return switch (role) {
                case INLINE, ACTIONS, FOOTER, NAVIGATION -> true;
                default -> false;
            };
        }

        int gap() {
            return ThemePainting.metric(
                    context,
                    role == Nodes.LayoutRole.ACTIONS
                            ? DesignMetrics.Role.BUTTON_GAP
                            : horizontal()
                                    ? DesignMetrics.Role.COLUMN_GAP
                                    : DesignMetrics.Role.ROW_GAP);
        }

        @Override
        public void addLayoutComponent(String name, Component component) {}

        @Override
        public void removeLayoutComponent(Component component) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return measure(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return measure(parent, true);
        }

        Dimension measure(Container parent, boolean minimum) {
            int primary = 0, cross = 0, count = 0;
            for (Component child : parent.getComponents())
                if (child.isVisible()) {
                    Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                    primary += horizontal() ? size.width : size.height;
                    cross = Math.max(cross, horizontal() ? size.height : size.width);
                    count++;
                }
            primary += Math.max(0, count - 1) * gap();
            var insets = parent.getInsets();
            return new Dimension(
                    (horizontal() ? primary : cross) + insets.left + insets.right,
                    (horizontal() ? cross : primary) + insets.top + insets.bottom);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int x = insets.left, y = insets.top;
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right),
                    height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            for (Component child : parent.getComponents())
                if (child.isVisible()) {
                    var size = child.getPreferredSize();
                    if (horizontal()) {
                        int visible =
                                (int)
                                        java.util.Arrays.stream(parent.getComponents())
                                                .filter(Component::isVisible)
                                                .count();
                        int total =
                                java.util.Arrays.stream(parent.getComponents())
                                        .filter(Component::isVisible)
                                        .mapToInt(c -> c.getPreferredSize().width)
                                        .sum();
                        int available = Math.max(0, width - Math.max(0, visible - 1) * gap());
                        int fitted =
                                total > available && total > 0
                                        ? (int) ((long) size.width * available / total)
                                        : size.width;
                        child.setBounds(
                                x,
                                y + Math.max(0, (height - size.height) / 2),
                                fitted,
                                Math.min(height, size.height));
                        x += fitted + gap();
                    } else {
                        child.setBounds(x, y, width, size.height);
                        y += size.height + gap();
                    }
                }
        }
    }
}
