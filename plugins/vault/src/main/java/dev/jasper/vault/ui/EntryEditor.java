package dev.jasper.vault.ui;

import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Account, existing-key and multiline secure-note editing; drafts own their secret copies. */
public final class EntryEditor extends EditorForm {
    final JTextField name = new JTextField(28), username = new JTextField(28);
    final JTextField privatePath = new JTextField(28), publicPath = new JTextField(28), comment = new JTextField(28);
    final JComboBox<String> auth = new JComboBox<>(new String[] {"Password", "Key", "Key and password"});
    final SecretDocument password = secret(), passphrase = secret(), noteText = secret();
    private EntryEditor(Runnable close) { super(close); field("Name", name); }

    public static EntryEditor login(Account initial, Function<Account, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(close);
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        Instant created = initial == null ? Instant.now() : initial.created();
        if (initial != null) {
            form.name.setText(initial.name()); form.username.setText(initial.username());
            switch (initial.auth()) {
                case Auth.Password value -> { form.auth.setSelectedIndex(0); form.password.replace(value.password()); }
                case Auth.Key value -> {
                    form.auth.setSelectedIndex(1); form.privatePath.setText(value.keyPath().toString());
                    if (value.passphrase() != null) form.passphrase.replace(value.passphrase());
                }
                case Auth.KeyAndPassword value -> {
                    form.auth.setSelectedIndex(2); form.privatePath.setText(value.keyPath().toString()); form.password.replace(value.password());
                    if (value.passphrase() != null) form.passphrase.replace(value.passphrase());
                }
            }
        }
        form.field("Username", form.username); form.field("Authentication", form.auth);
        form.field("Password", form.passwordField(form.password)); form.fileField("Private key path", form.privatePath);
        form.field("Key passphrase (optional)", form.passwordField(form.passphrase));
        form.submit(() -> {
            requireName(form.name.getText());
            int choice = form.auth.getSelectedIndex();
            Path path = choice == 0 ? null : requiredPath(form.privatePath.getText());
            char[] secret = choice == 1 ? null : form.password.snapshot();
            char[] phrase = choice == 0 ? null : form.passphrase.snapshot();
            if (phrase != null && phrase.length == 0) { SecureBytes.zero(phrase); phrase = null; }
            Auth value = switch (choice) {
                case 0 -> new Auth.Password(secret);
                case 1 -> new Auth.Key(path, phrase);
                default -> new Auth.KeyAndPassword(path, phrase, secret);
            };
            try { return save.apply(new Account(id, form.name.getText().strip(), form.username.getText(), value, created, Instant.now())); }
            catch (RuntimeException failure) { value.zero(); throw failure; }
        });
        return form;
    }

    public static EntryEditor note(Note initial, Function<Note, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(close);
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        if (initial != null) { form.name.setText(initial.name()); form.noteText.replace(initial.text()); }
        var text = new JTextArea(form.noteText, null, 8, 32);
        text.setLineWrap(true); text.setWrapStyleWord(true);
        form.field("Secure note", new JScrollPane(text));
        form.submit(() -> {
            requireName(form.name.getText());
            Note value = new Note(id, form.name.getText().strip(), form.noteText.snapshot(), Instant.now());
            try { return save.apply(value); }
            catch (RuntimeException failure) { value.zero(); throw failure; }
        });
        return form;
    }

    public static EntryEditor key(SshKey initial, Function<SshKey, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(close);
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        Instant created = initial == null ? Instant.now() : initial.created();
        if (initial != null) {
            form.name.setText(initial.name()); form.privatePath.setText(initial.privatePath().toString());
            form.publicPath.setText(initial.publicPath().toString()); form.comment.setText(initial.comment());
        }
        form.fileField("Private key path", form.privatePath); form.fileField("Public key path", form.publicPath); form.field("Comment", form.comment);
        form.submit(() -> {
            requireName(form.name.getText());
            return save.apply(new SshKey(id, form.name.getText().strip(), "", "", form.comment.getText(),
                requiredPath(form.privatePath.getText()), requiredPath(form.publicPath.getText()), created));
        });
        return form;
    }
    static Path requiredPath(String text) {
        if (text.isBlank()) throw new IllegalArgumentException("Choose a key file path");
        return Path.of(text.strip()).toAbsolutePath().normalize();
    }
    static void requireName(String value) { if (value.isBlank()) throw new IllegalArgumentException("Enter a name"); }
}
