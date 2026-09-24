package dev.jasper.remote.hosts;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A saved host: a reference to its credential, never the secret itself. {@code followDirectory} lets SFTP follow its shells. */
public record RemoteHost(UUID id, String name, String hostname, int port, String username, Auth auth, String group, boolean favorite,
                         Optional<UUID> jump, Instant created, Instant updated, boolean followDirectory) {
    public RemoteHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(auth, "auth"); Objects.requireNonNull(jump, "jump");
        Objects.requireNonNull(created, "created"); Objects.requireNonNull(updated, "updated");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A host needs a name");
        if (hostname == null || hostname.isBlank()) throw new IllegalArgumentException("A host needs a hostname");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("The port must be 1 to 65535");
        username = username == null ? "" : username.strip();
        if (username.isEmpty() && auth instanceof Auth.Agent) throw new IllegalArgumentException("An SSH agent host needs a username");
        group = group == null ? "" : group.strip();
        name = name.strip(); hostname = hostname.strip();
        if (jump.isPresent() && jump.get().equals(id)) throw new IllegalArgumentException("A host cannot jump through itself");
    }

    /** A host that follows shell folders, the default. */
    public RemoteHost(UUID id, String name, String hostname, int port, String username, Auth auth, String group, boolean favorite,
                      Optional<UUID> jump, Instant created, Instant updated) {
        this(id, name, hostname, port, username, auth, group, favorite, jump, created, updated, true);
    }

    public static RemoteHost create(String name, String hostname, int port, String username, Auth auth, String group, Optional<UUID> jump) {
        Instant now = Instant.now();
        return new RemoteHost(UUID.randomUUID(), name, hostname, port, username, auth, group, false, jump, now, now);
    }

    public RemoteHost withEdited(String name, String hostname, int port, String username, Auth auth, String group, Optional<UUID> jump) {
        return new RemoteHost(id, name, hostname, port, username, auth, group, favorite, jump, created, Instant.now(), followDirectory);
    }

    public RemoteHost withFavorite(boolean value) { return new RemoteHost(id, name, hostname, port, username, auth, group, value, jump, created, updated, followDirectory); }

    public RemoteHost withFollowDirectory(boolean value) { return new RemoteHost(id, name, hostname, port, username, auth, group, favorite, jump, created, updated, value); }

    /** {@code user@host:port}, or {@code host:port} when the username comes from the Vault login. */
    public String label() { return (username.isEmpty() ? "" : username + "@") + hostname + ":" + port; }
}
