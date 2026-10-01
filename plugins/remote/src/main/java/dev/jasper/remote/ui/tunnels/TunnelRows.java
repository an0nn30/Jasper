package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/** The words a tunnel shows in the panel, its tooltip, the palette and the status bar. Pure, so tests read them. */
public final class TunnelRows {
    private TunnelRows() { }

    /** The port it listens on now: the bound one while up, else the saved one, where 0 reads "any port". */
    static String port(Tunnel tunnel, TunnelState state) {
        if (state instanceof TunnelState.Up up) return String.valueOf(up.boundPort());
        return tunnel.bindPort() == 0 ? "any port" : String.valueOf(tunnel.bindPort());
    }

    /** The saved name, or a quick forward's listen address. */
    public static String title(Tunnel tunnel, TunnelState state) {
        return tunnel.quick() ? tunnel.bindAddress() + ":" + port(tunnel, state) : tunnel.name();
    }

    /** "localhost:5432 → db.internal:5432", "localhost:1080 · SOCKS5" or "server localhost:8080 → localhost:8080". */
    public static String mapping(Tunnel tunnel, TunnelState state) {
        String listen = tunnel.bindAddress() + ":" + port(tunnel, state);
        String target = tunnel.targetHost() + ":" + tunnel.targetPort();
        return switch (tunnel.kind()) {
            case LOCAL -> listen + " → " + target;
            case DYNAMIC -> listen + " · SOCKS5";
            case REMOTE -> "server " + listen + " → " + target;
        };
    }

    /** The row's last line: the host and what is happening, or that the host is gone. */
    public static String detail(Tunnel tunnel, TunnelState state, Optional<String> hostName, Instant now) {
        if (hostName.isEmpty()) return "Host missing — edit to choose another";
        String via = "via " + hostName.get();
        String line = switch (state) {
            case TunnelState.Stopped stopped -> via;
            case TunnelState.Starting starting -> via + " · " + starting.stage();
            case TunnelState.Failed failed -> via + " · " + failed.reason();
            case TunnelState.Up up -> up.counters().warning().map(warning -> via + " · " + warning).orElseGet(() ->
                via + " · " + connections(up.counters().open()) + " · ↓" + bytes(up.counters().received()) + " ↑" + bytes(up.counters().sent())
                    + " · " + duration(Duration.between(up.since(), now)));
        };
        return tunnel.quick() ? line + " · unsaved" : line;
    }

    /** One word or phrase for the palette and the status tooltip. */
    public static String stateText(TunnelState state) {
        return switch (state) {
            case TunnelState.Stopped stopped -> "stopped";
            case TunnelState.Starting starting -> "starting";
            case TunnelState.Up up -> "up";
            case TunnelState.Failed failed -> "failed: " + failed.reason();
        };
    }

    /** The status item: "2 tunnels", "2 tunnels · 1 failing" or "1 tunnel failing". */
    public static String status(int active, int failing) {
        String running = active == 1 ? "1 tunnel" : active + " tunnels";
        if (failing == 0) return running;
        if (active == 0) return failing == 1 ? "1 tunnel failing" : failing + " tunnels failing";
        return running + " · " + failing + " failing";
    }

    /**
     * What Copy Address copies; empty while the port is not known. A remote tunnel open to everyone is reached at
     * {@code serverHostname}.
     */
    public static Optional<String> address(Tunnel tunnel, TunnelState state, Optional<String> serverHostname) {
        int port = state instanceof TunnelState.Up up ? up.boundPort() : tunnel.bindPort();
        if (port == 0) return Optional.empty();
        String host = tunnel.kind() == TunnelKind.REMOTE && tunnel.bindAddress().equals(Tunnel.ALL)
            ? serverHostname.orElse("server") : hostPart(tunnel.bindAddress());
        return Optional.of(host + ":" + port);
    }

    /** What Open in Browser opens: {@code http://localhost:5432/} for a running local tunnel; empty otherwise. */
    public static Optional<URI> browseUri(Tunnel tunnel, TunnelState state) {
        if (tunnel.kind() != TunnelKind.LOCAL || !(state instanceof TunnelState.Up up)) return Optional.empty();
        return Optional.of(URI.create("http://" + hostPart(tunnel.bindAddress()) + ":" + up.boundPort() + "/"));
    }

    /** Whether {@code query} finds the tunnel in its name, host, addresses, ports or kind. */
    public static boolean matches(Tunnel tunnel, String hostName, String query) {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) return true;
        String haystack = String.join(" ", tunnel.name(), hostName, tunnel.bindAddress() + ":" + tunnel.bindPort(),
            tunnel.targetHost() + ":" + tunnel.targetPort(), tunnel.kind().key()).toLowerCase(Locale.ROOT);
        return haystack.contains(wanted);
    }

    static String connections(int open) { return open == 1 ? "1 conn" : open + " conns"; }

    static String bytes(long count) {
        if (count < 1024) return count + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = count;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return (value < 10 ? String.format(Locale.ROOT, "%.1f", value) : String.valueOf(Math.round(value))) + " " + units[unit];
    }

    static String duration(Duration elapsed) {
        long seconds = Math.max(0, elapsed.toSeconds());
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return seconds / 60 + " min";
        if (seconds < 86_400) return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
        return seconds / 86_400 + "d " + seconds % 86_400 / 3600 + "h";
    }

    /** {@code *} and {@code localhost} are reached as localhost; an IPv6 literal goes in brackets. */
    private static String hostPart(String bindAddress) {
        if (bindAddress.equals(Tunnel.ALL) || bindAddress.equals(Tunnel.LOCALHOST)) return "localhost";
        return bindAddress.contains(":") ? "[" + bindAddress + "]" : bindAddress;
    }
}
