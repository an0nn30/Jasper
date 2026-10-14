package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The files beside an interprocess socket that only the owner may read: the token a client sends back and the id of
 * the process that holds the socket.
 */
public final class OwnerOnlyFiles {
    private static final int TOKEN_BYTES = 32;

    private OwnerOnlyFiles() { }

    /**
     * Writes a new random token and a newline to {@code file}, readable only by its owner, and returns the token
     * (43 URL-safe base64 characters).
     */
    public static String writeToken(Path file) throws IOException {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        write(file, token);
        return token;
    }

    /** Writes this process's id and a newline to {@code file}, readable only by its owner. */
    public static void writeProcessId(Path file) throws IOException {
        write(file, Long.toString(ProcessHandle.current().pid()));
    }

    /** Owner-only where the file system understands it; Windows relies on the per-user directory's ACL. */
    public static void restrict(Path path, String permissions) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }

    private static void write(Path file, String line) throws IOException {
        Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrict(file, "rw-------");
    }
}
