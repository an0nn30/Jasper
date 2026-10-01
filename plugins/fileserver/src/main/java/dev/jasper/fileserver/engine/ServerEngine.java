package dev.jasper.fileserver.engine;

import java.net.URI;
import java.util.List;

/** One running protocol server. The registry builds it, then starts and stops it on a worker thread. */
public interface ServerEngine {
    /** Binds and starts listening; returns once clients can connect. */
    void start() throws EngineFailure;

    /** Stops listening and drops every client. Idempotent; returns within about a second. */
    void stop();

    /** The URLs clients use, recomputed from the current network interfaces; empty while stopped. */
    List<URI> addresses();
}
