package dev.jasper.app.launch;

/**
 * Whether a path's text names a UNC host. On Windows, touching {@code \\host\share} makes Windows resolve {@code host}
 * on the network (DNS, LLMNR, NBT-NS) and authenticate to it over SMB, so a path that program output names (a printed
 * path, a link, a directory a shell reported) must never reach a host. As with VS Code's {@code security.allowedUNCHosts},
 * no host is exempt by default: this machine's WSL shares are exempt only in a WSL profile's pane, where the user chose
 * WSL, because without WSL {@code wsl$} is an ordinary single-label name that an NBT-NS answer on the network can claim.
 * Pure text, so it holds on every OS; callers apply it on Windows only.
 */
public final class UncHosts {
    private UncHosts() { }

    /**
     * Whether {@code text} starts with two separators ({@code \} or {@code /}, in any mix) and then a host: any host, or
     * with {@code wslShares} any but the WSL shares' ({@code wsl.localhost} and {@code wsl$}, in any case). A WSL host
     * counts too when its share is {@code .} or {@code ..}, which Windows resolves to another host, and when it is not
     * all ASCII: {@link String#equalsIgnoreCase} folds some other characters to ASCII ones that Windows does not, and such
     * a name goes to name resolution on the network.
     */
    public static boolean namesForeignHost(String text, boolean wslShares) {
        if (text.length() < 2 || !separator(text.charAt(0)) || !separator(text.charAt(1))) return false;
        int hostEnd = endOfSegment(text, 2);
        if (!wslShares || !wslHost(text.substring(2, hostEnd))) return true;
        int share = hostEnd;
        while (share < text.length() && separator(text.charAt(share))) share++;
        String name = text.substring(share, endOfSegment(text, share));
        return name.equals(".") || name.equals("..");
    }

    private static boolean wslHost(String host) {
        return host.chars().allMatch(c -> c < 128) && (host.equalsIgnoreCase("wsl.localhost") || host.equalsIgnoreCase("wsl$"));
    }

    private static int endOfSegment(String text, int from) {
        int end = from;
        while (end < text.length() && !separator(text.charAt(end))) end++;
        return end;
    }

    private static boolean separator(char c) { return c == '\\' || c == '/'; }
}
