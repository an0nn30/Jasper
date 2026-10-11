package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;

/** Fixed label grid, flexible control and automatic trailing cell. */
final class FormRows {
    private FormRows() {}

    static final class RowControl extends ControlRegistry.Control {
        final JLabel label = Labels.literal();
        final Composition.WrappedText caption;

        RowControl(KitView view, Nodes.FormRow node) {
            super(view, node, new SettingRows.SearchHighlight(view.context()));
            caption = Composition.text(node.id() + ".caption");
            KitOwnership.mark(label, node.id() + ".label");
            component.setOpaque(false);
            component.add(label);
            component.add(caption);
            refresh();
        }

        @Override
        void refresh() {
            state();
            var row = (Nodes.FormRow) node;
            label.setText(row.label());
            ((Labels.LiteralLabel)label).wrap = true;
            caption.text(row.caption());
            caption.setVisible(!row.caption().isEmpty());
            Composition.style(
                    label, context(), row.control() instanceof Nodes.Choice choice && choice.face().filter(face -> face.density()==Nodes.PickerDensity.COMPACT).isPresent()?Typography.Role.PICKER_COMPACT:Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
            Composition.style(
                    caption, context(), Typography.Role.CAPTION, SemanticColor.TEXT_TERTIARY);
            label.setEnabled(component.isEnabled());
            caption.setEnabled(component.isEnabled());
            component.setLayout(new RowLayout(this));
            ((SettingRows.SearchHighlight) component).highlight(row.highlighted());
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            for (var c : controls) {
                Composition.label(c, true);
                if (c instanceof Labels.TextControl && c.component instanceof Labels.LiteralLabel label
                        && c.node instanceof Nodes.Text text) {
                    label.wrap = text.role() == Nodes.TextRole.CAPTION || text.role() == Nodes.TextRole.DESCRIPTION || text.role() == Nodes.TextRole.SUPPORTING;
                }
            }
            Stacks.mount(
                    component,
                    java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(label, caption),
                                    controls.stream().map(c -> c.component))
                            .toList());
            label.setLabelFor(controls.getFirst().focusTarget());
        }
    }

    static final class RowLayout implements Composition.WidthLayout {
        final RowControl owner;

        RowLayout(RowControl owner) {
            this.owner = owner;
        }

        int labelWidth() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.FORM_LABEL_WIDTH);
        }

        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        int trailing(Container p) {
            return p.getComponentCount() > 3 && p.getComponent(3).isVisible()
                    ? Math.min(
                            p.getComponent(3).getPreferredSize().width,
                            Math.max(0, (p.getWidth() - labelWidth() - 2 * gap()) / 2))
                    : 0;
        }

        @Override
        public int height(Container p, int width) {
            int trailing = trailing(p),
                    w =
                            Math.max(
                                    0,
                                    width
                                            - labelWidth()
                                            - gap()
                                            - (trailing > 0 ? trailing + gap() : 0));
            int h =
                    Composition.height(owner.label, Math.min(labelWidth(), width))
                            + (owner.caption.isVisible()
                                    ? owner.caption.height(labelWidth())
                                            + ThemePainting.metric(
                                                    owner.context(),
                                                    DesignMetrics.Role.CAPTION_TOP_OFFSET)
                                    : 0);
            if (p.getComponentCount() > 2)
                h = Math.max(h, Composition.height(p.getComponent(2), w));
            if (p.getComponentCount() > 3)
                h = Math.max(h, Composition.height(p.getComponent(3), trailing));
            return h;
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w = labelWidth() + gap();
            for (int i = 2; i < p.getComponentCount(); i++)
                w += p.getComponent(i).getPreferredSize().width + (i > 2 ? gap() : 0);
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        @Override
        public void layoutContainer(Container p) {
            int lw = Math.min(labelWidth(), p.getWidth()),
                    trailing = trailing(p),
                    x = lw + gap(),
                    w = Math.max(0, p.getWidth() - x - (trailing > 0 ? trailing + gap() : 0));
            int lh = Composition.height(owner.label, lw),
                    y =
                            owner.caption.isVisible()
                                    ? ThemePainting.metric(
                                            owner.context(), DesignMetrics.Role.CAPTION_TOP_OFFSET)
                                    : Math.max(0, (p.getHeight() - lh) / 2);
            owner.label.setBounds(0, y, lw, lh);
            owner.caption.setBounds(0, y + lh, lw, owner.caption.height(lw));
            if (p.getComponentCount() > 2)
                p.getComponent(2).setBounds(x, 0, w, Composition.height(p.getComponent(2), w));
            if (p.getComponentCount() > 3)
                p.getComponent(3)
                        .setBounds(
                                x + w + gap(),
                                0,
                                trailing,
                                Composition.height(p.getComponent(3), trailing));
        }
    }
}
