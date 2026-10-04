package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;

/** Persistent documents, guarded numeric edits and literal labels/validation for ordinary fields. */
final class TextFields {
    private TextFields() {}
    static final class FieldControl extends ControlRegistry.Control {
        final JTextComponent input;
        final JLabel label = Labels.literal(), error = Labels.literal(), leading = Labels.literal();
        final FieldSurface surface;
        final JPanel line = new JPanel(new BorderLayout()), container;
        final Buttons.ButtonControl browse;
        final JScrollPane scroll;
        final DocumentListener listener;
        boolean applying;
        boolean notifying;
        long valueRevision;
        FieldControl(KitView view, Nodes.Field node) {
            super(view, node, new JPanel(new BorderLayout())); container = (JPanel)component;
            input = node.kind() == Nodes.FieldKind.TEXTAREA ? new JTextArea(3, 16) : new JTextField(node.kind() == Nodes.FieldKind.PORT ? 6 : 16);
            if (input instanceof JTextArea area) { area.setLineWrap(true); area.setWrapStyleWord(true); }
            input.setOpaque(false); input.setBorder(new EmptyBorder(0, 0, 0, 0)); KitOwnership.mark(input, node.id() + ".input");
            if (node.kind() == Nodes.FieldKind.PORT) ((AbstractDocument)input.getDocument()).setDocumentFilter(new NumericFilter());
            surface = new FieldSurface(context(), input, node.kind() == Nodes.FieldKind.TEXTAREA);
            var focus = new ThemePainting.Focus(input); surface.focus = focus; own(focus);
            if (node.kind() == Nodes.FieldKind.TEXTAREA) {
                scroll = new JScrollPane(input); scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
                scroll.setOpaque(false); scroll.getViewport().setOpaque(false); surface.add(scroll, BorderLayout.CENTER);
            } else { scroll = null; surface.add(input, BorderLayout.CENTER); }
            if (node.kind() == Nodes.FieldKind.SEARCH || node.kind() == Nodes.FieldKind.PORT) surface.add(leading, BorderLayout.WEST);
            KitOwnership.mark(label, node.id() + ".label"); KitOwnership.mark(error, node.id() + ".error"); KitOwnership.mark(leading, node.id() + ".leading");
            label.setLabelFor(input); label.setText(node.label());
            line.setOpaque(false); line.add(surface, BorderLayout.CENTER);
            if (node.kind() == Nodes.FieldKind.PATH) {
                browse = new Buttons.ButtonControl(view, browseNode(node)); line.add(browse.component, BorderLayout.EAST); own(browse);
            } else browse = null;
            container.setOpaque(false); container.add(label, BorderLayout.NORTH); container.add(line, BorderLayout.CENTER); container.add(error, BorderLayout.SOUTH);
            listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { changed(); }
                @Override public void removeUpdate(DocumentEvent e) { changed(); }
                @Override public void changedUpdate(DocumentEvent e) { changed(); }
                private void changed() {
                    if (applying) return;
                    notifying = true;
                    try { emit(new UiEvent.Edited(FieldControl.this.node.id(), input.getText())); }
                    finally { notifying = false; }
                }
            };
            input.getDocument().addDocumentListener(listener); own(() -> input.getDocument().removeDocumentListener(listener));
            setValue(node.value());
            refresh();
        }
        private Nodes.Button browseNode(Nodes.Field field) {
            return new Nodes.Button(field.id() + ".browse", field.state(), "Browse…", "", Nodes.ButtonKind.SECONDARY,
                field.trailingAction().isBlank() ? field.id() + ".browse" : field.trailingAction(), "Browse…");
        }
        @Override void update(Nodes.Node next) {
            var previous = (Nodes.Field)node; var field = (Nodes.Field)next;
            if (!previous.value().equals(field.value()) || notifying && !input.getText().equals(field.value())) {
                long revision = ++valueRevision;
                if (!input.getText().equals(field.value())) {
                    if (notifying) {
                        // A synchronous host may normalize Edited text. Swing forbids document writes
                        // during notification; publish the descriptor now and apply the latest value later.
                        EventQueue.invokeLater(() -> {
                            if (!closed && valueRevision == revision) setValue(field.value());
                        });
                    } else setValue(field.value());
                }
            }
            super.update(next);
        }
        private void setValue(String value) {
            if (input.getText().equals(value)) return;
            applying = true;
            try { input.setText(value); } finally { applying = false; }
        }
        @Override JComponent focusTarget() { return input; }
        @Override void refresh() {
            var field = (Nodes.Field)node; state(); input.setEnabled(component.isEnabled());
            input.setEditable(component.isEnabled()); surface.setEnabled(component.isEnabled());
            input.setCursor(Cursor.getPredefinedCursor(component.isEnabled() ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR));
            if (scroll != null) {
                scroll.setEnabled(component.isEnabled());
                scroll.getVerticalScrollBar().setEnabled(component.isEnabled());
                scroll.getVerticalScrollBar().setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_INPUT));
                scroll.getVerticalScrollBar().setForeground(ThemePainting.color(context(), SemanticColor.BORDER_INPUT));
            }
            var role = field.kind() == Nodes.FieldKind.PORT || field.kind() == Nodes.FieldKind.PATH ? Typography.Role.MONO : Typography.Role.BODY;
            input.setFont(ThemePainting.font(context(), role));
            input.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            input.setDisabledTextColor(input.getForeground()); input.setCaretColor(input.getForeground());
            input.setSelectionColor(ThemePainting.color(context(), SemanticColor.SELECTION_BG));
            input.setSelectedTextColor(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            input.putClientProperty("JTextField.placeholderText", field.hint()); surface.hint = field.hint(); surface.role = role;
            input.getAccessibleContext().setAccessibleName(field.label());
            input.getAccessibleContext().setAccessibleDescription(field.state().error().isEmpty() ? field.hint() : field.state().error());
            label.setText(field.label()); label.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY)); label.setEnabled(component.isEnabled());
            error.setText(field.state().error()); error.setVisible(!field.state().error().isEmpty());
            error.setEnabled(component.isEnabled());
            error.setFont(ThemePainting.font(context(), Typography.Role.CAPTION)); error.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ERROR));
            surface.error = !field.state().error().isEmpty();
            leading.setFont(ThemePainting.font(context(), Typography.Role.MONO)); leading.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PLACEHOLDER));
            leading.setText(field.kind() == Nodes.FieldKind.PORT ? ":" : "");
            if (field.kind() == Nodes.FieldKind.SEARCH) leading.setIcon(context().icons().icon("search", context().theme().semantic(), SemanticColor.TEXT_PLACEHOLDER,
                com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CONTROL))));
            int gap = ThemePainting.metric(context(), field.kind() == Nodes.FieldKind.PORT ? DesignMetrics.Role.PORT_PREFIX_GAP : DesignMetrics.Role.ICON_GAP);
            leading.setBorder(new EmptyBorder(0, 0, 0, gap));
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int padding = ThemePainting.metric(context(), field.kind() == Nodes.FieldKind.TEXTAREA ? DesignMetrics.Role.TEXTAREA_PADDING : DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            surface.setBorder(new EmptyBorder(ring * 2, padding, ring * 2, padding));
            if (browse != null) { browse.parentEnabled = parentEnabled; browse.update(browseNode(field)); browse.component.setVisible(!field.trailingAction().isBlank()); }
            ((BorderLayout)line.getLayout()).setHgap(ThemePainting.metric(context(), DesignMetrics.Role.BUTTON_GAP));
            ((BorderLayout)container.getLayout()).setVgap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            container.revalidate(); container.repaint();
        }
    }
    static final class NumericFilter extends DocumentFilter {
        @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException {
            replace(bypass, offset, 0, text, attributes);
        }
        @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
            if (text == null || text.chars().allMatch(c -> c >= '0' && c <= '9')) super.replace(bypass, offset, length, text, attributes);
        }
    }
    static final class FieldSurface extends JPanel {
        final KitContext context; final JTextComponent input; final boolean multiline;
        Typography.Role role = Typography.Role.BODY; String hint = ""; boolean error;
        ThemePainting.Focus focus;
        FieldSurface(KitContext context, JTextComponent input, boolean multiline) {
            super(new BorderLayout()); this.context = context; this.input = input; this.multiline = multiline; setOpaque(false);
        }
        @Override public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(size.height, ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, role)));
        }
        @Override public void paint(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); super.paint(g); }
            finally { g.dispose(); }
        }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SURFACE_INPUT),
                    ThemePainting.color(context, error ? SemanticColor.BORDER_ERROR : focus.visible() ? SemanticColor.BORDER_FOCUS : SemanticColor.BORDER_INPUT),
                    focus.visible(), error);
            } finally { g.dispose(); }
        }
        @Override protected void paintChildren(Graphics graphics) {
            super.paintChildren(graphics);
            if (input.getText().isEmpty() && !hint.isEmpty()) {
                var g = (Graphics2D)graphics.create();
                try {
                    Point origin = SwingUtilities.convertPoint(input, 0, 0, this);
                    g.clipRect(origin.x, origin.y, input.getWidth(), input.getHeight());
                    g.setFont(input.getFont()); g.setColor(ThemePainting.color(context, SemanticColor.TEXT_PLACEHOLDER));
                    ThemePainting.antialias(g);
                    int y = multiline ? origin.y + g.getFontMetrics().getAscent() : origin.y + (input.getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
                    g.drawString(hint, origin.x, y);
                } finally { g.dispose(); }
            }
        }
    }
}
