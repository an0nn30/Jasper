package dev.jasper.remote.sftp;

import java.util.Objects;

/** A no-follow directory entry; unavailable metadata uses -1 and unavailable identity uses empty text. */
public record FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions,
                        String linkTarget, String fileKey) {
    public enum Kind { FILE, DIRECTORY, LINK, SPECIAL }
    public FileEntry {
        Objects.requireNonNull(name); Objects.requireNonNull(kind); Objects.requireNonNull(fileKey);
        linkTarget = linkTarget == null ? "" : linkTarget;
    }
    public FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions, String linkTarget) {
        this(name, kind, size, modifiedMillis, permissions, linkTarget, "");
    }
}
