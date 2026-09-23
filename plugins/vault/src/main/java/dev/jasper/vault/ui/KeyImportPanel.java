package dev.jasper.vault.ui;

import dev.jasper.vault.service.KeyImportPrompt;
import dev.jasper.sdk.Subscription;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.*;

/** Passive import review and passphrase controls; metadata is never interpreted as HTML. */
public final class KeyImportPanel extends JPanel implements AutoCloseable {
    private final SecretDocument secret = new SecretDocument();
    private final JPasswordField password = new JPasswordField(24);
    private final JTextArea status = new JTextArea(3, 42), keys = new JTextArea(7, 42);
    private final JPanel phrase = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JButton submit = new JButton("Unlock key"), commit = new JButton("Import and use"), cancel = new JButton("Cancel");
    private final Subscription subscription;
    public KeyImportPanel(KeyImportPrompt prompt) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setOpaque(false);
        keys.setEditable(false); keys.setLineWrap(true); keys.setWrapStyleWord(true);
        password.setDocument(secret); password.getAccessibleContext().setAccessibleName("Key passphrase");
        phrase.add(new JLabel("Key passphrase")); phrase.add(password); phrase.add(submit);
        var body = new JPanel(new BorderLayout(0, 10)); body.add(new JScrollPane(keys)); body.add(phrase, BorderLayout.SOUTH);
        add(status, BorderLayout.NORTH); add(body);
        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.add(cancel); actions.add(commit); add(actions, BorderLayout.SOUTH);
        Runnable unlock = () -> { char[] value = secret.snapshot(); secret.clear(); prompt.submitPassphrase(value); };
        submit.addActionListener(e -> unlock.run()); password.addActionListener(e -> unlock.run());
        commit.addActionListener(e -> prompt.commit()); cancel.addActionListener(e -> prompt.cancel());
        Runnable refresh = () -> {
            status.setText(prompt.status());
            keys.setText(String.join("\n", prompt.rows().stream().map(row -> row.name() + "  " + row.fingerprint() + (row.reused() ? " (reuse)" : " (new)")).toList()));
            boolean waiting = prompt.phase() == KeyImportPrompt.Phase.PASSPHRASE;
            phrase.setVisible(waiting); if (!waiting) secret.clear();
            commit.setEnabled(prompt.phase() == KeyImportPrompt.Phase.REVIEW);
            cancel.setEnabled(prompt.phase() != KeyImportPrompt.Phase.COMMITTING);
            revalidate(); repaint(); if (waiting) password.requestFocusInWindow();
        };
        subscription = prompt.onChanged(refresh); refresh.run();
    }
    @Override public void close() { subscription.close(); secret.clear(); }
}
