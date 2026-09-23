package dev.jasper.remote.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** "Delete prod?" with one verb button and Cancel. */
public final class ConfirmPanel extends JPanel {
    public final JButton confirm, cancel = new JButton("Cancel");

    public ConfirmPanel(String text, String verb, Runnable onConfirm, Runnable onCancel) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        confirm = new JButton(verb);
        add(new JLabel(text), BorderLayout.CENTER);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(confirm);
        add(buttons, BorderLayout.SOUTH);
        confirm.addActionListener(event -> onConfirm.run());
        cancel.addActionListener(event -> onCancel.run());
    }
}
