package dev.jasper.editor.recent;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One recently opened file, copied from its document when it was opened.
 *
 * @param key the document key, which says who reopens it
 * @param name the file name
 * @param location the full location, for example {@code dustin@prod-web:/etc/nginx/nginx.conf}
 * @param host the host of a remote file; empty for a local one
 * @param opened when it was last opened
 */
public record RecentFile(String key, String name, String location, Optional<String> host, Instant opened) {
    /** Rejects nulls and a blank key or name. */
    public RecentFile {
        Objects.requireNonNull(location, "location"); Objects.requireNonNull(host, "host"); Objects.requireNonNull(opened, "opened");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("A recent file needs a key");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A recent file needs a name");
    }
}
