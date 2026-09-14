package dev.jasper.app;

import java.awt.*;
import java.nio.file.Path;
import javax.swing.*;

/** Import an SSH private key: choose, inspect (fingerprint preview), then import an encrypted copy. */
final class VaultImportForm extends JPanel {
    final JTextField name = new JTextField(22);
    final JPasswordField passphrase = new JPasswordField(22);
    final JLabel file = new JLabel("No file selected"), fingerprint = new JLabel(" ");
    final JLabel message = new JLabel("Select a private key to inspect it.");
    final JButton browse = new JButton("Choose file…"), inspect = new JButton("Inspect key");
    final JButton save = new JButton("Import key"), cancel = new JButton("Cancel");
    Path source;

    VaultImportForm() {
        super(new BorderLayout(0, 12));
        VaultUi.pad(this, 20);
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, "Key name", name);
        VaultUi.field(fields, 1, "Private key", VaultUi.row(file, browse));
        VaultUi.field(fields, 2, "Passphrase (if encrypted)", passphrase);
        VaultUi.field(fields, 3, "Fingerprint", fingerprint);
        VaultUi.field(fields, 4, "", message);
        add(fields);
        add(VaultUi.row(inspect, cancel, save), BorderLayout.SOUTH);
        save.setEnabled(false);
        setPreferredSize(new Dimension(600, 300));
    }

    void clear() { passphrase.setText(""); source = null; }
}
