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
                            : ThemePainting.metric(context(), DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(context(), DesignMetrics.Role.HAIRLINE);
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
            controls.stream().filter(c -> c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.SEGMENTED).forEach(c -> Composition.label(c, true));
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
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE);
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
                    if (previous == null && !setting) y += pad();
                    else if (previous != null && setting != before)
                        // The outer card owns a stroke; an internal content/setting transition
                        // owns only its content padding. The setting row accounts for its divider.
                        y += ThemePainting.metric(owner.context(), DesignMetrics.Role.CARD_PADDING);
                    else if (previous != null && !setting)
                        y += ThemePainting.metric(owner.context(), DesignMetrics.Role.MIXED_ROW_GAP);
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

        @Override public Dimension getMinimumSize() {
            var minimum=super.getMinimumSize();
            int fixed=settings.stream().filter(Component::isVisible).mapToInt(c->c.getMinimumSize().width).max().orElse(0);
            return new Dimension(Math.max(minimum.width,fixed+getInsets().left+getInsets().right),minimum.height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CARD));
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), r * 2, r * 2));
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                g.draw(new RoundRectangle2D.Double(.5, .5, getWidth() - 1, getHeight() - 1, r * 2, r * 2));
                Component previous = null;
                for (Component child : getComponents()) if (child.isVisible()) {
                    if (previous != null && (settings.contains(child) || settings.contains(previous)))
                        g.drawLine(1, child.getY(), getWidth() - 2, child.getY());
                    previous = child;
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
