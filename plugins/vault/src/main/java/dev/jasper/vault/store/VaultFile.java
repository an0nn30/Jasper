package dev.jasper.vault.store;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/** The encrypted vault on disk. Writes go to a sibling temp file that is moved into place. */
public final class VaultFile {
    private final Path path;

    public VaultFile(Path path) { this.path = path; }

    public Path path() { return path; }
    public boolean exists() { return Files.isRegularFile(path); }
    public byte[] read() throws IOException { return Files.readAllBytes(path); }

    public void write(byte[] bytes) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, bytes);
        ownerOnly(temp);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void ownerOnly(Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }
}
