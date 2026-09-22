package dev.jasper.vault.store;

import java.io.IOException;
import java.util.Optional;

/** Where the 32-byte device secret lives: the platform keychain ({@link KeychainStore}) or a file ({@link FileStore}). */
public interface DeviceSecretStore {
    Optional<byte[]> read() throws IOException;
    void write(byte[] secret) throws IOException;
    void delete() throws IOException;
    /** Shown in the manager's status line: {@code keychain} or {@code file (no keychain found)}. */
    String description();
}
