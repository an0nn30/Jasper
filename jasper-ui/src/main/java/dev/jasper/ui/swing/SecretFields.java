package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;

/** Live secrets stay in wipeable documents; reveal changes only echo characters, never text. */
final class SecretFields {
    private SecretFields() {}
    static final class SecretControl extends ControlRegistry.Control {
        final SecretBuffer buffer = new SecretBuffer();
        final JPasswordField input = new JPasswordField();
        final JLabel label = Labels.literal(), error = Labels.literal(), lock = Labels.literal();
        final Buttons.KitButton reveal;
        final TextFields.FieldSurface surface;
        final char masked;
        boolean revealed;
        SecretControl(KitView view, Nodes.Secret node) {
            super(view, node, new JPanel(new BorderLayout()));
            own(buffer); input.setDocument(buffer); masked = input.getEchoChar();
            input.setOpaque(false); input.setBorder(new EmptyBorder(0, 0, 0, 0)); KitOwnership.mark(input, node.id() + ".input");
            input.putClientProperty("JPasswordField.cutCopyAllowed", false);
            surface = new TextFields.FieldSurface(context(), input, false);
            surface.focus = new ThemePainting.Focus(input); own(surface.focus);
            surface.add(input, BorderLayout.CENTER); surface.add(lock, BorderLayout.WEST);
            reveal = new Buttons.KitButton(context()); reveal.kind = Nodes.ButtonKind.GHOST;
            KitOwnership.mark(reveal, node.id() + ".reveal"); reveal.setOpaque(false); reveal.setContentAreaFilled(false);
            reveal.setBorderPainted(false); reveal.setFocusPainted(false); reveal.setRolloverEnabled(true);
            reveal.focus = new ThemePainting.Focus(reveal); own(reveal.focus);
            ActionListener action = event -> { if (interactive()) view.revealSecrets(this, !revealed); };
            reveal.addActionListener(action); own(() -> reveal.removeActionListener(action)); surface.add(reveal, BorderLayout.EAST);
            var container = (JPanel)component; container.setOpaque(false);
            container.add(label, BorderLayout.NORTH); container.add(surface, BorderLayout.CENTER); container.add(error, BorderLayout.SOUTH);
            label.setLabelFor(input);
            DocumentListener listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { changed(); }
                @Override public void removeUpdate(DocumentEvent event) { changed(); }
                @Override public void changedUpdate(DocumentEvent event) { changed(); }
                private void changed() { if (!buffer.clearing()) emit(new UiEvent.SecretChanged(SecretControl.this.node.id())); }
            };
            buffer.addDocumentListener(listener); own(() -> buffer.removeDocumentListener(listener)); refresh();
        }
        void reveal(boolean value) { revealed = value; input.setEchoChar(value ? (char)0 : masked); refreshReveal(); }
        private void refreshReveal() {
            reveal.setToolTipText(revealed ? "Hide password" : "Show password");
            reveal.getAccessibleContext().setAccessibleName(reveal.getToolTipText());
            reveal.getAccessibleContext().setAccessibleDescription(revealed ? "Password visible" : "Password hidden");
            reveal.setSelected(revealed); reveal.putClientProperty("Jasper.button.icon", "reveal");
            reveal.setIcon(context().icons().icon("reveal", context().theme().semantic(), SemanticColor.TEXT_SECONDARY,
                com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CONTROL))));
            reveal.setDisabledIcon(reveal.getIcon()); reveal.repaint();
        }
        @Override void update(Nodes.Node next) {
            if (!((Nodes.Secret)node).revealGroup().equals(((Nodes.Secret)next).revealGroup())) reveal(false);
            super.update(next);
        }
        boolean embedded;
        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(!value); }
        @Override void hero(boolean value) { surface.hero=value; }
        @Override JComponent focusTarget() { return input; }
        @Override void refresh() {
            var secret = (Nodes.Secret)node; state(); boolean enabled = component.isEnabled();
            input.setEnabled(enabled); input.setEditable(enabled); surface.setEnabled(enabled); reveal.setEnabled(enabled);
            input.setFont(ThemePainting.font(context(), Typography.Role.BODY)); input.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            input.setDisabledTextColor(input.getForeground()); input.setCaretColor(input.getForeground());
            input.setSelectionColor(ThemePainting.color(context(), SemanticColor.SELECTION_BG)); input.setSelectedTextColor(input.getForeground());
            input.getAccessibleContext().setAccessibleName(secret.label());
            input.getAccessibleContext().setAccessibleDescription(secret.state().error().isEmpty() ? secret.hint() : secret.state().error());
            label.setText(secret.label()); label.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY)); label.setEnabled(enabled);
            label.setVisible(!embedded);
            error.setText(secret.state().error()); error.setVisible(!secret.state().error().isEmpty()); error.setEnabled(enabled);
            error.setFont(ThemePainting.font(context(), Typography.Role.CAPTION)); error.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ERROR));
            surface.hint = secret.hint(); surface.error = !secret.state().error().isEmpty();
            lock.setIcon(context().icons().icon("lock", context().theme().semantic(), SemanticColor.TEXT_PLACEHOLDER,
                com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CONTROL))));
            lock.setBorder(new EmptyBorder(0, 0, 0, ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP)));
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int padding = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            surface.setBorder(new EmptyBorder(ring, padding, ring, ring)); reveal.setBorder(new EmptyBorder(ring, ring, ring, ring));
            reveal.setFont(ThemePainting.font(context(), Typography.Role.LABEL)); refreshReveal();
            ((BorderLayout)component.getLayout()).setVgap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP)); component.revalidate(); component.repaint();
        }
    }
}
