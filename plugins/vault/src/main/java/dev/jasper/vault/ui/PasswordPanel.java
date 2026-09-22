package dev.jasper.vault.ui;

import dev.jasper.vault.service.UnlockPrompt;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** The master-password form: unlock (one field) or create (two fields and the bind checkbox). */
public final class PasswordPanel extends JPanel {
    static final int MIN_LENGTH = 8;
    final JPasswordField password = new JPasswordField(24);
    final JPasswordField confirm = new JPasswordField(24);
    final JCheckBox bind = new JCheckBox("Bind to this device (recommended)");
    final JLabel message = new JLabel(" ");
    final JButton primary;
    final JButton cancel = new JButton("Cancel");

    private PasswordPanel(String primaryTitle, boolean creating, boolean bindDefault, String intro) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        password.setDocument(new SecretDocument());
        confirm.setDocument(new SecretDocument());
        primary = new JButton(primaryTitle);
        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(2, 2, 2, 2); at.anchor = GridBagConstraints.WEST; at.gridy = 0; at.gridwidth = 2;
        form.add(new JLabel(intro), at);
        at.gridwidth = 1; at.gridy++;
        form.add(new JLabel("Master password:"), at); at.gridx = 1; form.add(password, at);
        if (creating) {
            at.gridx = 0; at.gridy++;
            form.add(new JLabel("Confirm:"), at); at.gridx = 1; form.add(confirm, at);
            at.gridx = 0; at.gridy++; at.gridwidth = 2;
            bind.setSelected(bindDefault);
            form.add(bind, at);
        }
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        message.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(primary);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);
        password.addActionListener(event -> primary.doClick());
        confirm.addActionListener(event -> primary.doClick());
    }

    /** The unlock form bound to {@code prompt}: Enter submits, errors show, the prompt's dismissal is the dialog's. */
    public static PasswordPanel unlock(UnlockPrompt prompt) {
        var panel = new PasswordPanel("Unlock", false, false, "Enter the master password to unlock the vault.");
        prompt.onError(text -> { panel.message.setText(text); panel.primary.setEnabled(true); panel.password.selectAll(); panel.password.requestFocusInWindow(); });
        panel.primary.addActionListener(event -> {
            char[] typed = panel.password.getPassword();
            if (typed.length == 0) { Arrays.fill(typed, (char) 0); return; }
            panel.primary.setEnabled(false);
            panel.message.setText("Unlocking...");
            panel.password.setText("");
            prompt.submit(typed);
        });
        panel.cancel.addActionListener(event -> prompt.cancel());
        return panel;
    }

    /** The create form: two matching passwords of at least {@value #MIN_LENGTH} characters and the bind choice. */
    public static PasswordPanel create(boolean bindDefault, BiConsumer<char[], Boolean> onCreate, Runnable onCancel) {
        var panel = new PasswordPanel("Create Vault", true, bindDefault, "Choose a master password. It cannot be recovered if forgotten.");
        panel.primary.addActionListener(event -> {
            char[] typed = panel.password.getPassword(), again = panel.confirm.getPassword();
            try {
                if (typed.length < MIN_LENGTH) { panel.message.setText("Use at least " + MIN_LENGTH + " characters"); return; }
                if (!Arrays.equals(typed, again)) { panel.message.setText("The passwords do not match"); return; }
                panel.password.setText(""); panel.confirm.setText("");
                onCreate.accept(typed.clone(), panel.bind.isSelected());
            } finally { Arrays.fill(typed, (char) 0); Arrays.fill(again, (char) 0); }
        });
        panel.cancel.addActionListener(event -> onCancel.run());
        return panel;
    }
    /** Erases editable copies when the containing surface closes or locks. */
    public void clear() { password.setText(""); confirm.setText(""); }

    /** Prevents duplicate submits while a background create is running. */
    public void setBusy(boolean busy) { primary.setEnabled(!busy); password.setEnabled(!busy); confirm.setEnabled(!busy); bind.setEnabled(!busy); }
}
