package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import javax.swing.*;

/** Key details editor: only the name is editable; private material is never shown here. */
final class VaultKeyForm extends JPanel {
    final JTextField name = new JTextField(20);
    final JLabel algorithm = new JLabel(" "), fingerprint = new JLabel(" "), reuse = new JLabel(" ");
    final JTextArea publicKey = new JTextArea(3, 40);
    final JButton copy = new JButton(VaultIcons.icon("copy"));
    Runnable changed = () -> {};
    private boolean loading, dirty;

    VaultKeyForm() {
        super(new BorderLayout(0, 6));
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, "Name", name);
        VaultUi.field(fields, 1, "Algorithm", algorithm);
        VaultUi.field(fields, 2, "Fingerprint", fingerprint);
        publicKey.setEditable(false); publicKey.setLineWrap(true); publicKey.setWrapStyleWord(true);
        JPanel pub = new JPanel(new BorderLayout(4, 0));
        JScrollPane scroll = new JScrollPane(publicKey); scroll.setPreferredSize(new Dimension(0, 64));
        pub.add(scroll); pub.add(VaultUi.row(copy), BorderLayout.EAST);
        VaultUi.field(fields, 3, "Public key", pub);
        VaultUi.field(fields, 4, "", reuse);
        add(fields, BorderLayout.NORTH);
        copy.setToolTipText("Copy public key"); copy.getAccessibleContext().setAccessibleName("Copy public key");
        publicKey.getAccessibleContext().setAccessibleName("Public key");
        VaultUi.changes(name, () -> { if (!loading) { dirty = true; changed.run(); } });
        setMinimumSize(new Dimension(0, 0));
    }

    void load(VaultSnapshot.KeyInfo key) {
        loading = true;
        name.setText(key == null ? "" : key.name());
        algorithm.setText(key == null ? " " : key.algorithm());
        fingerprint.setText(key == null ? " " : key.fingerprint());
        publicKey.setText(key == null ? "" : key.publicKey()); publicKey.setCaretPosition(0);
        reuse.setText(key == null ? " " : "Used by " + key.loginUses() + " login" + (key.loginUses() == 1 ? "" : "s"));
        dirty = false; loading = false;
    }

    boolean dirty() { return dirty; }
    String draftName() { return name.getText().strip(); }
    void clear() { load(null); }
}
