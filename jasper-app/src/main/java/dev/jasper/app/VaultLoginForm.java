package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.*;

/** Login draft editor. The committed password is never loaded; only typed text becomes a draft value. */
final class VaultLoginForm extends JPanel {
    record KeyChoice(UUID id, String label) { @Override public String toString() { return label; } }
    record Draft(String name, String username, UUID keyId, char[] password) implements AutoCloseable {
        @Override public void close() { if (password != null) Arrays.fill(password, (char) 0); }
        @Override public String toString() { return "LoginDraft[redacted]"; }
    }

    final JTextField name = new JTextField(20), username = new JTextField(20);
    final JComboBox<KeyChoice> key = new JComboBox<>();
    final JPasswordField password = new JPasswordField(20);
    final JLabel reuse = new JLabel(" ");
    final JButton reveal = new JButton(VaultIcons.icon("eye")), copy = new JButton(VaultIcons.icon("copy"));
    Runnable changed = () -> {};
    private final char defaultEcho = password.getEchoChar();
    private boolean loading, passwordChanged, dirty;

    VaultLoginForm(boolean editor) {
        super(new BorderLayout(0, 6));
        JPanel fields = VaultUi.form();
        VaultUi.field(fields, 0, editor ? "Name" : "Login name", name);
        VaultUi.field(fields, 1, "Username", username);
        VaultUi.field(fields, 2, "SSH key", key);
        JPanel secret = new JPanel(new BorderLayout(4, 0)); secret.add(password);
        if (editor) secret.add(VaultUi.row(reveal, copy), BorderLayout.EAST);
        VaultUi.field(fields, 3, "Password", secret);
        VaultUi.field(fields, 4, "", reuse);
        add(fields, BorderLayout.NORTH);
        reveal.setToolTipText("Reveal or hide password"); reveal.getAccessibleContext().setAccessibleName("Reveal or hide password");
        copy.setToolTipText("Copy password"); copy.getAccessibleContext().setAccessibleName("Copy password");
        password.getAccessibleContext().setAccessibleName("Password");
        VaultUi.changes(name, this::mark); VaultUi.changes(username, this::mark);
        VaultUi.changes(password, () -> { if (!loading) passwordChanged = true; mark(); });
        key.addActionListener(e -> mark());
        setMinimumSize(new Dimension(0, 0));
        load(null, List.of());
    }

    private void mark() { if (!loading) { dirty = true; changed.run(); } }

    void keys(List<VaultSnapshot.KeyInfo> keys, UUID selected) {
        boolean before = loading; loading = true;
        key.removeAllItems(); key.addItem(new KeyChoice(null, "None"));
        for (var k : keys) key.addItem(new KeyChoice(k.id(), k.name() + " · " + k.algorithm()));
        choose(selected); loading = before;
    }

    void choose(UUID id) {
        for (int i = 0; i < key.getItemCount(); i++)
            if (Objects.equals(key.getItemAt(i).id(), id)) { key.setSelectedIndex(i); return; }
        key.setSelectedIndex(0);
    }

    void load(VaultSnapshot.LoginInfo login, List<VaultSnapshot.KeyInfo> keys) {
        loading = true;
        name.setText(login == null ? "" : login.name()); username.setText(login == null ? "" : login.username());
        keys(keys, login == null ? null : login.keyId());
        password.setText(""); password.setEchoChar(defaultEcho);
        password.putClientProperty("JTextField.placeholderText", login != null && login.hasPassword() ? "Saved password" : "Not set");
        reuse.setText(keys.stream().filter(k -> login != null && k.id().equals(login.keyId())).findFirst()
            .map(k -> k.algorithm() + " · shared by " + k.loginUses() + " login" + (k.loginUses() == 1 ? "" : "s")).orElse(" "));
        passwordChanged = false; dirty = false; loading = false;
    }

    boolean dirty() { return dirty; }
    boolean hasTypedPassword() { return passwordChanged; }
    Draft draft() {
        KeyChoice choice = (KeyChoice) key.getSelectedItem();
        return new Draft(name.getText().strip(), username.getText().strip(), choice == null ? null : choice.id(),
            passwordChanged ? password.getPassword() : null);
    }
    void reveal(char[] secret) { loading = true; password.setText(new String(secret)); password.setEchoChar((char) 0); loading = false; }
    boolean revealed() { return password.getEchoChar() == 0; }
    void hideSecret() { loading = true; if (!passwordChanged) password.setText(""); password.setEchoChar(defaultEcho); loading = false; }
    void clear() { load(null, List.of()); }
}
