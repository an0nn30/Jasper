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

    /** Created by the vault; the arrays become this credential's to zero. */
    public Credential(UUID id, String name, Kind kind, String username, char[] password, Path keyPath, char[] passphrase) {
        this(id, name, kind, username, password, keyPath, null, passphrase);
    }

    /** Consumes arrays; a managed key uses bytes instead of a path. */
    public Credential(UUID id, String name, Kind kind, String username, char[] password, Path keyPath, byte[] keyBytes, char[] passphrase) {
        if (keyPath != null && keyBytes != null) throw new IllegalArgumentException("A key must have one source");
        this.keyBytes = keyBytes;
        this.id = Objects.requireNonNull(id, "id"); this.name = Objects.requireNonNull(name, "name"); this.kind = Objects.requireNonNull(kind, "kind");
        this.username = username; this.password = password; this.keyPath = keyPath; this.passphrase = passphrase;
    }

    public UUID id() { open(); return id; }
    public String name() { open(); return name; }
    public Kind kind() { open(); return kind; }
    /** Empty for a bare SSH key. */
    public Optional<String> username() { open(); return Optional.ofNullable(username); }
    /** Null unless the kind has a password. Not a copy: it is zeroed by {@link #close()}. */
    public char[] password() { open(); return password; }
    public Optional<byte[]> keyBytes() { open(); return Optional.ofNullable(keyBytes); }
    public Optional<Path> keyPath() { open(); return Optional.ofNullable(keyPath); }
    /** Null when the key has no passphrase or the kind has no key. */
    public char[] passphrase() { open(); return passphrase; }

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
