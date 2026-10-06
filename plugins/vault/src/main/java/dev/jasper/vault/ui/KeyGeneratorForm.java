package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.keygen.KeyAlgorithm;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Generates a standalone SSH key and, optionally, an account referring to it. */
public final class KeyGeneratorForm extends EditorForm {
    /** The callback borrows passphrase synchronously; it must copy before returning an asynchronous result. */
    public record Request(KeyAlgorithm algorithm, String name, String comment, Optional<String> username, char[] passphrase) { }
    static final String NAME = "name", ALGORITHM = "algorithm", COMMENT = "comment", PASSPHRASE = "passphrase", CONFIRM = "confirm",
        KEY_FILES = "key-files", ACCOUNT = "account", USERNAME = "account-username";
    static final String DESCRIPTION = "A passphrase encrypts the private key file.\nLeave empty for an unencrypted key.\n"
        + "A login account stores the passphrase in the vault.";

    public KeyGeneratorForm(UiKit kit, Function<Request, CompletableFuture<Void>> generate, Runnable close) {
        super(kit, close);
        setSaveLabel("Generate Key");
        text(NAME, "Name", "");
        choice(ALGORITHM, "Algorithm", Arrays.stream(KeyAlgorithm.values()).map(a -> new Option(a.name(), a.toString(), Optional.empty())).toList(),
            KeyAlgorithm.values()[0].name());
        text(COMMENT, "Comment", "");
        secret(PASSPHRASE, "Passphrase (optional)", "passphrase");
        secret(CONFIRM, "Confirm passphrase", "passphrase");
        description(KEY_FILES, "Key files", DESCRIPTION);
        toggle(ACCOUNT, "Also add a login account", false);
        text(USERNAME, "Account username", "");
        submit(() -> {
            EntryEditor.requireName(value(NAME));
            String user = value(USERNAME).strip();
            if (checked(ACCOUNT) && user.isEmpty()) throw invalid(USERNAME, "Enter the account username");
            if (value(COMMENT).indexOf('\n') >= 0 || value(COMMENT).indexOf('\r') >= 0)
                throw invalid(COMMENT, "The key comment must fit on one line");
            char[] phrase = copySecret(PASSPHRASE), again = copySecret(CONFIRM);
            try {
                if (!Arrays.equals(phrase, again)) throw invalid(CONFIRM, "The passphrases do not match");
                var request = new Request(KeyAlgorithm.valueOf(selected(ALGORITHM)), value(NAME).strip(), value(COMMENT).strip(),
                    checked(ACCOUNT) ? Optional.of(user) : Optional.empty(), phrase);
                setCancelEnabled(false);
                try { return generate.apply(request).whenComplete((ignored, failure) -> setCancelEnabled(true)); }
                catch (RuntimeException failure) { setCancelEnabled(true); throw failure; }
            } finally { SecureBytes.zero(phrase); SecureBytes.zero(again); }
        });
    }
}
