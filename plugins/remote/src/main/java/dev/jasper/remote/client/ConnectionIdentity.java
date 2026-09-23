package dev.jasper.remote.client;

import dev.jasper.remote.hosts.RemoteHost;
import java.util.List;
import java.util.Objects;

/** Nonsecret, immutable target-first route. Cosmetic saved-host changes do not change identity. */
public record ConnectionIdentity(List<Hop> hops) {
    public ConnectionIdentity {
        hops = List.copyOf(hops);
        if (hops.isEmpty()) throw new IllegalArgumentException("An endpoint needs a host");
    }
    public RemoteHost host() { return hops.getFirst().host(); }
    public String username() { return hops.getFirst().username(); }
    public boolean resolved() { return hops.stream().allMatch(h -> !h.username().isBlank()); }
    ConnectionIdentity parent() { return hops.size() == 1 ? null : new ConnectionIdentity(hops.subList(1, hops.size())); }

    public record Hop(RemoteHost host, String username) {
        public Hop { Objects.requireNonNull(host); Objects.requireNonNull(username); }
        @Override public boolean equals(Object value) {
            return value instanceof Hop other && host.id().equals(other.host.id())
                && host.hostname().equals(other.host.hostname()) && host.port() == other.host.port()
                && host.username().equals(other.host.username()) && host.auth().equals(other.host.auth())
                && username.equals(other.username);
        }
        @Override public int hashCode() {
            return Objects.hash(host.id(), host.hostname(), host.port(), host.username(), host.auth(), username);
        }
    }
}
