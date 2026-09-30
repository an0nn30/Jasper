package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A folder or file the chooser can show: which source and root, and an absolute path in that root's syntax. Local
 * places use the {@link #LOCAL} source and root; {@link #HOME} stands for a root's home until its browser is open.
 */
public record Location(String source, String root, String path) {
    /** The local disk's source and root id. */
    public static final String LOCAL = "local";
    /** A root's home folder, resolved once connected. */
    public static final String HOME = "~";

    public Location {
        Objects.requireNonNull(source); Objects.requireNonNull(root); Objects.requireNonNull(path);
        if (source.isBlank() || root.isBlank() || path.isEmpty()) throw new IllegalArgumentException("A location needs a source, a root and a path");
    }

    public static Location local(Path path) { return new Location(LOCAL, LOCAL, path.toAbsolutePath().normalize().toString()); }

    public static Location home(String source, String root) { return new Location(source, root, HOME); }

    public boolean isLocal() { return source.equals(LOCAL); }

    public Path localPath() {
        if (!isLocal()) throw new IllegalStateException("Not a local location: " + this);
        return Path.of(path);
    }

    /** The same root at another path. */
    public Location at(String other) { return new Location(source, root, other); }

    /** Stored form: a local path as it is, anything else as {@code @source|root|path}. */
    public String stored() { return isLocal() ? path : "@" + source + "|" + root + "|" + path; }

    /** Reads {@link #stored()}; empty for anything unreadable, such as a relative path. */
    public static Optional<Location> parse(String stored) {
        try {
            if (stored.startsWith("@")) {
                String[] parts = stored.substring(1).split("\\|", 3);
                return parts.length == 3 ? Optional.of(new Location(parts[0], parts[1], parts[2])) : Optional.empty();
            }
            Path path = Path.of(stored);
            return path.isAbsolute() ? Optional.of(local(path)) : Optional.empty();
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
