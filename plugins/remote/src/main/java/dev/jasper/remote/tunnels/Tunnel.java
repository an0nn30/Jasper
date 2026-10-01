package dev.jasper.remote.tunnels;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A tunnel: a saved one from tunnels.toml, or a quick forward that lives in memory until it is saved (a quick forward
 * has an empty name). It goes through a saved host, which holds the credential, the host-key trust and the jump chain.
 * A dynamic tunnel has no target.
 */
public record Tunnel(UUID id, String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort,
                     String targetHost, int targetPort, TunnelStart start, Instant created, Instant updated) {
    /** Listen on every address. */
    public static final String ALL = "*";
    /** Listen on the loopback addresses only, the default. */
    public static final String LOCALHOST = "localhost";

    public Tunnel {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(hostId, "hostId"); Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(start, "start"); Objects.requireNonNull(created, "created"); Objects.requireNonNull(updated, "updated");
        name = name == null ? "" : name.strip();
        bindAddress = bindAddress == null ? "" : bindAddress.strip();
        if (!validBindAddress(bindAddress)) throw new IllegalArgumentException("Listen on localhost, * or an IP address");
        if (bindPort < 0 || bindPort > 65535) throw new IllegalArgumentException("The listen port must be 0 to 65535");
        if (kind == TunnelKind.DYNAMIC) { targetHost = ""; targetPort = 0; }
        else {
            targetHost = targetHost == null ? "" : targetHost.strip();
            if (targetHost.isEmpty()) throw new IllegalArgumentException("A tunnel needs a host to forward to");
            if (targetPort < 1 || targetPort > 65535) throw new IllegalArgumentException("The target port must be 1 to 65535");
        }
    }

    /** A new manual tunnel; an empty name makes it a quick forward. */
    public static Tunnel create(String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort, String targetHost, int targetPort) {
        Instant now = Instant.now();
        return new Tunnel(UUID.randomUUID(), name, hostId, kind, bindAddress, bindPort, targetHost, targetPort, TunnelStart.MANUAL, now, now);
    }

    /** The same tunnel (id, start option, creation time) with new settings. */
    public Tunnel withEdited(String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort, String targetHost, int targetPort) {
        return new Tunnel(id, name, hostId, kind, bindAddress, bindPort, targetHost, targetPort, start, created, Instant.now());
    }

    /** A quick forward: unsaved, shown by its listen address. */
    public boolean quick() { return name.isEmpty(); }

    /** Whether the two forward the same way, which is what a running tunnel must restart for. */
    public boolean sameForwarding(Tunnel other) {
        return hostId.equals(other.hostId) && kind == other.kind && bindAddress.equals(other.bindAddress) && bindPort == other.bindPort
            && targetHost.equals(other.targetHost) && targetPort == other.targetPort;
    }

    /** {@code localhost}, {@code *} or an IP literal; a host name is refused so binding never waits on DNS. */
    public static boolean validBindAddress(String address) {
        if (address.equals(LOCALHOST) || address.equals(ALL)) return true;
        try { InetAddress.ofLiteral(address); return true; }
        catch (IllegalArgumentException notALiteral) { return false; }
    }
}
