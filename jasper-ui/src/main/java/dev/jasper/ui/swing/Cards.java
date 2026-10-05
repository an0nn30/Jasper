package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Grouped card surfaces and section labels. */
final class Cards {
    private Cards() {}

    static final class CardControl extends ControlRegistry.Control {
        CardControl(KitView view, Nodes.Card node) {
            super(view, node, new CardSurface(view.context()));
            refresh();
        }

        @Override
        void refresh() {
            state();
            int pad =
                    ((Nodes.Card) node).kind() != Nodes.CardKind.FORM
                            ? 0
                            : ThemePainting.metric(context(), DesignMetrics.Role.CARD_PADDING);
            component.setBorder(new EmptyBorder(pad, pad, pad, pad));
            component.setLayout(
                    ((Nodes.Card) node).kind() == Nodes.CardKind.MIXED
                            ? new MixedLayout(this)
                            : new Composition.Vertical(
                                    ((Nodes.Card) node).kind() == Nodes.CardKind.SETTINGS
                                            ? 0
                                            : ThemePainting.metric(
                                                    context(), DesignMetrics.Role.ROW_GAP)));
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            ((CardSurface) component).settings =
                    controls.stream()
                            .filter(c -> c.node instanceof Nodes.SettingRow)
                            .map(c -> c.component)
                            .toList();
            Stacks.mount(component, controls.stream().map(c -> c.component).toList());
        }
    }

    static final class MixedLayout implements Composition.WidthLayout {
        final CardControl owner;

        MixedLayout(CardControl owner) {
            this.owner = owner;
        }

        int pad() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.CARD_PADDING);
        }

        boolean setting(Component c) {
            return ((CardSurface) owner.component).settings.contains(c);
        }

        int arrange(Container p, int width, boolean layout) {
            int y = 0;
            Component previous = null;
            for (Component c : p.getComponents())
                if (c.isVisible()) {
                    boolean setting = setting(c), before = previous != null && setting(previous);
                    if (previous == null && !setting || previous != null && setting != before)
                        y += pad();
                    else if (previous != null && !setting)
                        y += ThemePainting.metric(owner.context(), DesignMetrics.Role.ROW_GAP);
                    int inset = setting ? 0 : pad(),
                            w = Math.max(0, width - 2 * inset),
                            h = Composition.height(c, w);
                    if (layout) c.setBounds(inset, y, w, h);
                    y += h;
                    previous = c;
                }
            if (previous != null && !setting(previous)) y += pad();
            return y;
        }

        @Override
        public int height(Container p, int width) {
            return arrange(p, width, false);
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w = 0;
            for (Component c : p.getComponents())
                if (c.isVisible())
                    w = Math.max(w, c.getPreferredSize().width + (setting(c) ? 0 : 2 * pad()));
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container p) {
            arrange(p, p.getWidth(), true);
        }
    }

    static final class CardSurface extends JPanel {
        List<JComponent> settings = List.of();
        final KitContext context;

        CardSurface(KitContext context) {
            this.context = context;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CARD));
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), r, r));
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                g.draw(new RoundRectangle2D.Double(.5, .5, getWidth() - 1, getHeight() - 1, r, r));
                Component[] children = getComponents();
                for (int i = 1; i < children.length; i++)
                    if (children[i].isVisible()
                            && children[i - 1].isVisible()
                            && (children[i].getName() != null)) {
                        int y = children[i].getY();
                        if (settings.contains(children[i]) || settings.contains(children[i - 1]))
                            g.drawLine(1, y, getWidth() - 2, y);
                    }
            } finally {
                g.dispose();
            }
        }
    }

    static final class SectionControl extends ControlRegistry.Control {
        final JLabel title = Labels.literal();

        SectionControl(KitView view, Nodes.Section node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            KitOwnership.mark(title, node.id() + ".title");
            component.add(title);
            refresh();
        }

        @Override
        void refresh() {
            state();
            title.setText(((Nodes.Section) node).title());
            title.setEnabled(component.isEnabled());
            Composition.style(
                    title, context(), Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY);
            component.setLayout(
                    new Composition.Vertical(
                            ThemePainting.metric(context(), DesignMetrics.Role.SECTION_LABEL_GAP)));
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            Stacks.mount(component, List.of(title, controls.getFirst().component));
        }
    }
}
