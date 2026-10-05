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

        RowControl(KitView view, Nodes.SettingRow node) {
            super(view, node, new JPanel());
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
            component.setBorder(new EmptyBorder(vertical, side, vertical, side));
            component.setLayout(new RowLayout(this));
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.forEach(c -> Composition.label(c, true));
            Stacks.mount(component, List.of(title, description, controls.getFirst().component));
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
                            p.getComponent(2).getPreferredSize().width,
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
                    ThemePainting.metric(
                            owner.context(),
                            owner.description.isVisible()
                                    ? DesignMetrics.Role.SETTING_DESCRIPTION_HEIGHT
                                    : DesignMetrics.Role.SETTING_HEIGHT),
                    i.top + i.bottom + Math.max(text, control));
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w =
                    owner.title.getPreferredSize().width
                            + gap()
                            + p.getInsets().left
                            + p.getInsets().right
                            + (p.getComponentCount() > 2
                                    ? p.getComponent(2).getPreferredSize().width
                                    : 0);
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
