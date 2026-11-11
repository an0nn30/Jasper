package dev.jasper.browser.tools;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The hosts Save Page As never fetches from for a public page (plan 7's F12 and ruling D43), in the spirit of
 * Chromium's Private Network Access, which keeps a public page's requests from non-public addresses, and wider than it:
 * this computer ({@code localhost} and the names under it, {@code 127.0.0.0/8}, {@code ::1}), a link
 * ({@code 169.254.0.0/16}, {@code fe80::/10}), a private network ({@code 10.0.0.0/8}, {@code 172.16.0.0/12},
 * {@code 192.168.0.0/16}, {@code 100.64.0.0/10}, {@code fc00::/7}, {@code fec0::/10}), benchmarking's
 * {@code 198.18.0.0/15}, NAT64's local-use {@code 64:ff9b:1::/48}, the broadcast address {@code 255.255.255.255} and an
 * unspecified address ({@code 0.0.0.0/8}, {@code ::}), an IPv4 one also inside an IPv4-mapped, IPv4-compatible,
 * well-known NAT64 or 6to4 ({@code 2002::/16}) IPv6 address (ruling D44). A host is read
 * as an IP address in any spelling a resolver might take, the BSD forms such as {@code 0177.1} included, without
 * looking anything up. Pure.
 */
final class PrivateHosts {
    // What an IPv4 address in a BSD spelling is made of: digits, hex digits, x and dots.
    private static final Pattern POSIX = Pattern.compile("[0-9a-fx.]+");
    // NAT64's well-known prefix, 64:ff9b::/96, and the first 48 bits of its local-use one, 64:ff9b:1::/48.
    private static final byte[] NAT64 = {0, 0x64, (byte) 0xFF, (byte) 0x9B, 0, 0, 0, 0, 0, 0, 0, 0},
        LOCAL_NAT64 = {0, 0x64, (byte) 0xFF, (byte) 0x9B, 0, 1},
        BROADCAST = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    private PrivateHosts() { }

    /**
     * Whether {@code host}, as an address writes it, is refused by its spelling: {@code localhost}, a name under it, or
     * an IP address of a refused range.
     *
     * @param host the host, an IPv6 address in brackets or not, in any case, with or without a final dot
     * @return whether it is
     */
    static boolean refused(String host) {
        String name = bare(host);
        if (name.equals("localhost") || name.endsWith(".localhost")) return true;
        return literals(name).stream().anyMatch(PrivateHosts::refused);
    }

    /**
     * Whether {@code host} is written as an IP address, in any spelling {@link #refused(String)} reads, so no name
     * server is asked for it.
     *
     * @param host the host
     * @return whether it is
     */
    static boolean literal(String host) {
        return !literals(bare(host)).isEmpty();
    }

    /**
     * Whether {@code address} is in a refused range.
     *
     * @param address an address
     * @return whether it is
     */
    static boolean refused(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress())
            return true;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF, second = bytes[1] & 0xFF;
            return first == 0 || first == 10 || first == 127 || first == 100 && (second & 0xC0) == 64 || first == 169 && second == 254
                || first == 172 && (second & 0xF0) == 16 || first == 192 && second == 168 || first == 198 && (second & 0xFE) == 18
                || Arrays.equals(bytes, BROADCAST);
        }
        if ((bytes[0] & 0xFE) == 0xFC || Arrays.equals(bytes, 0, 6, LOCAL_NAT64, 0, 6)) return true;
        boolean compatible = Arrays.equals(bytes, 0, 12, new byte[12], 0, 12), nat64 = Arrays.equals(bytes, 0, 12, NAT64, 0, 12);
        boolean sixToFour = (bytes[0] & 0xFF) == 0x20 && (bytes[1] & 0xFF) == 0x02;
        if (compatible || nat64 || sixToFour) {
            try {
                return refused(InetAddress.getByAddress(sixToFour ? Arrays.copyOfRange(bytes, 2, 6) : Arrays.copyOfRange(bytes, 12, 16)));
            } catch (UnknownHostException impossible) {
                return true;
            }
        }
        return false;
    }

    /** {@code host} in lower case, without brackets, a zone or a final dot. */
    private static String bare(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        if (name.startsWith("[") && name.endsWith("]")) name = name.substring(1, name.length() - 1);
        int zone = name.indexOf('%');
        if (zone >= 0 && name.indexOf(':') >= 0) name = name.substring(0, zone);
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }

    /** The addresses {@code name} spells: as Java reads an IP address, and as a BSD resolver reads an IPv4 one. */
    private static List<InetAddress> literals(String name) {
        var found = new ArrayList<InetAddress>(2);
        if (name.isEmpty()) return found;
        try {
            found.add(InetAddress.ofLiteral(name));
        } catch (IllegalArgumentException notOne) {
            // No address in Java's spelling.
        }
        if (POSIX.matcher(name).matches()) {
            try {
                found.add(Inet4Address.ofPosixLiteral(name));
            } catch (IllegalArgumentException notOne) {
                // No address in a BSD spelling.
            }
        }
        if (found.isEmpty() && name.indexOf(':') >= 0) {
            try {
                found.add(Inet6Address.ofLiteral(name));
            } catch (IllegalArgumentException notOne) {
                // No IPv6 address.
            }
        }
        return found;
    }
}
