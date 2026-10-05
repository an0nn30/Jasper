package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A project's identity: a local root folder, or a root folder on one of Remote's saved hosts.
 *
 * @param host the saved host's id; empty for a local project
 * @param root the root folder: a local path's text, or an absolute {@code /}-separated remote path
 */
public record ProjectKey(Optional<UUID> host, String root) {
    private static final String LOCAL = "local:";
    private static final String REMOTE = "remote:";
    private static final int UUID_LENGTH = 36;

    /** Rejects a blank root and a relative remote root. */
    public ProjectKey {
        Objects.requireNonNull(host, "host");
        if (root == null || root.isBlank()) throw new IllegalArgumentException("A project needs a root");
        if (host.isPresent() && !root.startsWith("/")) throw new IllegalArgumentException("A remote root is absolute: " + root);
    }

    /**
     * @param root a local folder
     * @return its key, with the path made absolute and normalized
     */
    public static ProjectKey local(Path root) { return new ProjectKey(Optional.empty(), root.toAbsolutePath().normalize().toString()); }

    /**
     * @param host a saved host's id
     * @param root an absolute folder on it
     * @return its key
     */
    public static ProjectKey remote(UUID host, String root) { return new ProjectKey(Optional.of(host), root); }

    /** @return whether the project is on this machine */
    public boolean isLocal() { return host.isEmpty(); }

    /** @return the local root; throws for a remote project */
    public Path path() {
        if (!isLocal()) throw new IllegalStateException("Not a local project: " + id());
        return Path.of(root);
    }

    /** @return the root folder's own name */
    public String name() {
        if (isLocal()) {
            Path name = path().getFileName();
            return name == null ? root : name.toString();
        }
        String trimmed = root.length() > 1 && root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        String last = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return last.isEmpty() ? trimmed : last;
    }

    /** @return {@code local:<root>} or {@code remote:<host id>:<root>} */
    public String id() { return host.map(id -> REMOTE + id + ":" + root).orElse(LOCAL + root); }

    /**
     * @param id what {@link #id()} returned
     * @return the key, or empty when the text is not one
     */
    public static Optional<ProjectKey> parse(String id) {
        try {
            if (id.startsWith(LOCAL)) return Optional.of(new ProjectKey(Optional.empty(), id.substring(LOCAL.length())));
            if (id.startsWith(REMOTE) && id.length() > REMOTE.length() + UUID_LENGTH + 1 && id.charAt(REMOTE.length() + UUID_LENGTH) == ':') {
                UUID host = UUID.fromString(id.substring(REMOTE.length(), REMOTE.length() + UUID_LENGTH));
                return Optional.of(new ProjectKey(Optional.of(host), id.substring(REMOTE.length() + UUID_LENGTH + 1)));
            }
        } catch (IllegalArgumentException malformed) {
            // not a key
        }
        return Optional.empty();
    }

    /**
     * @param folder a local folder
     * @return whether it is this local project's root or inside it
     */
    public boolean contains(Path folder) {
        return isLocal() && folder.toAbsolutePath().normalize().startsWith(path());
    }

    /**
     * @param onHost a saved host's id
     * @param folder an absolute folder on that host
     * @return whether it is this remote project's root or inside it
     */
    public boolean contains(UUID onHost, String folder) {
        if (!host.equals(Optional.of(onHost))) return false;
        String prefix = root.endsWith("/") ? root : root + "/";
        return folder.equals(root) || folder.startsWith(prefix);
    }
}
