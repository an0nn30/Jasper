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
        java.util.List<ControlRegistry.Control> controls = java.util.List.of();
        /** The card's shared label column (the label policy's), or -1 for the fixed FORM_LABEL_WIDTH column. */
        int column = -1;
        /** A trailing PORT field's own label, mounted only when the label policy gives one. */
        final JLabel port = Labels.literal();

        RowControl(KitView view, Nodes.FormRow node) {
            super(view, node, new SettingRows.SearchHighlight(view.context()));
            caption = Composition.text(node.id() + ".caption");
            KitOwnership.mark(label, node.id() + ".label");
            KitOwnership.mark(port, node.id() + ".port");
            component.setOpaque(false);
            component.add(label);
            component.add(caption);
            refresh();
        }

        @Override
        void refresh() {
            state();
            var row = (Nodes.FormRow) node;
            label.setText(context().faces().labels.label(row.label()));
            ((Labels.LiteralLabel)label).wrap = true;
            caption.text(row.caption());
            caption.setVisible(!row.caption().isEmpty());
            // A caption is one line under its label, shortened at its end; its whole text is the tooltip.
            caption.single = context().faces().labels.singleCaption();
            caption.setToolTipText(row.caption().isEmpty() ? null : row.caption());
            Composition.style(
                    label, context(), row.control() instanceof Nodes.Choice choice && choice.face().filter(face -> face.density()==Nodes.PickerDensity.COMPACT).isPresent()?Typography.Role.PICKER_COMPACT:Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
            label.setForeground(context().faces().labels.labelInk(context(), label.getForeground()));
            context().faces().labels.styleCaption(caption, context());
            port.setText(context().faces().labels.label("Port")); port.setFont(label.getFont()); port.setForeground(label.getForeground());
            port.setEnabled(component.isEnabled());
            label.setEnabled(component.isEnabled());
            caption.setEnabled(component.isEnabled());
            component.setLayout(new RowLayout(this));
            ((SettingRows.SearchHighlight) component).highlight(row.highlighted());
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            this.controls = List.copyOf(controls);
            for (var c : controls) {
                Composition.label(c, true);
                if (c instanceof Labels.TextControl && c.component instanceof Labels.LiteralLabel label
                        && c.node instanceof Nodes.Text text) {
                    label.wrap = text.role() == Nodes.TextRole.CAPTION || text.role() == Nodes.TextRole.DESCRIPTION || text.role() == Nodes.TextRole.SUPPORTING;
                }
            }
            boolean ported = controls.size() > 1 && controls.get(1).node instanceof Nodes.Field field && field.kind() == Nodes.FieldKind.PORT
                && context().faces().labels.portLabel();
            Stacks.mount(
                    component,
                    java.util.stream.Stream.concat(java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(label, caption),
                                    controls.stream().map(c -> c.component)),
                                    ported ? java.util.stream.Stream.of((JComponent) port) : java.util.stream.Stream.<JComponent>empty())
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
            return owner.column >= 0 ? owner.column : ThemePainting.metric(owner.context(), DesignMetrics.Role.FORM_LABEL_WIDTH);
        }

        /** The "Port:" label's width and its gap, when it is mounted after the trailing field. */
        int portWidth(Container p) {
            return p.getComponentCount() > 4 && p.getComponent(4) == owner.port ? owner.port.getPreferredSize().width + gap() : 0;
        }

        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP);
        }

        int trailing(Container p,int width) {
            return p.getComponentCount() > 3 && p.getComponent(3).isVisible()
                    ? Math.min(
                            p.getComponent(3).getPreferredSize().width + portWidth(p),
                            Math.max(0, (width - labelWidth() - 2 * gap()) / 2))
                    : 0;
        }

        boolean trailingVisible(Container p) { return p.getComponentCount()>3 && p.getComponent(3).isVisible(); }
        /**
         * A label with a caption beside a single-line input (a field or a dropdown): the two lines sit as one block centered
         * on the input, with no caption offset, as the New Tunnel reference shows. A taller control (an ordered list) keeps
         * the label top-aligned at the caption offset.
         */
        boolean besideInput() {
            var control = ((Nodes.FormRow) owner.node).control();
            return owner.caption.isVisible() && (control instanceof Nodes.Field field && field.kind() != Nodes.FieldKind.TEXTAREA
                || control instanceof Nodes.Choice choice && (choice.kind() == Nodes.ChoiceKind.DROPDOWN || choice.kind() == Nodes.ChoiceKind.PICKER || choice.kind() == Nodes.ChoiceKind.SEARCHABLE));
        }
        int captionOffset() { return besideInput() ? 0 : ThemePainting.metric(owner.context(), DesignMetrics.Role.CAPTION_TOP_OFFSET); }
        boolean stacked(Container p,int width) {
            int lane=p.getComponentCount()>2?Math.min(p.getComponent(2).getPreferredSize().width,SurfacePanel.bodyLaneWidth(owner.context())):0;
            return width<labelWidth()+gap()+lane+(trailingVisible(p)?gap()+Math.min(p.getComponent(3).getPreferredSize().width,SurfacePanel.bodyLaneWidth(owner.context())):0);
        }
        int textHeight(int width) {
            return Composition.height(owner.label,width)+(owner.caption.isVisible()?owner.caption.height(width)
                +ThemePainting.metric(owner.context(),DesignMetrics.Role.CAPTION_TOP_OFFSET):0);
        }
        @Override
        public int height(Container p, int width) {
            if(stacked(p,width)) {
                int height=textHeight(width);
                for(int i=2;i<p.getComponentCount();i++)if(p.getComponent(i).isVisible())height+=gap()+Composition.height(p.getComponent(i),width);
                return height;
            }
            int trailing = trailing(p,width),
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
                                    ? owner.caption.height(labelWidth()) + captionOffset()
                                    : 0), shift = 0;
            // The row holds its whole label block; beside an input that is taller than it, the input moves down to centre on it.
            if (p.getComponentCount() > 2) {
                int control = Composition.height(p.getComponent(2), w);
                shift = inputShift(firstLine(2, control), h);
                h = Math.max(h, control + shift);
            }
            if (p.getComponentCount() > 3)
                h = Math.max(h, Composition.height(p.getComponent(3), trailing - portWidth(p)) + shift);
            return h;
        }

        /** How far an input sits below its row's top: half of what its label and caption are taller than its first line. */
        int inputShift(int line, int block) { return besideInput() ? Math.max(0, block - line) / 2 : 0; }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            int w = labelWidth() + gap();
            for (int i = 2; i < p.getComponentCount(); i++)
                w += p.getComponent(i).getPreferredSize().width + (i > 2 ? gap() : 0);
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }

        /** The control's first line: labels and trailing text centre on it, not on the whole (error-grown) row. */
        int firstLine(int index, int height) {
            int line = owner.controls.size() > index - 2 ? owner.controls.get(index - 2).firstLineHeight() : -1;
            if (line <= 0) line = Math.min(height, ThemePainting.height(owner.context(), DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY));
            return Math.min(height, line);
        }

        @Override
        public void layoutContainer(Container p) {
            if(stacked(p,p.getWidth())) {
                int width=p.getWidth(),labelHeight=Composition.height(owner.label,width);
                owner.label.setBounds(0,0,width,labelHeight);
                owner.caption.setBounds(0,labelHeight+ThemePainting.metric(owner.context(),DesignMetrics.Role.CAPTION_TOP_OFFSET),width,owner.caption.height(width));
                int y=textHeight(width);
                for(int i=2;i<p.getComponentCount();i++)if(p.getComponent(i).isVisible()) {
                    int height=Composition.height(p.getComponent(i),width);y+=gap();
                    p.getComponent(i).setBounds(0,y,width,height);y+=height;
                }
                return;
            }
            int lw = Math.min(labelWidth(), p.getWidth()),
                    trailing = trailing(p,p.getWidth()),
                    x = lw + gap(),
                    w = Math.max(0, p.getWidth() - x - (trailing > 0 ? trailing + gap() : 0));
            int controlHeight = p.getComponentCount() > 2 ? Composition.height(p.getComponent(2), w) : p.getHeight();
            int line = p.getComponentCount() > 2 ? firstLine(2, controlHeight) : p.getHeight();
            int lh = Composition.height(owner.label, lw),
                    shift = inputShift(line, lh + (owner.caption.isVisible() ? owner.caption.height(lw) : 0)),
                    y =
                            besideInput()
                                    // Centred on the control's first line, so an error growing the row moves nothing.
                                    ? (Math.max(line, lh + owner.caption.height(lw)) - lh - owner.caption.height(lw)) / 2
                                    : owner.caption.isVisible()
                                    ? ThemePainting.metric(
                                            owner.context(), DesignMetrics.Role.CAPTION_TOP_OFFSET)
                                    : Math.max(0, (line - lh) / 2);
            if (p.getComponentCount() > 2) p.getComponent(2).setBounds(x, shift, w, controlHeight);
            owner.label.setBounds(0, y, lw, lh);
            owner.caption.setBounds(0, y + lh, lw, owner.caption.height(lw));
            if (p.getComponentCount() > 3) {
                int ported = portWidth(p), tx = x + w + gap();
                if (ported > 0) {
                    var size = owner.port.getPreferredSize();
                    owner.port.setBounds(tx, shift + Math.max(0, (line - size.height) / 2), size.width, size.height);
                }
                int th = Composition.height(p.getComponent(3), trailing - ported);
                // Shorter trailing content (a unit or note) centres on the control's first line as the label does.
                p.getComponent(3).setBounds(tx + ported, shift + (th < line ? (line - th) / 2 : 0), trailing - ported, th);
            }
        }
    }
}
