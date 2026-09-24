package dev.jasper.remote.client;

import dev.jasper.remote.hosts.RemoteHost;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.session.ClientSession;

/** One caller's reference to a shared transport. Close releases this reference, never another caller's channel. */
public final class SessionLease implements AutoCloseable {
    private final ConnectionIdentity identity;
    private final ClientSession session;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();
    SessionLease(ConnectionIdentity identity, ClientSession session, Runnable release) {
        this.identity = identity; this.session = session; this.release = release;
    }
    public ConnectionIdentity identity() { return identity; }
    public RemoteHost host() { return identity.host(); }
    public ClientSession session() { if (closed.get()) throw new IllegalStateException("Lease closed"); return session; }
    @Override public void close() { if (closed.compareAndSet(false, true)) release.run(); }
}
