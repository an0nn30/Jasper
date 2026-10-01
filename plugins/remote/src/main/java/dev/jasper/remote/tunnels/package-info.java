/**
 * SSH tunnels without Swing: the model, tunnels.toml, and the engine that binds listeners, leases the host's shared session
 * from {@code client.Connections} and forwards each connection over its own channel. {@link dev.jasper.remote.tunnels.TunnelManager}
 * is UI-thread-only; connection threads touch only a run's session, its counters and its tracked ends.
 */
package dev.jasper.remote.tunnels;
