package dev.jasper.vault.api;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The caller's own copy of a secret. Fetch it just before use and {@link #close()} right after: closing
 * zeroes the arrays and every later accessor throws {@link IllegalStateException}. The vault keeps nothing
 * about it, so an auto-lock never invalidates a credential already in hand.
 */
public final class Credential implements AutoCloseable {
    private final UUID id;
    private final String name;
    private final Kind kind;
    private final String username;
    private final char[] password;
    private final Path keyPath;
    private final byte[] keyBytes;
    private final char[] passphrase;
    private boolean closed;

    /**
     * Created by the vault, for a credential without key bytes; the arrays become this credential's to zero.
     *
     * @param id the entry's id
     * @param name the entry's name
     * @param kind what the credential carries
     * @param username the account's username, or null for a bare SSH key
     * @param password the password, or null when the kind has none
     * @param keyPath the private key file, or null when the kind has no key
     * @param passphrase the key's passphrase, or null when there is none
     */
    public Credential(UUID id, String name, Kind kind, String username, char[] password, Path keyPath, char[] passphrase) {
        this(id, name, kind, username, password, keyPath, null, passphrase);
    }

    /**
     * Created by the vault; the arrays become this credential's to zero. A key stored in Vault comes as
     * {@code keyBytes}, a key file as {@code keyPath}.
     *
     * @param id the entry's id
     * @param name the entry's name
     * @param kind what the credential carries
     * @param username the account's username, or null for a bare SSH key
     * @param password the password, or null when the kind has none
     * @param keyPath the private key file, or null for a key stored in Vault and when the kind has no key
     * @param keyBytes the stored private key file's contents, or null unless the key is stored in Vault
     * @param passphrase the key's passphrase, or null when there is none
     * @throws IllegalArgumentException when both {@code keyPath} and {@code keyBytes} are given
     */
    public Credential(UUID id, String name, Kind kind, String username, char[] password, Path keyPath, byte[] keyBytes, char[] passphrase) {
        if (keyPath != null && keyBytes != null) throw new IllegalArgumentException("A key must have one source");
        this.keyBytes = keyBytes;
        this.id = Objects.requireNonNull(id, "id"); this.name = Objects.requireNonNull(name, "name"); this.kind = Objects.requireNonNull(kind, "kind");
        this.username = username; this.password = password; this.keyPath = keyPath; this.passphrase = passphrase;
    }

    /**
     * The entry's id in Vault, the same as its {@link CredentialDescriptor}'s.
     *
     * @return the id
     */
    public UUID id() { open(); return id; }
    /**
     * The entry's name in Vault.
     *
     * @return the name
     */
    public String name() { open(); return name; }
    /**
     * What this credential carries, which decides which of the other accessors have a value.
     *
     * @return the kind
     */
    public Kind kind() { open(); return kind; }
    /**
     * The account's username.
     *
     * @return the username; empty for a bare SSH key
     */
    public Optional<String> username() { open(); return Optional.ofNullable(username); }
    /**
     * The password, for {@link Kind#ACCOUNT_PASSWORD} and {@link Kind#ACCOUNT_KEY_AND_PASSWORD}. Not a copy: it is
     * zeroed by {@link #close()}.
     *
     * @return the password; null unless the kind has a password
     */
    public char[] password() { open(); return password; }
    /**
     * The private key file's contents, for an SSH key stored in Vault, still protected by {@link #passphrase()} when
     * that is not null. Not a copy: it is zeroed by {@link #close()}.
     *
     * @return the key; empty for every other credential
     */
    public Optional<byte[]> keyBytes() { open(); return Optional.ofNullable(keyBytes); }
    /**
     * The private key file, for an account that signs in with a key and for an SSH key Vault refers to by its path.
     *
     * @return the file; empty for a password account and for a key stored in Vault
     */
    public Optional<Path> keyPath() { open(); return Optional.ofNullable(keyPath); }
    /**
     * The key's passphrase. Not a copy: it is zeroed by {@link #close()}.
     *
     * @return the passphrase; null when the kind has no key, when the key has no passphrase, and for an SSH key Vault
     *     refers to by its path (Vault holds no passphrase for it)
     */
    public char[] passphrase() { open(); return passphrase; }

    /**
     * Zeroes the password, passphrase and key bytes; afterwards every accessor throws {@link IllegalStateException}.
     * Closing again does nothing.
     */
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
        if (password != null) Arrays.fill(password, (char) 0);
        if (passphrase != null) Arrays.fill(passphrase, (char) 0);
    }

    private void open() { if (closed) throw new IllegalStateException("The credential is closed"); }

    @Override public String toString() { return "Credential[" + name + ", " + kind + (closed ? ", closed" : "") + "]"; }
}
