package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Account, existing-key and multiline secure-note editing; drafts own their secret copies. */
public final class EntryEditor extends EditorForm {
    static final String NAME = "name", USERNAME = "username", AUTH = "auth", PASSWORD = "password", PRIVATE_PATH = "private-path",
        ALSO_PASSWORD = "also-password", PASSPHRASE = "passphrase", PUBLIC_PATH = "public-path", COMMENT = "comment", NOTE = "note";
    static final String AUTH_PASSWORD = "password", AUTH_KEY = "key", AUTH_BOTH = "key-and-password";
    static final List<Option> AUTH_OPTIONS = List.of(new Option(AUTH_PASSWORD, "Password", Optional.empty()),
        new Option(AUTH_KEY, "Private Key", Optional.empty()));

    private EntryEditor(UiKit kit, Runnable close, String name) { super(kit, close); discreetInputs(); text(NAME, "Name", name); }

    public static EntryEditor login(UiKit kit, Account initial, Function<Account, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(kit, close, initial == null ? "" : initial.name());
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        Instant created = initial == null ? Instant.now() : initial.created();
        String auth = AUTH_PASSWORD, keyPath = "";
        char[] password = null, passphrase = null;
        if (initial != null) switch (initial.auth()) {
            case Auth.Password value -> password = value.password();
            case Auth.Key value -> { auth = AUTH_KEY; keyPath = value.keyPath().toString(); passphrase = value.passphrase(); }
            case Auth.KeyAndPassword value -> {
                auth = AUTH_KEY; keyPath = value.keyPath().toString(); password = value.password(); passphrase = value.passphrase();
            }
        }
        form.hint(NAME, "e.g. Production server");
        form.hint(USERNAME, "root");
        form.hint(PRIVATE_PATH, "~/.ssh/id_ed25519");
        form.text(USERNAME, "Username", initial == null ? "" : initial.username());
        form.identity("user", Nodes.Hue.BLUE);
        form.segmented(AUTH, "Authentication", AUTH_OPTIONS, auth);
        form.toggle(ALSO_PASSWORD, "Also use password", initial != null && initial.auth() instanceof Auth.KeyAndPassword);
        form.visibleWhen(ALSO_PASSWORD, () -> !form.selected(AUTH).equals(AUTH_PASSWORD));
        form.visibleWhen(PRIVATE_PATH, () -> !form.selected(AUTH).equals(AUTH_PASSWORD));
        form.visibleWhen(PASSPHRASE, () -> !form.selected(AUTH).equals(AUTH_PASSWORD));
        form.visibleWhen(PASSWORD, () -> form.selected(AUTH).equals(AUTH_PASSWORD) || form.checked(ALSO_PASSWORD));
        form.group("identity-fields", "", NAME, USERNAME);
        form.group("authentication", "Authentication", AUTH, PRIVATE_PATH, PASSPHRASE, ALSO_PASSWORD, PASSWORD);
        form.classicGroup("Login"); form.classicLabel(AUTH, "Method"); form.classicLabel(PRIVATE_PATH, "Private key");
        form.readyWhen(() -> !form.value(NAME).isBlank() && (form.selected(AUTH).equals(AUTH_PASSWORD) || validPath(form.value(PRIVATE_PATH))));
        form.secret(PASSWORD, "Password", "");
        form.path(PRIVATE_PATH, "Private Key", keyPath);
        form.secret(PASSPHRASE, "Passphrase", "");
        // The borrowed record keeps its own arrays; the view copies them into its wipeable storage.
        if (password != null) form.replaceSecret(PASSWORD, password);
        if (passphrase != null) form.replaceSecret(PASSPHRASE, passphrase);
        form.submit(() -> {
            requireName(form.value(NAME));
            String choice = form.selected(AUTH).equals(AUTH_PASSWORD) ? AUTH_PASSWORD : form.checked(ALSO_PASSWORD) ? AUTH_BOTH : AUTH_KEY;
            Path path = choice.equals(AUTH_PASSWORD) ? null : requiredPath(PRIVATE_PATH, form.value(PRIVATE_PATH));
            char[] secret = choice.equals(AUTH_KEY) ? null : form.copySecret(PASSWORD);
            char[] phrase = choice.equals(AUTH_PASSWORD) ? null : form.copySecret(PASSPHRASE);
            if (phrase != null && phrase.length == 0) { SecureBytes.zero(phrase); phrase = null; }
            Auth value = switch (choice) {
                case AUTH_PASSWORD -> new Auth.Password(secret);
                case AUTH_KEY -> new Auth.Key(path, phrase);
                default -> new Auth.KeyAndPassword(path, phrase, secret);
            };
            try { return save.apply(new Account(id, form.value(NAME).strip(), form.value(USERNAME), value, created, Instant.now())); }
            catch (RuntimeException failure) { value.zero(); throw failure; }
        });
        return form;
    }

    public static EntryEditor note(UiKit kit, Note initial, Function<Note, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(kit, close, initial == null ? "" : initial.name());
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        form.identity("note", Nodes.Hue.AMBER);
        form.classicGroup("Secure Note");
        form.readyWhen(() -> !form.value(NAME).isBlank());
        form.note(NOTE, "Secure note");
        if (initial != null) form.replaceSecret(NOTE, initial.text());
        form.submit(() -> {
            requireName(form.value(NAME));
            Note value = new Note(id, form.value(NAME).strip(), form.copySecret(NOTE), Instant.now());
            try { return save.apply(value); }
            catch (RuntimeException failure) { value.zero(); throw failure; }
        });
        return form;
    }

    public static EntryEditor key(UiKit kit, SshKey initial, Function<SshKey, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(kit, close, initial == null ? "" : initial.name());
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        Instant created = initial == null ? Instant.now() : initial.created();
        form.identity("key", Nodes.Hue.GREEN);
        form.hint(NAME, "e.g. Work laptop key");
        form.hint(PRIVATE_PATH, "~/.ssh/id_ed25519");
        form.hint(PUBLIC_PATH, "~/.ssh/id_ed25519.pub");
        form.hint(COMMENT, "Optional");
        form.classicGroup("SSH Key"); form.classicLabel(PRIVATE_PATH, "Private key"); form.classicLabel(PUBLIC_PATH, "Public key");
        form.readyWhen(() -> !form.value(NAME).isBlank() && validPath(form.value(PRIVATE_PATH)) && validPath(form.value(PUBLIC_PATH)));
        form.path(PRIVATE_PATH, "Private Key", initial == null ? "" : initial.privatePath().toString());
        form.path(PUBLIC_PATH, "Public Key", initial == null ? "" : initial.publicPath().toString());
        form.text(COMMENT, "Comment", initial == null ? "" : initial.comment());
        form.submit(() -> {
            requireName(form.value(NAME));
            return save.apply(new SshKey(id, form.value(NAME).strip(), "", "", form.value(COMMENT),
                requiredPath(PRIVATE_PATH, form.value(PRIVATE_PATH)), requiredPath(PUBLIC_PATH, form.value(PUBLIC_PATH)), created));
        });
        return form;
    }

    private static boolean validPath(String value) {
        try { if (value.isBlank()) return false; Path.of(value.strip()); return true; }
        catch (java.nio.file.InvalidPathException invalid) { return false; }
    }

    static Path requiredPath(String field, String text) {
        if (text.isBlank()) throw invalid(field, "Choose a key file path");
        return Path.of(text.strip()).toAbsolutePath().normalize();
    }
    static void requireName(String value) { if (value.isBlank()) throw invalid(NAME, "Enter a name"); }
}
