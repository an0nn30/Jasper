package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Protocol;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/** The address a server binds and the URLs it advertises, computed from a snapshot of the network interfaces. */
public final class ServerAddresses {
    /** One network interface as File Server sees it. */
    public record Nic(String name, boolean up, boolean loopback, boolean virtual, List<InetAddress> addresses) {
        public Nic { addresses = List.copyOf(addresses); }

        /** The name and preferred address, for example "en0 (192.168.1.20)". */
        public String label() { return addresses.isEmpty() ? name : name + " (" + hostText(preferred(this)) + ")"; }
    }

    private ServerAddresses() { }

    /** This computer's interfaces now; empty when the system will not say. */
    public static List<Nic> system() {
        var nics = new ArrayList<Nic>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return List.of();
            for (NetworkInterface nic : Collections.list(all)) {
                try { nics.add(new Nic(nic.getName(), nic.isUp(), nic.isLoopback(), nic.isVirtual(), Collections.list(nic.getInetAddresses()))); }
                catch (SocketException vanished) { /* gone while listing */ }
            }
        } catch (SocketException unavailable) {
            return List.of();
        }
        return List.copyOf(nics);
    }

    /** The local address to bind: loopback, the wildcard, or the interface's first IPv4 address. */
    public static InetAddress bindAddress(Bind bind, List<Nic> nics) throws EngineFailure {
        return switch (bind) {
            case Bind.Loopback loopback -> InetAddress.getLoopbackAddress();
            case Bind.All all -> new InetSocketAddress(0).getAddress();
            case Bind.Interface named -> nics.stream()
                .filter(nic -> nic.name().equals(named.name()) && nic.up() && !nic.addresses().isEmpty()).findFirst()
                .map(ServerAddresses::preferred)
                .orElseThrow(() -> new EngineFailure("Network interface " + named.name() + " is not available"));
        };
    }

    /** The addresses other programs reach a server on: IPv4 first, then global IPv6; loopback when nothing else is up. */
    public static List<InetAddress> reachable(Bind bind, List<Nic> nics) {
        return switch (bind) {
            case Bind.Loopback loopback -> List.of(InetAddress.getLoopbackAddress());
            case Bind.Interface named -> nics.stream().filter(nic -> nic.name().equals(named.name()) && !nic.addresses().isEmpty())
                .findFirst().map(nic -> List.of(preferred(nic))).orElse(List.of());
            case Bind.All all -> {
                List<InetAddress> found = nics.stream().filter(nic -> nic.up() && !nic.loopback() && !nic.virtual())
                    .flatMap(nic -> nic.addresses().stream()).filter(ServerAddresses::shareable)
                    .sorted(Comparator.comparing((InetAddress address) -> !(address instanceof Inet4Address)))
                    .distinct().toList();
                yield found.isEmpty() ? List.of(InetAddress.getLoopbackAddress()) : found;
            }
        };
    }

    /** One URL per address; SFTP URLs name the user. */
    public static List<URI> urls(Protocol protocol, boolean tls, List<InetAddress> addresses, int port, Optional<String> user) {
        String scheme = protocol == Protocol.HTTP && tls ? "https" : protocol.scheme();
        String who = user.filter(name -> protocol == Protocol.SFTP)
            .map(name -> URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20") + "@").orElse("");
        var urls = new ArrayList<URI>();
        for (InetAddress address : addresses) {
            String host = address instanceof Inet6Address ? "[" + hostText(address) + "]" : hostText(address);
            urls.add(URI.create(scheme + "://" + who + host + ":" + port + "/"));
        }
        return List.copyOf(urls);
    }

    /** How a bound address reads in a message: 0.0.0.0 for every address. */
    public static String describe(InetAddress address) {
        return address.isAnyLocalAddress() ? "0.0.0.0" : hostText(address);
    }

    /** The address as text, without an IPv6 scope. */
    public static String hostText(InetAddress address) {
        String text = address.getHostAddress();
        int scope = text.indexOf('%');
        return scope < 0 ? text : text.substring(0, scope);
    }

    private static InetAddress preferred(Nic nic) {
        return nic.addresses().stream().filter(address -> address instanceof Inet4Address).findFirst().orElse(nic.addresses().getFirst());
    }

    private static boolean shareable(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) return false;
        return !(address instanceof Inet6Address) || !address.isSiteLocalAddress();
    }
}
