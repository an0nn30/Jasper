package dev.jasper.remote.tunnels;

import java.util.Locale;

/** Which way a tunnel forwards. */
public enum TunnelKind {
    /** {@code -L}: a port on this computer reaches a target through the server. */
    LOCAL,
    /** {@code -R}: a port on the server reaches a target through this computer. */
    REMOTE,
    /** {@code -D}: a SOCKS5 proxy on this computer; each connection names its own target, reached through the server. */
    DYNAMIC;

    /** The letter the panel shows: L, R or D. */
    public String letter() { return name().substring(0, 1); }

    /** The value in tunnels.toml. */
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
