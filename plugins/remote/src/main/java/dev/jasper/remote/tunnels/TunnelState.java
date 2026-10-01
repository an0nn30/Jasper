package dev.jasper.remote.tunnels;

import java.time.Instant;
import java.util.Objects;

/** Where a tunnel is. Plan 7b-3 adds Reconnecting and WaitingForVault. */
public sealed interface TunnelState {
    /** Not running. */
    record Stopped() implements TunnelState { }

    /** Binding, connecting, verifying the host key, authenticating or asking the server for its port; {@code stage} says which. */
    record Starting(String stage) implements TunnelState {
        public Starting { Objects.requireNonNull(stage, "stage"); }
    }

    /** Forwarding since {@code since} on {@code boundPort}: this computer's port, or the server's for a remote tunnel. */
    record Up(Instant since, int boundPort, Counters counters) implements TunnelState {
        public Up { Objects.requireNonNull(since, "since"); Objects.requireNonNull(counters, "counters"); }
    }

    /** Stopped by a failure it will not retry; {@code reason} is already in the user's words. */
    record Failed(String reason) implements TunnelState {
        public Failed { Objects.requireNonNull(reason, "reason"); }
    }

    TunnelState STOPPED = new Stopped();

    /** Whether it holds a listener: Starting or Up. */
    default boolean running() { return this instanceof Starting || this instanceof Up; }
}
