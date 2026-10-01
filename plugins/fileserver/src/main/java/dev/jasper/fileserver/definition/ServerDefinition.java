package dev.jasper.fileserver.definition;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One saved (or temporary) server. Construction validates every rule the spec states and throws
 * {@link IllegalArgumentException} with a message the editor can show.
 */
public record ServerDefinition(UUID id, String name, Protocol protocol, Path folder, Bind bind, int port,
                               Optional<PortRange> passiveRange, boolean writable, Login login, List<String> authorizedKeys,
                               boolean tls, boolean requireTls, boolean autostart) {
    public static final int MAX_NAME = 80;

    public ServerDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(passiveRange, "passiveRange");
        Objects.requireNonNull(login, "login");
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("A server needs a name");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("A server name is at most " + MAX_NAME + " characters");
        if (!folder.isAbsolute()) throw new IllegalArgumentException("The folder must be an absolute path");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("The port must be a number from 1 to 65535");
        if (passiveRange.isPresent() && protocol != Protocol.FTP) throw new IllegalArgumentException("Only FTP has a passive port range");
        authorizedKeys = List.copyOf(authorizedKeys);
        if (!authorizedKeys.isEmpty() && protocol != Protocol.SFTP) throw new IllegalArgumentException("Only SFTP has authorized keys");
        for (String key : authorizedKeys)
            if (key.isBlank() || key.contains("\n") || key.contains("\r")) throw new IllegalArgumentException("An authorized key is one non-blank line");
        if (tls && !protocol.allowsTls()) throw new IllegalArgumentException("Only HTTP and FTP use TLS");
        if (requireTls && protocol != Protocol.FTP) throw new IllegalArgumentException("Only FTP can require TLS");
        if (requireTls && !tls) throw new IllegalArgumentException("Require TLS needs Use TLS");
        if (protocol == Protocol.TFTP && !(login instanceof Login.Anonymous)) throw new IllegalArgumentException("TFTP has no login");
        if (protocol == Protocol.SFTP && login instanceof Login.Anonymous && authorizedKeys.isEmpty())
            throw new IllegalArgumentException("SFTP needs a password or authorized keys");
    }

    /** A new, read-only server on this computer only, at the protocol's default port. */
    public static ServerDefinition create(String name, Protocol protocol, Path folder) {
        Login login = protocol == Protocol.HTTP || protocol == Protocol.TFTP ? Login.ANONYMOUS : Login.SESSION;
        return new ServerDefinition(UUID.randomUUID(), name, protocol, folder, Bind.LOOPBACK, protocol.defaultPort(),
            Optional.empty(), false, login, List.of(), false, false, false);
    }

    /** The same settings under a new id and name; a copy never starts with Jasper. */
    public ServerDefinition copyAs(String newName) {
        return new ServerDefinition(UUID.randomUUID(), newName, protocol, folder, bind, port, passiveRange, writable, login,
            authorizedKeys, tls, requireTls, false);
    }
}
