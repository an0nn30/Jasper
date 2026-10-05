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

        @Override
        void children(List<ControlRegistry.Control> controls) {
            Stacks.mount(component, controls.stream().map(c -> c.component).toList());
        }
    }

    static final class Layout implements Composition.WidthLayout {
        final SplitControl owner;

        Layout(SplitControl owner) {
            this.owner = owner;
        }

        int gap() {
            return ((Nodes.Split)owner.node).kind() == Nodes.SplitKind.THREE_COLUMN ? 0 : ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        @Override
        public int height(Container p, int width) {
            int h = 0,
                    n = Math.max(1, p.getComponentCount()),
                    w = Math.max(0, (width - (n - 1) * gap()) / n);
            for (Component c : p.getComponents())
                if (c.isVisible()) h = Math.max(h, Composition.height(c, w));
            return h;
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w = 0;
            for (Component c : p.getComponents())
                if (c.isVisible()) w += c.getPreferredSize().width;
            w += Math.max(0, p.getComponentCount() - 1) * gap();
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container p) {
            var visible =
                    java.util.Arrays.stream(p.getComponents())
                            .filter(Component::isVisible)
                            .toList();
            int n = visible.size();
            if (n == 0) return;
            int available = Math.max(0, p.getWidth() - (n - 1) * gap()), x = 0;
            for (int i = 0; i < n; i++) {
                int w;
                if (((Nodes.Split)owner.node).kind() == Nodes.SplitKind.THREE_COLUMN && n == 3) {
                    int sidebar = ThemePainting.metric(owner.context(), DesignMetrics.Role.MANAGER_SIDEBAR_WIDTH), list = ThemePainting.metric(owner.context(), DesignMetrics.Role.MANAGER_LIST_WIDTH);
                    double fit = Math.min(1.0, available / (double)(sidebar + list + sidebar));
                    int first = (int)Math.floor(sidebar * fit), second = (int)Math.floor(list * fit);
                    w = i == 0 ? first : i == 1 ? second : available - first - second;
                } else w = available / n + (i < available % n ? 1 : 0);
                visible.get(i).setBounds(x, 0, w, p.getHeight());
                x += w + gap();
            }
        }
    }
}
