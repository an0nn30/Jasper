package dev.jasper.remote.sftp;

import java.util.Objects;

/**
 * A no-follow directory entry; unavailable metadata uses -1 and unavailable identity uses empty text.
 * {@code uid} and {@code gid} are the owner and group ids, for the editor's safe-write owner check.
 */
public record FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions,
                        String linkTarget, String fileKey, int uid, int gid) {
    public enum Kind { FILE, DIRECTORY, LINK, SPECIAL }
    public FileEntry {
        Objects.requireNonNull(name); Objects.requireNonNull(kind); Objects.requireNonNull(fileKey);
        linkTarget = linkTarget == null ? "" : linkTarget;
    }
    public FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions, String linkTarget, String fileKey) {
        this(name, kind, size, modifiedMillis, permissions, linkTarget, fileKey, -1, -1);
    }
    public FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions, String linkTarget) {
        this(name, kind, size, modifiedMillis, permissions, linkTarget, "", -1, -1);
    }
}
