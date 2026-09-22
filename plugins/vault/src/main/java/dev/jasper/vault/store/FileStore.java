package dev.jasper.vault.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The fallback: {@code data/device.secret}, owner-readable only. */
public final class FileStore implements DeviceSecretStore {
    private final Path file;

    public FileStore(Path file) { this.file = file; }

    @Override public Optional<byte[]> read() throws IOException {
        return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
    }

    @Override public void write(byte[] secret) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, secret);
        VaultFile.ownerOnly(file);
    }

    @Override public void delete() throws IOException { Files.deleteIfExists(file); }
    @Override public String description() { return "file (no keychain found)"; }
}
