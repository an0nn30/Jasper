package dev.jasper.remote.tunnels;

import java.util.Locale;

/** When a saved tunnel starts. Plan 7b-2 stores it; plan 7b-3 starts {@code LAUNCH} tunnels when Jasper starts. */
public enum TunnelStart {
    /** Only when you start it. */
    MANUAL,
    /** When Jasper starts, without prompting. */
    LAUNCH;

    /** The value in tunnels.toml. */
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
