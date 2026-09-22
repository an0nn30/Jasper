package dev.jasper.app.pluginmanager;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** A question with one consequence and two answers, for a removal. Plugin-supplied text is never markup. */
final class ConfirmView extends JPanel {
    final JButton ok;
    final JButton cancel = new JButton("Cancel");
    private final JTextArea body = new JTextArea();

    ConfirmView(String text, String okLabel, Runnable onOk, Runnable onCancel) {
        super(new BorderLayout(0, 12));
        ok = new JButton(okLabel);
        body.putClientProperty("html.disable", Boolean.TRUE);
        body.setText(text);
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true); body.setOpaque(false);
        body.setFont(UIManager.getFont("Label.font"));
        body.setColumns(44);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        add(body, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.add(cancel); buttons.add(ok);
        add(buttons, BorderLayout.SOUTH);
        ok.addActionListener(event -> onOk.run());
        cancel.addActionListener(event -> onCancel.run());
    }

    String text() { return body.getText(); }
}
