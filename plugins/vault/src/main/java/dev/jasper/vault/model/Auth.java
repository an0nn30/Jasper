package dev.jasper.vault.model;

import dev.jasper.vault.crypto.SecureBytes;
import java.nio.file.Path;
import java.util.Objects;

/** How an account authenticates. Secret arrays are owned by the record; {@link #zero()} clears them. */
public sealed interface Auth {
    void zero();

    record Password(char[] password) implements Auth {
        public Password { Objects.requireNonNull(password, "password"); }
        @Override public void zero() { SecureBytes.zero(password); }
    }

    /** {@code passphrase} is null when the key has none. */
    record Key(Path keyPath, char[] passphrase) implements Auth {
        public Key { Objects.requireNonNull(keyPath, "keyPath"); }
        @Override public void zero() { SecureBytes.zero(passphrase); }
    }

    /** {@code passphrase} is null when the key has none. */
    record KeyAndPassword(Path keyPath, char[] passphrase, char[] password) implements Auth {
        public KeyAndPassword { Objects.requireNonNull(keyPath, "keyPath"); Objects.requireNonNull(password, "password"); }
        @Override public void zero() { SecureBytes.zero(passphrase); SecureBytes.zero(password); }
    }
}
