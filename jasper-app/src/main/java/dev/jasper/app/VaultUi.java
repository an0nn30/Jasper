package dev.jasper.app;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Small Swing layout helpers shared by the vault forms; plain logical pixels, no scaling library. */
final class VaultUi {
    private VaultUi() {}

    static Action action(String text, String icon, Runnable run) {
        return new AbstractAction(text, icon == null ? null : VaultIcons.icon(icon)) {
            @Override public void actionPerformed(ActionEvent e) { if (isEnabled()) run.run(); }
        };
    }

    static JPanel row(Component... children) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (Component child : children) panel.add(child);
        return panel;
    }

    static JPanel form() { return new JPanel(new GridBagLayout()); }

    static void field(JPanel panel, int row, String text, JComponent input) {
        JLabel label = new JLabel(text); label.setLabelFor(input);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(5, 0, 5, 14);
        panel.add(label, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(5, 0, 5, 0);
        panel.add(input, c);
        if (!text.isBlank()) input.getAccessibleContext().setAccessibleName(text);
    }

    static void pad(JComponent component, int pixels) {
        component.setBorder(BorderFactory.createEmptyBorder(pixels, pixels, pixels, pixels));
    }

    static void changes(JTextComponent input, Runnable changed) {
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed.run(); }
            @Override public void removeUpdate(DocumentEvent e) { changed.run(); }
            @Override public void changedUpdate(DocumentEvent e) { changed.run(); }
        });
    }

    static void enabled(Component component, boolean value) {
        component.setEnabled(value);
        if (component instanceof Container container)
            for (Component child : container.getComponents()) enabled(child, value);
    }

    static String rememberedText(Instant until, Instant now) {
        if (until == null) return "No remembered device access";
        String formatted = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").withZone(ZoneId.systemDefault()).format(until);
        return (until.isAfter(now) ? "Device access until " : "Device access expired ") + formatted;
    }
}
