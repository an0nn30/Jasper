package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Domain-supplied dependency state; descriptions wrap around the trailing control. */
final class SettingRows {
    private SettingRows() {}

    static final class RowControl extends ControlRegistry.Control {
        final Composition.WrappedText title, description;
        boolean panelFooter;

        RowControl(KitView view, Nodes.SettingRow node) {
            super(view, node, new SearchHighlight(view.context()));
            title = Composition.text(node.id() + ".title");
            description = Composition.text(node.id() + ".description");
            component.setOpaque(false);
            component.add(title);
            component.add(description);
            refresh();
        }

        @Override
        void refresh() {
            state();
            var row = (Nodes.SettingRow) node;
            title.text(row.title());
            description.text(row.description());
            description.setVisible(!row.description().isEmpty());
            Composition.style(title, context(), Typography.Role.LABEL, SemanticColor.TEXT_PRIMARY);
            Composition.style(
                    description, context(), Typography.Role.CAPTION, SemanticColor.TEXT_TERTIARY);
            title.setEnabled(component.isEnabled());
            description.setEnabled(component.isEnabled());
            int side = ThemePainting.metric(context(), DesignMetrics.Role.CARD_PADDING),
                    vertical =
                            ThemePainting.metric(
                                    context(), DesignMetrics.Role.SETTING_VERTICAL_PADDING);
            component.setBorder(panelFooter ? new EmptyBorder(0,0,0,0) : new EmptyBorder(vertical, side, vertical, side));
            component.setLayout(new RowLayout(this));
            ((SearchHighlight) component).highlight(row.highlighted());
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.forEach(c -> Composition.label(c, true));
            Stacks.mount(component, List.of(title, description, controls.getFirst().component));
        }
    }

    /** A row surface that paints the host's search highlight behind its content, in the theme's selection color. */
    static final class SearchHighlight extends JPanel {
        final KitContext context;
        boolean highlighted;

        SearchHighlight(KitContext context) {
            this.context = context;
        }

        void highlight(boolean value) {
            highlighted = value;
            getAccessibleContext().setAccessibleDescription(value ? "Matches search" : null);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (highlighted) {
                var g = (Graphics2D) graphics.create();
                try {
                    ThemePainting.antialias(g);
                    g.setColor(ThemePainting.color(context, SemanticColor.SELECTION_BG));
                    int r = ThemePainting.metric(context, DesignMetrics.Role.CONTROL_RADIUS);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), r * 2, r * 2);
                } finally {
                    g.dispose();
                }
            }
            super.paintComponent(graphics);
        }
    }

    static final class RowLayout implements Composition.WidthLayout {
        final RowControl owner;

        RowLayout(RowControl owner) {
            this.owner = owner;
        }

        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        int trailing(Container p, int width) {
            return p.getComponentCount() > 2
                    ? Math.min(
                            Math.max(p.getComponent(2).getPreferredSize().width, ((Nodes.SettingRow)owner.node).control() instanceof Nodes.Choice ? ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_CHOICE_WIDTH) : 0),
                            Math.max(
                                    0,
                                    (width - p.getInsets().left - p.getInsets().right - gap()) / 2))
                    : 0;
        }

        @Override
        public int height(Container p, int width) {
            Insets i = p.getInsets();
            int tw = trailing(p, width), w = Math.max(0, width - i.left - i.right - tw - gap());
            int text =
                    owner.title.height(w)
                            + (owner.description.isVisible() ? owner.description.height(w) : 0);
            int control = p.getComponentCount() > 2 ? Composition.height(p.getComponent(2), tw) : 0;
            return Math.max(
                    owner.panelFooter ? 0 : ThemePainting.metric(
                            owner.context(),
                            owner.description.isVisible()
                                    ? DesignMetrics.Role.SETTING_DESCRIPTION_HEIGHT
                                    : DesignMetrics.Role.SETTING_HEIGHT),
                    i.top + i.bottom + Math.max(text, control));
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int control = p.getComponentCount() > 2 ? p.getComponent(2).getPreferredSize().width : 0;
            // The trailing control takes at most half the row, so a wide control (a list) needs twice its width.
            int w =
                    Math.max(owner.title.getPreferredSize().width + gap() + control, 2 * control + gap())
                            + p.getInsets().left
                            + p.getInsets().right;
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container p) {
            Insets i = p.getInsets();
            int tw = trailing(p, p.getWidth()),
                    w = Math.max(0, p.getWidth() - i.left - i.right - tw - gap()),
                    th = owner.title.height(w),
                    dh = owner.description.isVisible() ? owner.description.height(w) : 0;
            int y = Math.max(i.top, (p.getHeight() - th - dh) / 2);
            owner.title.setBounds(i.left, y, w, th);
            owner.description.setBounds(i.left, y + th, w, dh);
            if (p.getComponentCount() > 2) {
                Component c = p.getComponent(2);
                int h = Composition.height(c, tw);
                c.setBounds(
                        p.getWidth() - i.right - tw,
                        Math.max(i.top, (p.getHeight() - h) / 2),
                        tw,
                        h);
            }
        }
    }
}
