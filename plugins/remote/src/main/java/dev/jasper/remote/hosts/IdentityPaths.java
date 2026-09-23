package dev.jasper.remote.hosts;

import java.nio.file.Path;

/** Single-pass, non-executing expansion of supported IdentityFile tokens. Never reads a key. */
public final class IdentityPaths {
    private IdentityPaths() {}
    public static Path resolve(String expression, Path home, String localUser, String host, String remoteUser, int port) {
        if (expression.isBlank() || expression.contains("${")) throw new IllegalArgumentException("Unsupported IdentityFile expression");
        var out = new StringBuilder();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c != '%') { out.append(c); continue; }
            if (++i == expression.length()) throw new IllegalArgumentException("Incomplete IdentityFile token");
            out.append(switch (expression.charAt(i)) {
                case '%' -> "%"; case 'd' -> home.toString(); case 'u' -> localUser;
                case 'r' -> remoteUser; case 'h' -> host; case 'p' -> Integer.toString(port);
                default -> throw new IllegalArgumentException("Unsupported IdentityFile token");
            });
        }
        String expanded = out.toString();
        if (expanded.equals("~")) return home.toAbsolutePath().normalize();
        if (expanded.startsWith("~/")) expanded = home.resolve(expanded.substring(2)).toString();
        else if (expanded.startsWith("~")) throw new IllegalArgumentException("Unsupported IdentityFile tilde username");
        if (expanded.isBlank()) throw new IllegalArgumentException("Empty IdentityFile path");
        Path path = Path.of(expanded);
        return (path.isAbsolute() ? path : home.resolve(path)).toAbsolutePath().normalize();
    }
}
