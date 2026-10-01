package dev.jasper.fileserver.runtime;

import java.util.Objects;
import java.util.Optional;

/** Where a server is in its life, and why it failed. */
public record ServerState(Phase phase, Optional<String> reason) {
    public enum Phase {
        STOPPED, STARTING, RUNNING, STOPPING, FAILED, WAITING_FOR_VAULT;

        public boolean startable() { return this == STOPPED || this == FAILED || this == WAITING_FOR_VAULT; }
    }

    public static final ServerState STOPPED = new ServerState(Phase.STOPPED, Optional.empty());
    public static final ServerState STARTING = new ServerState(Phase.STARTING, Optional.empty());
    public static final ServerState RUNNING = new ServerState(Phase.RUNNING, Optional.empty());
    public static final ServerState STOPPING = new ServerState(Phase.STOPPING, Optional.empty());
    public static final ServerState WAITING_FOR_VAULT = new ServerState(Phase.WAITING_FOR_VAULT, Optional.empty());

    public ServerState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(reason, "reason");
    }

    public static ServerState failed(String reason) { return new ServerState(Phase.FAILED, Optional.of(reason)); }
}
