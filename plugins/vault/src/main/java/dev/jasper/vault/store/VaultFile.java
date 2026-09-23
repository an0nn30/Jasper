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

    /** Preserves the encrypted pre-upgrade file without ever replacing an earlier backup. */
    public void backupVersionOne(byte[] encrypted) throws IOException {
        Path backup = path.resolveSibling(path.getFileName() + ".v1-backup");
        if (Files.exists(backup, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(backup, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Vault backup is not a regular file");
            try { dev.jasper.vault.crypto.VaultFileFormat.parse(Files.readAllBytes(backup)); }
            catch (RuntimeException invalid) { throw new IOException("Existing Vault backup is invalid"); }
            ownerOnly(backup); return;
        }
        boolean created = false;
        try {
            if (Files.getFileStore(path.getParent()).supportsFileAttributeView("posix"))
                Files.createFile(backup, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            else Files.createFile(backup);
            created = true; ownerOnly(backup);
            Files.write(backup, encrypted, java.nio.file.StandardOpenOption.WRITE);
        } catch (IOException failure) { if (created) Files.deleteIfExists(backup); throw failure; }
    }

    static void ownerOnly(Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        else {
            var acl = Files.getFileAttributeView(file, java.nio.file.attribute.AclFileAttributeView.class);
            if (acl != null) acl.setAcl(java.util.List.of(java.nio.file.attribute.AclEntry.newBuilder()
                .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(Files.getOwner(file))
                .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build()));
        }
    }
}
