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
            super(view, node, new JPanel());
            component.setOpaque(false);
            refresh();
        }

        @Override
        void refresh() {
            state();
            component.setLayout(new Layout(this));
        }

        /** DOCUMENT: the children that may fill the remaining height (Custom and Empty), in order. */
        final java.util.Set<Component> fills = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        @Override
        void children(List<ControlRegistry.Control> controls) {
            fills.clear();
            for (var c : controls) if (c.node instanceof Nodes.Custom || c.node instanceof Nodes.Empty) fills.add(c.component);
            Stacks.mount(component, controls.stream().map(c -> c.component).toList());
        }

        boolean document() { return ((Nodes.Split) node).kind() == Nodes.SplitKind.DOCUMENT; }
    }

    static final class Layout implements Composition.WidthLayout {
        final SplitControl owner;

        Layout(SplitControl owner) {
            this.owner = owner;
        }

        int gap() {
            return ((Nodes.Split)owner.node).kind() == Nodes.SplitKind.THREE_COLUMN ? 0 : ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        List<Component> visible(Container p) {
            return java.util.Arrays.stream(p.getComponents()).filter(Component::isVisible).toList();
        }

        int[] widths(int width, int n) {
            int[] result = new int[n];
            if (n == 0) return result;
            int available = Math.max(0, width - (n - 1) * gap());
            if (((Nodes.Split)owner.node).kind() == Nodes.SplitKind.THREE_COLUMN && n == 3) {
                int sidebar = ThemePainting.metric(owner.context(), DesignMetrics.Role.MANAGER_SIDEBAR_WIDTH), list = ThemePainting.metric(owner.context(), DesignMetrics.Role.MANAGER_LIST_WIDTH);
                double fit = Math.min(1.0, available / (double)(sidebar + list + sidebar));
                result[0] = (int)Math.floor(sidebar * fit); result[1] = (int)Math.floor(list * fit);
                result[2] = available - result[0] - result[1];
            } else for (int i = 0; i < n; i++) result[i] = available / n + (i < available % n ? 1 : 0);
            return result;
        }

        /** DOCUMENT: the first visible Custom or Empty child, which takes the height the others leave. */
        Component fill(List<Component> children) {
            for (Component c : children) if (owner.fills.contains(c)) return c;
            return null;
        }

        @Override
        public int height(Container p, int width) {
            if (owner.document()) {
                int h = 0;
                for (Component c : visible(p)) h += Composition.height(c, width);
                return h;
            }
            var children = visible(p); var widths = widths(width, children.size());
            int h = 0;
            for (int i = 0; i < children.size(); i++) h = Math.max(h, Composition.height(children.get(i), widths[i]));
            return h;
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
                int width = p.getWidth(), others = 0;
                for (Component c : children) if (c != fill) others += Composition.height(c, width);
                int y = 0;
                for (Component c : children) {
                    int h = c == fill ? Math.max(0, p.getHeight() - others) : Composition.height(c, width);
                    c.setBounds(0, y, width, h);
                    y += h;
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
