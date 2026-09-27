package dev.jasper.remote.edit;

import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.remote.sftp.WriteHandle;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The editor's write contract (editor spec 4.4) over any {@link FileEndpoint}, in POSIX path text. Blocking
 * and endpoint-confined: call it on the worker that owns the endpoint.
 */
public final class SafeWrite {
    static final int MAX_LINKS = 40;

    /**
     * Where a path's links lead, and what is there.
     *
     * @param path the resolved path, whose folders are real
     * @param entry a no-follow stat of it, or empty when nothing is there
     */
    public record Resolved(String path, Optional<FileEntry> entry) { }

    /**
     * Marks a failed write that could not remove its hidden sibling: attached to the write's failure as a suppressed
     * exception, so the failure keeps its own type and message.
     */
    public static final class SiblingLeft extends IOException {
        private final String path;

        SiblingLeft(String path, Throwable cause) {
            super("Could not remove " + path, cause);
            this.path = path;
        }

        /** @return the sibling's path, which is still on the endpoint */
        public String path() { return path; }
    }

    private SafeWrite() { }

    /**
     * @param failure a failure of {@link #write}, possibly wrapped by a future
     * @return the path of the hidden sibling that the failed write left behind, or empty
     */
    public static Optional<String> leftover(Throwable failure) {
        Throwable at = failure;
        while ((at instanceof CompletionException || at instanceof ExecutionException) && at.getCause() != null) at = at.getCause();
        if (at == null) return Optional.empty();
        for (Throwable suppressed : at.getSuppressed()) {
            if (suppressed instanceof SiblingLeft left) return Optional.of(left.path());
        }
        return Optional.empty();
    }

    /**
     * Follows every symbolic link in {@code path}, relative ones from their own folder.
     *
     * @return where it leads; the last component need not exist
     * @throws IOException after more than 40 links, when a folder on the way is a file, or when the endpoint fails
     */
    public static Resolved resolve(FileEndpoint endpoint, String path) throws IOException {
        String absolute = FilePaths.absolute(path);
        try {
            FileEntry entry = endpoint.stat(absolute);
            if (entry.kind() != FileEntry.Kind.LINK) return new Resolved(absolute, Optional.of(entry));
        } catch (NoSuchFileException missing) {
            return new Resolved(absolute, Optional.empty());
        } catch (IOException linkedFolder) {
            // stat refuses a path through a linked folder; the walk below follows it (and rethrows real failures).
        }
        return walk(endpoint, absolute);
    }

    /**
     * Replaces the file at {@code path}: resolve links; refuse a folder, a special file or a read-only file;
     * write an owner-only hidden sibling, give it the target's permissions and atomically replace the target.
     * Writes in place instead when the target's permissions are unknown (an owner-only sibling could not be
     * restored to the same mode), when creating the sibling is denied (the folder disallows creating it
     * though the file itself is writable), when the sibling would change the owner or group, or when atomic
     * replacement is unavailable; creates a missing target. Any other failure creating the sibling is
     * rethrown unchanged, not treated as a signal to write in place. Any failure after the sibling exists removes
     * it; when that fails too (an aborted SFTP endpoint refuses every later call), the failure carries a
     * {@link SiblingLeft} (see {@link #leftover}). A sibling that cannot be removed before writing in place fails
     * the save with the removal failure, marked the same way, and the target stays unchanged.
     *
     * @return a stat of the target after writing
     */
    public static FileEntry write(FileEndpoint endpoint, String path, byte[] content) throws IOException {
        Resolved resolved = resolve(endpoint, path);
        String target = resolved.path();
        Optional<FileEntry> existing = resolved.entry();
        if (existing.isPresent()) {
            FileEntry file = existing.orElseThrow();
            if (file.kind() == FileEntry.Kind.DIRECTORY) throw new FileSystemException(target, null, "Is a folder");
            if (file.kind() != FileEntry.Kind.FILE) throw new FileSystemException(target, null, "Not a regular file");
            // The folder may allow the rename, but a read-only file is not replaced (as a write in place would fail).
            if (!endpoint.writable(target)) throw new AccessDeniedException(target);
            // Unknown permissions could not be copied onto the sibling, so replacing it would silently narrow the mode.
            if (file.permissions() < 0) return inPlace(endpoint, target, content);
        }
        String sibling = FilePaths.child(FilePaths.parent(target),
            "." + FilePaths.name(target) + ".jasper-" + Long.toHexString(ThreadLocalRandom.current().nextLong()) + ".tmp");
        WriteHandle out;
        try {
            // Owner-only while it holds the new content; the target's own permissions are copied just before the rename.
            out = endpoint.create(sibling, existing.isPresent() ? 0600 : 0666);
        } catch (AccessDeniedException denied) {
            if (existing.isEmpty()) throw denied;
            try {
                return inPlace(endpoint, target, content);
            } catch (IOException failed) {
                failed.addSuppressed(denied);
                throw failed;
            }
        }
        boolean sameOwner = true;
        try {
            if (existing.isPresent()) {
                FileEntry created = endpoint.stat(sibling);
                sameOwner = sameOwner(created, existing.orElseThrow());
                // A server that ignored the create's mode would let others read the new content as it is written.
                if (sameOwner && created.permissions() >= 0 && (created.permissions() & 077) != 0) endpoint.metadata(sibling, -1, 0600);
            }
        } catch (IOException | RuntimeException failure) {
            try { out.close(); } catch (IOException | RuntimeException suppressed) { failure.addSuppressed(suppressed); }
            remove(endpoint, sibling, failure);
            throw failure;
        }
        if (!sameOwner) {
            try {
                out.close();
            } catch (IOException | RuntimeException failure) {
                remove(endpoint, sibling, failure);
                throw failure;
            }
            discard(endpoint, sibling);
            return inPlace(endpoint, target, content);
        }
        boolean atomicUnavailable = false;
        try {
            try (out) {
                out.write(content);
                out.checkpoint();
            }
            int permissions = existing.map(FileEntry::permissions).orElse(-1);
            if (permissions >= 0) endpoint.metadata(sibling, -1, permissions);
            try {
                endpoint.publish(sibling, target, existing.isPresent());
            } catch (AtomicMoveNotSupportedException unavailable) {
                atomicUnavailable = true;
            }
        } catch (IOException | RuntimeException failure) {
            remove(endpoint, sibling, failure);
            throw failure;
        }
        if (atomicUnavailable) {
            discard(endpoint, sibling);
            return inPlace(endpoint, target, content);
        }
        return endpoint.stat(target);
    }

    /** Resolves {@code path} one component at a time, so no stat ever passes through an unresolved link. */
    private static Resolved walk(FileEndpoint endpoint, String path) throws IOException {
        var pending = new ArrayDeque<String>();
        push(pending, path);
        String resolved = "/";
        FileEntry entry = null;
        int links = 0;
        while (!pending.isEmpty()) {
            String part = pending.poll();
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                resolved = FilePaths.parent(resolved);
                entry = null;
                continue;
            }
            String next = FilePaths.child(resolved, part);
            try {
                entry = endpoint.stat(next);
            } catch (NoSuchFileException missing) {
                var rest = new StringBuilder(next);
                for (String left : pending) rest.append('/').append(left);
                return new Resolved(FilePaths.absolute(rest.toString()), Optional.empty());
            }
            if (entry.kind() == FileEntry.Kind.LINK) {
                if (++links > MAX_LINKS) throw new FileSystemException(path, null, "Too many levels of symbolic links");
                String target = entry.linkTarget();
                if (target.startsWith("/")) resolved = "/";
                push(pending, target);
                entry = null;
                continue;
            }
            if (!pending.isEmpty() && entry.kind() != FileEntry.Kind.DIRECTORY) throw new FileSystemException(next, null, "Not a folder");
            resolved = next;
        }
        return new Resolved(resolved, Optional.of(entry != null ? entry : endpoint.stat(resolved)));
    }

    /** Puts the components of {@code path} in front of {@code pending}, in order. */
    private static void push(ArrayDeque<String> pending, String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) pending.push(parts[i]);
    }

    /** Keeps the owner, group, hard links and extended attributes; not atomic. */
    private static FileEntry inPlace(FileEndpoint endpoint, String target, byte[] content) throws IOException {
        try (var out = endpoint.write(target, 0, false)) {
            out.write(content);
            out.checkpoint();
        }
        endpoint.truncate(target, content.length);
        return endpoint.stat(target);
    }

    /** Unknown ids (-1) skip the check, as the local write does where the platform reports none. */
    private static boolean sameOwner(FileEntry sibling, FileEntry target) {
        if (sibling.uid() < 0 || target.uid() < 0 || sibling.gid() < 0 || target.gid() < 0) return true;
        return sibling.uid() == target.uid() && sibling.gid() == target.gid();
    }

    /** Removes the sibling after {@code failure}; a sibling it cannot remove is marked on the failure. */
    private static void remove(FileEndpoint endpoint, String sibling, Exception failure) {
        try {
            endpoint.remove(sibling, false);
        } catch (IOException | RuntimeException left) {
            failure.addSuppressed(new SiblingLeft(sibling, left));
        }
    }

    /** Removes the sibling before writing in place; a sibling it cannot remove fails the save, marked. */
    private static void discard(FileEndpoint endpoint, String sibling) throws IOException {
        try {
            endpoint.remove(sibling, false);
        } catch (IOException | RuntimeException left) {
            // The removal failure is what the save throws, so its marker has no cause: the chain would loop.
            left.addSuppressed(new SiblingLeft(sibling, null));
            throw left;
        }
    }
}
