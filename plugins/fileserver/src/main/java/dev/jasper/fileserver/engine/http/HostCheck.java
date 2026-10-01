package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.definition.Bind;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses requests whose Host header names some other site's DNS name, which is how a web page reaches a local
 * server through DNS rebinding. IP literals, {@code localhost} and this computer's own names pass; a loopback server
 * accepts loopback names only.
 */
final class HostCheck {
    private final Set<String> names;
    private final boolean loopbackOnly;

    HostCheck(Set<String> names, boolean loopbackOnly) { this.names = Set.copyOf(names); this.loopbackOnly = loopbackOnly; }

    /** Looks up this computer's names, which may be slow: call it off the UI thread. */
    static HostCheck forBind(Bind bind) {
        var names = new HashSet<String>(Set.of("localhost"));
        if (!(bind instanceof Bind.Loopback)) {
            try {
                String host = InetAddress.getLocalHost().getHostName().toLowerCase(Locale.ROOT);
                int dot = host.indexOf('.');
                String shortName = dot < 0 ? host : host.substring(0, dot);
                names.add(host);
                names.add(shortName);
                names.add(shortName + ".local");
            } catch (IOException unknown) { /* addresses and localhost still work */ }
        }
        return new HostCheck(names, bind instanceof Bind.Loopback);
    }

    boolean allows(String header) {
        if (header == null || header.isBlank()) return false;
        String host = hostPart(header.strip()).toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) return !loopbackOnly || host.equals("[::1]");
        if (ipv4(host)) return !loopbackOnly || host.startsWith("127.");
        return names.contains(host);
    }

    private static String hostPart(String header) {
        if (header.startsWith("[")) {
            int close = header.indexOf(']');
            return close < 0 ? header : header.substring(0, close + 1);
        }
        int colon = header.lastIndexOf(':');
        return colon < 0 ? header : header.substring(0, colon);
    }

    private static boolean ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) if (part.charAt(i) < '0' || part.charAt(i) > '9') return false;
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
}
