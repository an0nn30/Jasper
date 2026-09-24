package dev.jasper.remote.transfer;

import dev.jasper.remote.client.ConnectionIdentity;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable endpoint reference, never a credential or a live session. */
public record EndpointRef(Optional<UUID> hostId, String snapshot, String effectiveUsername) {
    public EndpointRef {
        Objects.requireNonNull(hostId); Objects.requireNonNull(snapshot); Objects.requireNonNull(effectiveUsername);
        if (hostId.isEmpty() && (!snapshot.isEmpty() || !effectiveUsername.isEmpty())) throw new IllegalArgumentException("Invalid local endpoint");
        if (hostId.isPresent() && (snapshot.isEmpty() || effectiveUsername.isBlank())) throw new IllegalArgumentException("Unresolved remote endpoint");
    }
    public static EndpointRef local() { return new EndpointRef(Optional.empty(), "", ""); }
    public static EndpointRef remote(ConnectionIdentity identity) throws IOException {
        if (!identity.resolved()) throw new IllegalArgumentException("Resolve endpoint accounts before queuing a transfer");
        return new EndpointRef(Optional.of(identity.host().id()), TransferCodec.identity(identity), identity.username());
    }
    public Optional<ConnectionIdentity> identity() throws IOException {
        if (hostId.isEmpty()) return Optional.empty();
        var identity = TransferCodec.identity(snapshot);
        if (!identity.host().id().equals(hostId.orElseThrow()) || !identity.username().equals(effectiveUsername) || !identity.resolved())
            throw new IOException("Inconsistent endpoint snapshot");
        return Optional.of(identity);
    }
    public String label() throws IOException { return identity().map(i -> i.username() + "@" + i.host().hostname()).orElse("Local"); }
}
