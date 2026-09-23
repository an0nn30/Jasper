package dev.jasper.remote.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.*;

/** Edits the display name used for hosts with no explicit group. */
public final class GroupNamePanel extends JPanel {
    final JTextField name = new JTextField();
    final JButton save = new JButton("Rename");
    final JLabel error = new JLabel(" ");
    public GroupNamePanel(String current, Consumer<String> rename, Runnable cancel) {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20)); setPreferredSize(new Dimension(360, 150));
        var form = new JPanel(new BorderLayout(0, 6)); form.add(new JLabel("Default group name"), BorderLayout.NORTH);
        name.setText(current); form.add(name, BorderLayout.CENTER); form.add(error, BorderLayout.SOUTH); add(form, BorderLayout.CENTER);
        var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        var close = new JButton("Cancel"); buttons.add(close); buttons.add(save); add(buttons, BorderLayout.SOUTH);
        save.addActionListener(event -> rename.accept(name.getText())); name.addActionListener(event -> save.doClick());
        close.addActionListener(event -> cancel.run()); error.putClientProperty("html.disable", true);
    }
    public void showError(String message) { error.setText(message); }
}
