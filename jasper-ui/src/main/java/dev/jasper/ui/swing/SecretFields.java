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

/**
 * Live secrets stay in wipeable documents; reveal changes only echo characters, never text. A MULTILINE secret edits
 * the same wipeable buffer in a wrapping text area shown in clear (a secure note), with no reveal or copy control.
 */
final class SecretFields {
    private SecretFields() {}

    /** A MULTILINE secret shows this many wrapped lines before it scrolls, as the former secure-note editor did. */
    static final int MULTILINE_ROWS = 8;

    /** The preferred width of a MULTILINE secret, in columns, as the former secure-note editor's. */
    static final int MULTILINE_COLUMNS = 32;

    static final class SecretControl extends ControlRegistry.Control {
        final SecretBuffer buffer = new SecretBuffer();
        final JPasswordField input = new JPasswordField();
        /** The wrapping editor of a MULTILINE secret over {@link #buffer}; null for the other kinds. */
        final JTextArea area;
        final JScrollPane areaScroll;
        final boolean multiline;
        final JLabel label = Labels.literal(), error = Labels.literal(), lock = Labels.literal();
        final Buttons.KitButton reveal, copy;
        final boolean detail;
        final JComponent detailDisplay;
        char[] detailValue = new char[0];
        final TextFields.FieldSurface surface;
        final char masked;
        boolean revealed, focusWithin;
        int detailValueTop = -1;
        boolean detailActionsBelow;
        int detailActionWidth() { return reveal.getPreferredSize().width + copy.getPreferredSize().width; }
        int detailActionHeight() { return Math.max(reveal.getPreferredSize().height, copy.getPreferredSize().height); }
        int detailTextHeight() { return component.getFontMetrics(ThemePainting.font(context(), Typography.Role.BODY)).getHeight(); }

        SecretControl(KitView view, Nodes.Secret node) {
            super(view, node, new JPanel(new BorderLayout()));
            detail = node.kind() == Nodes.SecretKind.DETAIL;
            multiline = node.kind() == Nodes.SecretKind.MULTILINE;
            own(() -> java.util.Arrays.fill(detailValue, (char) 0));
            own(buffer);
            masked = input.getEchoChar();
            input.setOpaque(false);
            input.setBorder(new EmptyBorder(0, 0, 0, 0));
            input.putClientProperty("JPasswordField.cutCopyAllowed", false);
            if (multiline) {
                // The password field never holds this buffer: one document belongs to one editor.
                area = new JTextArea(buffer, null, MULTILINE_ROWS, MULTILINE_COLUMNS);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setOpaque(false);
                area.setBorder(new EmptyBorder(0, 0, 0, 0));
                KitOwnership.mark(area, node.id() + ".input");
                areaScroll = new JScrollPane(area);
                areaScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
                areaScroll.setOpaque(false);
                areaScroll.getViewport().setOpaque(false);
                surface = new TextFields.FieldSurface(context(), area, true);
                surface.focus = new ThemePainting.Focus(area, surface, view);
                own(surface.focus);
                surface.add(areaScroll, BorderLayout.CENTER);
            } else {
                area = null;
                areaScroll = null;
                input.setDocument(buffer);
                KitOwnership.mark(input, node.id() + ".input");
                surface = new TextFields.FieldSurface(context(), input, false);
                surface.focus = new ThemePainting.Focus(input, surface, view);
                own(surface.focus);
                surface.add(input, BorderLayout.CENTER);
                surface.add(lock, BorderLayout.WEST);
            }
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
            if (node.kind() == Nodes.SecretKind.DISCREET_INPUT) {
                var slot=new JPanel(new BorderLayout()) {
                    @Override public Dimension getPreferredSize() { return reveal.getPreferredSize(); }
                };
                slot.setOpaque(false);slot.add(reveal);surface.add(slot,BorderLayout.EAST);
            } else if (!multiline && node.kind() != Nodes.SecretKind.CONFIRMATION) surface.add(reveal, BorderLayout.EAST);
            var focusListener=new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { focusWithin=true; revealVisibility(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    focusWithin=e.getOppositeComponent()!=null && SwingUtilities.isDescendingFrom(e.getOppositeComponent(),component);
                    revealVisibility();
                }
            };
            input.addFocusListener(focusListener);reveal.addFocusListener(focusListener);
            own(()->{input.removeFocusListener(focusListener);reveal.removeFocusListener(focusListener);});
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
                var row = new JPanel(new BorderLayout()) {
                    @Override public void doLayout() {
                        super.doLayout();
                        if (detailValueTop >= 0) {
                            var actions = getComponent(1);
                            int height = actions.getPreferredSize().height;
                            int actionY=detailActionsBelow ? detailValueTop+detailTextHeight()+ThemePainting.metric(context(),DesignMetrics.Role.DETAIL_VALUE_GAP) : (getHeight()-height)/2;
                            actions.setBounds(detailActionsBelow ? 0 : getWidth()-actions.getPreferredSize().width, actionY, actions.getPreferredSize().width, height);
                            detailDisplay.setBounds(0, detailValueTop, detailActionsBelow ? getWidth() : Math.max(0, actions.getX()), detailTextHeight());
                        }
                    }
                };
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
            label.setLabelFor(detail ? detailDisplay : multiline ? area : input);
            bindSubmit();
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
                            revealVisibility();
                            if (!buffer.clearing())
                                emit(new UiEvent.SecretChanged(SecretControl.this.node.id()));
                        }
                    };
            buffer.addDocumentListener(listener);
            own(() -> buffer.removeDocumentListener(listener));
            refresh();
        }

        /**
         * Enter in an input secret with a submit action runs it and keeps the key (the containing form's primary is not
         * pressed); without one the binding is disabled and Enter passes on to the form or dialog.
         */
        private void bindSubmit() {
            KeyStroke key = KeyStroke.getKeyStroke("ENTER");
            String name = "secret.submit";
            input.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
            input.getActionMap()
                    .put(
                            name,
                            new AbstractAction() {
                                @Override
                                public boolean isEnabled() {
                                    var secret = (Nodes.Secret) node;
                                    return !closed
                                            && (secret.kind() == Nodes.SecretKind.INPUT || secret.kind() == Nodes.SecretKind.DISCREET_INPUT || secret.kind() == Nodes.SecretKind.CONFIRMATION)
                                            && !secret.submitAction().isBlank();
                                }

                                @Override
                                public void actionPerformed(java.awt.event.ActionEvent event) {
                                    if (isEnabled() && interactive())
                                        emit(new UiEvent.Action(((Nodes.Secret) node).submitAction()));
                                }
                            });
            own(
                    () -> {
                        input.getInputMap(JComponent.WHEN_FOCUSED).remove(key);
                        input.getActionMap().remove(name);
                    });
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

        private void revealVisibility() {
            var kind=((Nodes.Secret)node).kind();
            reveal.setVisible(!multiline && kind!=Nodes.SecretKind.CONFIRMATION
                && (kind!=Nodes.SecretKind.DISCREET_INPUT || focusWithin || buffer.getLength()>0));
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

        private void refreshArea(Nodes.Secret secret, boolean enabled) {
            area.setEnabled(enabled);
            area.setEditable(enabled);
            area.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR));
            area.setFont(ThemePainting.font(context(), Typography.Role.BODY));
            area.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            area.setDisabledTextColor(area.getForeground());
            area.setCaretColor(area.getForeground());
            area.setSelectionColor(ThemePainting.color(context(), SemanticColor.SELECTION_BG));
            area.setSelectedTextColor(area.getForeground());
            area.getAccessibleContext().setAccessibleName(secret.label());
            area.getAccessibleContext()
                    .setAccessibleDescription(
                            secret.state().error().isEmpty() ? secret.hint() : secret.state().error());
            areaScroll.setEnabled(enabled);
            areaScroll.getVerticalScrollBar().setEnabled(enabled);
            areaScroll.getVerticalScrollBar()
                    .setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_INPUT));
            areaScroll.getVerticalScrollBar()
                    .setForeground(ThemePainting.color(context(), SemanticColor.BORDER_INPUT));
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
            return detail ? reveal : multiline ? area : input;
        }

        @Override
        void refresh() {
            var secret = (Nodes.Secret) node;
            state();
            boolean enabled = component.isEnabled();
            input.setEnabled(enabled);
            input.setEditable(enabled && !detail);
            if (multiline) refreshArea(secret, enabled);
            surface.setEnabled(enabled);
            reveal.setEnabled(enabled);
            revealVisibility();
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
            lock.setVisible(!embedded && !surface.hero && !multiline);
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
                    ThemePainting.metric(
                            context(),
                            multiline
                                    ? DesignMetrics.Role.TEXTAREA_PADDING
                                    : DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            if (multiline) surface.setBorder(new EmptyBorder(ring * 2, padding, ring * 2, padding));
            else surface.setBorder(new EmptyBorder(ring, padding, ring, ring));
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
