package dev.jasper.app.vault;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

final class VaultFiles {
    static final int MAX_FILE = 16 * 1024 * 1024;
    static byte[] read(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, NOFOLLOW_LINKS)) throw new IOException("Vault is not a regular file");
        try (var in = Files.newInputStream(file, NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(MAX_FILE + 1);
            if (bytes.length > MAX_FILE) throw new IOException("Vault too large"); return bytes;
        }
    }
    static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    static void privatePermissions(Path file) throws IOException {
        var posix = Files.getFileAttributeView(file, PosixFileAttributeView.class, NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(Files.isDirectory(file, NOFOLLOW_LINKS) ? "rwx------" : "rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(file, AclFileAttributeView.class, NOFOLLOW_LINKS);
        if (acl == null) throw new IOException("Private vault permissions unavailable");
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
    static void directory(Path parent) throws IOException {
        if (Files.exists(parent, NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(parent, NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) throw new IOException("Invalid vault directory");
            return;
        }
        Path ancestor = parent.getParent(); if (ancestor != null) directory(ancestor);
        try { Files.createDirectory(parent); privatePermissions(parent); }
        catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(parent, NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) throw e;
        }
    }
    static void publish(Path target, byte[] bytes, byte[] expectedHash, boolean create) throws IOException {
        if (bytes.length > MAX_FILE) throw new IOException("Vault too large");
        target = target.toAbsolutePath().normalize(); directory(target.getParent());
        Path lockPath = target.resolveSibling(target.getFileName() + ".lck");
        if (Files.isSymbolicLink(lockPath)) throw new IOException("Invalid vault lock file");
        try (FileChannel channel = FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS)) {
            privatePermissions(lockPath);
            try (FileLock lock = channel.tryLock()) {
                if (lock == null || !lock.isValid()) throw new IOException("Vault is being saved by another process");
                if (create && Files.exists(target, NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
                if (!create && (expectedHash == null || !MessageDigest.isEqual(expectedHash, hash(read(target)))))
                    throw new IOException("Vault changed externally; lock and unlock again before saving");
                Path tmp = Files.createTempFile(target.getParent(), ".jasper-vault-", ".tmp");
                try {
                    privatePermissions(tmp);
                    try (FileChannel out = FileChannel.open(tmp, WRITE, TRUNCATE_EXISTING, NOFOLLOW_LINKS)) {
                        ByteBuffer b = ByteBuffer.wrap(bytes); while (b.hasRemaining()) out.write(b); out.force(true);
                    }
                    if (create) Files.createLink(target, tmp);
                    else Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(tmp); }
            } catch (OverlappingFileLockException e) { throw new IOException("Vault is being saved by another instance"); }
        }
    }
    static void replaceHint(Path target, byte[] bytes) throws IOException {
        boolean exists = Files.exists(target, NOFOLLOW_LINKS);
        publish(target, bytes, exists ? hash(read(target)) : null, !exists);
    }
}
