package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.time.Instant;
import javax.swing.*;

/** Create or unlock the vault. Shown inline in a locked manager and as a dialog from the padlock/menu. */
final class VaultUnlockForm extends JPanel {
    final JPasswordField password = new JPasswordField(24), confirmation = new JPasswordField(24);
    final JCheckBox remember = new JCheckBox("Remember on this device");
    final JLabel message = new JLabel(" "), expiry = new JLabel(" ");
    final JButton primary = new JButton(), cancel = new JButton("Cancel");
    final boolean create;
    private final JPanel fields = VaultUi.form();
    private final JLabel passwordLabel;
    private final boolean rememberingAvailable;

    VaultUnlockForm(boolean create, boolean rememberingAvailable) {
        super(new BorderLayout(0, 12));
        this.create = create; this.rememberingAvailable = rememberingAvailable;
        VaultUi.pad(this, 24);
        JLabel title = new JLabel(create ? "Create vault" : "Vault locked");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);
        VaultUi.field(fields, 0, "Master password", password);
        passwordLabel = (JLabel) fields.getComponent(0);
        VaultUi.field(fields, 1, "Confirm password", confirmation);
        confirmation.setVisible(create); fields.getComponent(2).setVisible(create);
        VaultUi.field(fields, 2, "", remember);
        VaultUi.field(fields, 3, "", expiry);
        VaultUi.field(fields, 4, "", message);
        add(fields);
        primary.setText(create ? "Create vault" : "Unlock");
        add(VaultUi.row(primary, cancel), BorderLayout.SOUTH);
        remember.setEnabled(rememberingAvailable);
        password.getAccessibleContext().setAccessibleName("Master password");
        setPreferredSize(new Dimension(480, 290));
    }

    void state(VaultSnapshot snapshot) {
        expiry.setText(VaultUi.rememberedText(snapshot.rememberedUntil(), Instant.now()));
        remember.setText("Remember on this device for " + snapshot.settings().rememberDays() + " days");
        boolean usable = rememberingAvailable && (snapshot.deviceWarning() == null || snapshot.deviceWarning().isBlank());
        remember.setEnabled(usable);
        if (!usable) { remember.setSelected(false); message.setText("OS credential store unavailable. Use the master password."); }
    }

    void passwordVisible(boolean visible) {
        passwordLabel.setVisible(visible); password.setVisible(visible); remember.setVisible(visible);
        revalidate(); repaint();
    }

    void showPassword(String reason) { passwordVisible(true); message.setText(reason); password.requestFocusInWindow(); }

    void clear() { password.setText(""); confirmation.setText(""); remember.setSelected(false); }
}
