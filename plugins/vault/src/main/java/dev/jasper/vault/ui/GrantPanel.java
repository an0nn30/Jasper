package dev.jasper.vault.ui;

import dev.jasper.vault.service.GrantPrompt;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** "Allow SSH to use deploy@prod?" with Allow once, Always allow for SSH, Deny. */
public final class GrantPanel extends JPanel {
    final JButton once = new JButton("Allow once");
    final JButton always;
    final JButton deny = new JButton("Deny");

    public GrantPanel(GrantPrompt prompt) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        always = new JButton("Always allow for " + prompt.consumerName());
        String what = prompt.descriptor().subtitle().isBlank() ? prompt.descriptor().name() : prompt.descriptor().subtitle() + "@" + prompt.descriptor().name();
        add(new JLabel("<html><b>" + escape(prompt.consumerName()) + "</b> wants to use <b>" + escape(what) + "</b> from the vault.</html>"), BorderLayout.CENTER);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(deny); buttons.add(Box.createHorizontalGlue()); buttons.add(always); buttons.add(Box.createHorizontalStrut(8)); buttons.add(once);
        add(buttons, BorderLayout.SOUTH);
        once.addActionListener(event -> prompt.answer(GrantPrompt.Decision.ALLOW_ONCE));
        always.addActionListener(event -> prompt.answer(GrantPrompt.Decision.ALWAYS));
        deny.addActionListener(event -> prompt.answer(GrantPrompt.Decision.DENY));
    }

    static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
