package dev.jasper.vault.ui;

import dev.jasper.vault.keygen.KeyAlgorithm;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Generates a standalone SSH key and, optionally, an account referring to it. */
public final class KeyGeneratorForm extends EditorForm {
    public record Request(KeyAlgorithm algorithm, String name, String comment, Optional<String> username) { }
    final JTextField name = new JTextField(28), comment = new JTextField(28), username = new JTextField(28);
    final JComboBox<KeyAlgorithm> algorithm = new JComboBox<>(KeyAlgorithm.values());
    final JCheckBox alsoAccount = new JCheckBox("Also add a login account", false);
    final JLabel description = new JLabel("<html>Generated private key files are unencrypted.<br>The vault stores their paths.</html>");
    public KeyGeneratorForm(Function<Request, CompletableFuture<Void>> generate, Runnable close) {
        super(close); save.setText("Generate Key");
        field("Name", name); field("Algorithm", algorithm); field("Comment", comment);
        field("Key files", description); field("Account", alsoAccount); field("Account username", username);
        submit(() -> {
            EntryEditor.requireName(name.getText());
            String user = username.getText().strip();
            if (alsoAccount.isSelected() && user.isEmpty()) throw new IllegalArgumentException("Enter the account username");
            if (comment.getText().indexOf('\n') >= 0 || comment.getText().indexOf('\r') >= 0)
                throw new IllegalArgumentException("The key comment must fit on one line");
            var request = new Request((KeyAlgorithm) algorithm.getSelectedItem(), name.getText().strip(), comment.getText().strip(),
                alsoAccount.isSelected() ? Optional.of(user) : Optional.empty());
            cancel.setEnabled(false);
            try { return generate.apply(request).whenComplete((ignored, failure) -> cancel.setEnabled(true)); }
            catch (RuntimeException failure) { cancel.setEnabled(true); throw failure; }
        });
    }
}
