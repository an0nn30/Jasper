package dev.jasper.remote.ui;

import dev.jasper.remote.client.HostKeyVerifier;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The unknown-host-key question: Cancel, Connect once, Trust and connect. */
public final class HostKeyPanel extends JPanel {
    final JLabel text;
    final JButton cancel = new JButton("Cancel"), once = new JButton("Connect once"), trust = new JButton("Trust and connect");

    public HostKeyPanel(HostKeyVerifier.Question question, Consumer<HostKeyVerifier.Decision> decide) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        text = new JLabel("<html>The authenticity of <b>" + escape(question.host()) + "</b> port " + question.port() + " can't be established.<br>"
            + escape(question.keyType()) + " key fingerprint: <code>" + escape(question.fingerprint()) + "</code><br>Compare it with the server's before trusting.</html>");
        add(text, BorderLayout.CENTER);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(cancel); buttons.add(Box.createHorizontalGlue()); buttons.add(once); buttons.add(Box.createHorizontalStrut(8)); buttons.add(trust);
        add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(event -> decide.accept(HostKeyVerifier.Decision.CANCEL));
        once.addActionListener(event -> decide.accept(HostKeyVerifier.Decision.ONCE));
        trust.addActionListener(event -> decide.accept(HostKeyVerifier.Decision.TRUST));
    }

    static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
