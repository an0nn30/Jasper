package dev.jasper.sdk.files;

import java.util.Objects;

/**
 * One entry of a folder a {@link FileBrowser} lists.
 *
 * @param name           the entry's own name, not a path; untrusted text
 * @param kind           what it is; a link reports what it points to when the browser can tell
 * @param size           bytes, or -1 for a folder or when unknown
 * @param modifiedMillis epoch milliseconds, or -1 when unknown
 * @since 0.10.0
 */
public record FileEntry(String name, Kind kind, long size, long modifiedMillis) {
    /** What an entry is. */
    public enum Kind {
        /** A folder, or a link to one. */
        DIRECTORY,
        /** A regular file, or a link to one. */
        FILE,
        /** Anything else, including a dangling link. */
        OTHER
    }

    /** Rejects a missing kind and an empty name or one containing {@code /}. */
    public FileEntry {
        Objects.requireNonNull(kind, "kind");
        if (name == null || name.isEmpty() || name.contains("/")) throw new IllegalArgumentException("An entry needs a name without /: " + name);
    }
}
