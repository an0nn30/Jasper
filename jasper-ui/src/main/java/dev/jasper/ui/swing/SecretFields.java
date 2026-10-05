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
        final Buttons.KitButton reveal, copy;
        final boolean detail;
        final JComponent detailDisplay;
        char[] detailValue = new char[0];
        final TextFields.FieldSurface surface;
        final char masked;
        boolean revealed;

        SecretControl(KitView view, Nodes.Secret node) {
            super(view, node, new JPanel(new BorderLayout()));
            detail = node.kind() == Nodes.SecretKind.DETAIL;
            own(() -> java.util.Arrays.fill(detailValue, (char) 0));
            own(buffer);
            input.setDocument(buffer);
            masked = input.getEchoChar();
            input.setOpaque(false);
            input.setBorder(new EmptyBorder(0, 0, 0, 0));
            KitOwnership.mark(input, node.id() + ".input");
            input.putClientProperty("JPasswordField.cutCopyAllowed", false);
            surface = new TextFields.FieldSurface(context(), input, false);
            surface.focus = new ThemePainting.Focus(input, surface, view);
            own(surface.focus);
            surface.add(input, BorderLayout.CENTER);
            surface.add(lock, BorderLayout.WEST);
            reveal = new Buttons.KitButton(context());
            reveal.kind = Nodes.ButtonKind.GHOST;
            KitOwnership.mark(reveal, node.id() + ".reveal");
            reveal.setOpaque(false);
            reveal.setContentAreaFilled(false);
            reveal.setBorderPainted(false);
            reveal.setFocusPainted(false);
            reveal.setRolloverEnabled(true);
            reveal.focus = new ThemePainting.Focus(reveal, reveal, view);
            own(reveal.focus);
            ActionListener action =
                    event -> {
                        if (interactive()) view.revealSecrets(this, !revealed);
                    };
            reveal.addActionListener(action);
            own(() -> reveal.removeActionListener(action));
            surface.add(reveal, BorderLayout.EAST);
            copy = new Buttons.KitButton(context());
            copy.kind = Nodes.ButtonKind.GHOST;
            KitOwnership.mark(copy, node.id() + ".copy");
            copy.setOpaque(false);
            copy.setContentAreaFilled(false);
            copy.setBorderPainted(false);
            copy.setFocusPainted(false);
            copy.focus = new ThemePainting.Focus(copy, copy, view);
            own(copy.focus);
            ActionListener copying =
                    event -> {
                        if (interactive())
                            emit(new UiEvent.Action(((Nodes.Secret) this.node).copyAction()));
                    };
            copy.addActionListener(copying);
            own(() -> copy.removeActionListener(copying));
            detailDisplay =
                    new JComponent() {
                        @Override
                        public javax.accessibility.AccessibleContext getAccessibleContext() {
                            if (accessibleContext == null)
                                accessibleContext =
                                        new AccessibleJComponent() {
                                            @Override
                                            public javax.accessibility.AccessibleRole
                                                    getAccessibleRole() {
                                                return javax.accessibility.AccessibleRole
                                                        .PASSWORD_TEXT;
                                            }
                                        };
                            return accessibleContext;
                        }

                        @Override
                        public Dimension getPreferredSize() {
                            return new Dimension(
                                    120,
                                    ThemePainting.height(
                                            context(),
                                            DesignMetrics.Role.FIELD_HEIGHT,
                                            Typography.Role.BODY));
                        }

                        @Override
                        protected void paintComponent(Graphics graphics) {
                            var g = (Graphics2D) graphics.create();
                            char[] chars = snapshot();
                            try {
                                if (!isEnabled())
                                    g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                                g.setFont(ThemePainting.font(context(), Typography.Role.BODY));
                                g.setColor(
                                        ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
                                if (!revealed) java.util.Arrays.fill(chars, (char) 0x2022);
                                var fm = g.getFontMetrics();
                                g.drawChars(
                                        chars,
                                        0,
                                        chars.length,
                                        0,
                                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                            } finally {
                                java.util.Arrays.fill(chars, (char) 0);
                                g.dispose();
                            }
                        }
                    };
            KitOwnership.mark(detailDisplay, node.id() + ".value");
            var container = (JPanel) component;
            container.setOpaque(false);
            container.add(label, BorderLayout.NORTH);
            container.add(error, BorderLayout.SOUTH);
            if (detail) {
                var row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                var actions = new JPanel(new GridLayout(1, 0));
                actions.setOpaque(false);
                actions.add(reveal);
                actions.add(copy);
                row.add(detailDisplay, BorderLayout.CENTER);
                row.add(actions, BorderLayout.EAST);
                container.add(row, BorderLayout.CENTER);
                surface.remove(input);
            } else container.add(surface, BorderLayout.CENTER);
            label.setLabelFor(detail ? detailDisplay : input);
            DocumentListener listener =
                    new DocumentListener() {
                        @Override
                        public void insertUpdate(DocumentEvent event) {
                            changed();
                        }

                        @Override
                        public void removeUpdate(DocumentEvent event) {
                            changed();
                        }

                        @Override
                        public void changedUpdate(DocumentEvent event) {
                            changed();
                        }

                        private void changed() {
                            if (!buffer.clearing())
                                emit(new UiEvent.SecretChanged(SecretControl.this.node.id()));
                        }
                    };
            buffer.addDocumentListener(listener);
            own(() -> buffer.removeDocumentListener(listener));
            refresh();
        }

        char[] snapshot() {
            return detail ? detailValue.clone() : buffer.snapshot();
        }

        void clear() {
            if (detail) {
                java.util.Arrays.fill(detailValue, (char) 0);
                detailValue = new char[0];
                reveal(false);
            } else buffer.clear();
        }

        void replace(char[] value) {
            if (detail) {
                char[] next = value.clone();
                clear();
                detailValue = next;
                detailDisplay.repaint();
            } else buffer.replace(value);
        }

        void reveal(boolean value) {
            revealed = value;
            input.setEchoChar(value ? (char) 0 : masked);
            refreshReveal();
            if (detailDisplay != null) {
                detailDisplay
                        .getAccessibleContext()
                        .setAccessibleDescription(
                                revealed ? "Stored secret visible" : "Stored secret hidden");
                detailDisplay.repaint();
            }
        }

        private void refreshReveal() {
            reveal.setToolTipText(revealed ? "Hide password" : "Show password");
            reveal.getAccessibleContext().setAccessibleName(reveal.getToolTipText());
            reveal.getAccessibleContext()
                    .setAccessibleDescription(revealed ? "Password visible" : "Password hidden");
            reveal.setSelected(revealed);
            reveal.putClientProperty("Jasper.button.icon", "reveal");
            reveal.setIcon(
                    context()
                            .icons()
                            .icon(
                                    "reveal",
                                    context().theme().semantic(),
                                    SemanticColor.TEXT_SECONDARY,
                                    com.formdev.flatlaf.util.UIScale.scale(
                                            context().icons().size(Nodes.IconSize.CONTROL))));
            reveal.setDisabledIcon(reveal.getIcon());
            reveal.repaint();
        }

        @Override
        void update(Nodes.Node next) {
            if (!((Nodes.Secret) node).revealGroup().equals(((Nodes.Secret) next).revealGroup()))
                reveal(false);
            super.update(next);
        }

        boolean embedded;

        @Override
        void embeddedLabel(boolean value) {
            embedded = value;
            label.setVisible(!value && !surface.hero);
            lock.setVisible(!value && !surface.hero);
        }

        @Override
        void hero(boolean value) {
            surface.hero = value;
            label.setVisible(!embedded && !value);
            lock.setVisible(!embedded && !value);
        }

        @Override
        JComponent focusTarget() {
            return detail ? reveal : input;
        }

        @Override
        void refresh() {
            var secret = (Nodes.Secret) node;
            state();
            boolean enabled = component.isEnabled();
            input.setEnabled(enabled);
            input.setEditable(enabled && !detail);
            surface.setEnabled(enabled);
            reveal.setEnabled(enabled);
            detailDisplay.setEnabled(enabled);
            detailDisplay.getAccessibleContext().setAccessibleName(secret.label());
            detailDisplay
                    .getAccessibleContext()
                    .setAccessibleDescription(
                            revealed ? "Stored secret visible" : "Stored secret hidden");
            copy.setEnabled(enabled);
            copy.setVisible(detail && !secret.copyAction().isBlank());
            copy.setToolTipText("Copy " + secret.label());
            copy.getAccessibleContext().setAccessibleName(copy.getToolTipText());
            copy.setIcon(
                    context()
                            .icons()
                            .icon(
                                    "copy",
                                    context().theme().semantic(),
                                    SemanticColor.TEXT_SECONDARY,
                                    com.formdev.flatlaf.util.UIScale.scale(
                                            context().icons().size(Nodes.IconSize.CONTROL))));
            copy.setDisabledIcon(copy.getIcon());
            input.setFont(ThemePainting.font(context(), Typography.Role.BODY));
            input.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            input.setDisabledTextColor(input.getForeground());
            input.setCaretColor(input.getForeground());
            input.setSelectionColor(ThemePainting.color(context(), SemanticColor.SELECTION_BG));
            input.setSelectedTextColor(input.getForeground());
            input.getAccessibleContext().setAccessibleName(secret.label());
            input.getAccessibleContext()
                    .setAccessibleDescription(
                            secret.state().error().isEmpty()
                                    ? secret.hint()
                                    : secret.state().error());
            label.setText(secret.label());
            label.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY));
            label.setEnabled(enabled);
            label.setVisible(!embedded && !surface.hero);
            error.setText(secret.state().error());
            error.setVisible(!secret.state().error().isEmpty());
            error.setEnabled(enabled);
            error.setFont(ThemePainting.font(context(), Typography.Role.CAPTION));
            error.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ERROR));
            surface.hint = secret.hint();
            surface.error = !secret.state().error().isEmpty();
            lock.setIcon(
                    context()
                            .icons()
                            .icon(
                                    "lock",
                                    context().theme().semantic(),
                                    SemanticColor.TEXT_PLACEHOLDER,
                                    com.formdev.flatlaf.util.UIScale.scale(
                                            context().icons().size(Nodes.IconSize.CONTROL))));
            lock.setBorder(
                    new EmptyBorder(
                            0, 0, 0, ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP)));
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int padding =
                    ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            surface.setBorder(new EmptyBorder(ring, padding, ring, ring));
            reveal.setBorder(new EmptyBorder(ring, ring, ring, ring));
            copy.setBorder(new EmptyBorder(ring, ring, ring, ring));
            reveal.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            refreshReveal();
            ((BorderLayout) component.getLayout())
                    .setVgap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            component.revalidate();
            component.repaint();
        }
    }
}
