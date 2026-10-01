package dev.jasper.fileserver.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A question with two answers, shown in a host dialog. */
public final class ConfirmPanel extends JPanel {
    final JLabel message;
    final JButton confirm, cancel;

    public ConfirmPanel(String text, String confirmLabel, String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        message = new JLabel();
        message.putClientProperty("html.disable", Boolean.TRUE);
        message.setText(text);
        add(message, BorderLayout.CENTER);
        confirm = new JButton(confirmLabel);
        cancel = new JButton(cancelLabel);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(confirm);
        add(buttons, BorderLayout.SOUTH);
        confirm.addActionListener(event -> onConfirm.run());
        cancel.addActionListener(event -> onCancel.run());
    }
}
