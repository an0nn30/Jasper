package dev.jasper.app.files;

import java.util.Objects;

/**
 * One listed file or folder. Links are followed where the browser can tell, so a link to a folder is a folder.
 *
 * @param path absolute path in its root's syntax
 * @param name the last path element, untrusted text
 * @param kind folder, file or anything else (including entries whose details cannot be read)
 * @param size bytes, or -1 for folders and unknown sizes
 * @param modified epoch milliseconds, or -1 when unknown
 * @param hidden a dotfile, or hidden by the platform
 */
public record ChooserEntry(String path, String name, Kind kind, long size, long modified, boolean hidden) {
    /** What an entry is. */
    public enum Kind { DIRECTORY, FILE, OTHER }

    public ChooserEntry {
        Objects.requireNonNull(path); Objects.requireNonNull(name); Objects.requireNonNull(kind);
    }

    public boolean directory() { return kind == Kind.DIRECTORY; }
}
