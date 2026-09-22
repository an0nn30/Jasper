package dev.jasper.vault.ui;

import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.crypto.SecureBytes;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Generates a standalone SSH key and, optionally, an account referring to it. */
public final class KeyGeneratorForm extends EditorForm {
    /** The callback borrows passphrase synchronously; it must copy before returning an asynchronous result. */
    public record Request(KeyAlgorithm algorithm, String name, String comment, Optional<String> username, char[] passphrase) { }
    final SecretDocument passphrase = secret(), confirm = secret();
    final JTextField name = new JTextField(28), comment = new JTextField(28), username = new JTextField(28);
    final JComboBox<KeyAlgorithm> algorithm = new JComboBox<>(KeyAlgorithm.values());
    final JCheckBox alsoAccount = new JCheckBox("Also add a login account", false);
    final JLabel description = new JLabel("<html>A passphrase encrypts the private key file.<br>Leave empty for an unencrypted key.<br>A login account stores the passphrase in the vault.</html>");
    public KeyGeneratorForm(Function<Request, CompletableFuture<Void>> generate, Runnable close) {
        super(close); save.setText("Generate Key");
        field("Name", name); field("Algorithm", algorithm); field("Comment", comment);
        field("Passphrase (optional)", passwordField(passphrase)); field("Confirm passphrase", passwordField(confirm));
        field("Key files", description); field("Account", alsoAccount); field("Account username", username);
        submit(() -> {
            EntryEditor.requireName(name.getText());
            String user = username.getText().strip();
            if (alsoAccount.isSelected() && user.isEmpty()) throw new IllegalArgumentException("Enter the account username");
            if (comment.getText().indexOf('\n') >= 0 || comment.getText().indexOf('\r') >= 0)
                throw new IllegalArgumentException("The key comment must fit on one line");
            char[] phrase = passphrase.snapshot(), again = confirm.snapshot();
            try {
                if (!Arrays.equals(phrase, again)) throw new IllegalArgumentException("The passphrases do not match");
                var request = new Request((KeyAlgorithm) algorithm.getSelectedItem(), name.getText().strip(), comment.getText().strip(),
                    alsoAccount.isSelected() ? Optional.of(user) : Optional.empty(), phrase);
                cancel.setEnabled(false);
                try { return generate.apply(request).whenComplete((ignored, failure) -> cancel.setEnabled(true)); }
                catch (RuntimeException failure) { cancel.setEnabled(true); throw failure; }
            } finally { SecureBytes.zero(phrase); SecureBytes.zero(again); }
        });
    }
}
