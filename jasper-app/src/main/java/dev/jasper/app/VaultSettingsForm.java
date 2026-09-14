package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.time.Instant;
import javax.swing.*;

/** Vault settings live inside the encrypted vault; this form edits them and can forget device access. */
final class VaultSettingsForm extends JPanel {
    final JSpinner auto, days;
    final JButton forget = new JButton("Forget this device"), save = new JButton("Save"), cancel = new JButton("Cancel");
    final JLabel message = new JLabel(" ");

    VaultSettingsForm(VaultSnapshot snapshot) {
        super(new BorderLayout(0, 16));
        VaultUi.pad(this, 20);
        auto = new JSpinner(new SpinnerNumberModel(snapshot.settings().autoLockMinutes(), 0, 1440, 1));
        days = new JSpinner(new SpinnerNumberModel(snapshot.settings().rememberDays(), 1, 365, 1));
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, "Auto-lock minutes (0 disables)", auto);
        VaultUi.field(fields, 1, "Remember days", days);
        VaultUi.field(fields, 2, "Device access", new JLabel(VaultUi.rememberedText(snapshot.rememberedUntil(), Instant.now())));
        VaultUi.field(fields, 3, "", forget);
        VaultUi.field(fields, 4, "", new JLabel("Duration changes do not renew current device access."));
        VaultUi.field(fields, 5, "", message);
        add(fields);
        add(VaultUi.row(cancel, save), BorderLayout.SOUTH);
        setPreferredSize(new Dimension(510, 280));
    }

    VaultSettings settings() { return new VaultSettings((Integer) auto.getValue(), (Integer) days.getValue()); }
}
