package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Protocol;
import java.net.BindException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerAddressesTest {
    static InetAddress ip(String literal) {
        try { return InetAddress.getByName(literal); } catch (UnknownHostException impossible) { throw new AssertionError(impossible); }
    }

    final List<ServerAddresses.Nic> nics = List.of(
        new ServerAddresses.Nic("lo0", true, true, false, List.of(ip("127.0.0.1"), ip("::1"))),
        new ServerAddresses.Nic("en0", true, false, false, List.of(ip("fe80::1"), ip("192.168.1.20"), ip("2001:db8::5"))),
        new ServerAddresses.Nic("en1", false, false, false, List.of(ip("10.0.0.2"))),
        new ServerAddresses.Nic("utun3", true, false, true, List.of(ip("10.8.0.1"))),
        new ServerAddresses.Nic("en5", true, false, false, List.of(ip("169.254.10.10"))));

    @Test void reachableAddressesFollowTheBind() {
        assertThat(ServerAddresses.reachable(Bind.LOOPBACK, nics)).containsExactly(InetAddress.getLoopbackAddress());
        assertThat(ServerAddresses.reachable(Bind.ALL, nics)).as("IPv4 first; no link-local, down or virtual interfaces")
            .containsExactly(ip("192.168.1.20"), ip("2001:db8::5"));
        assertThat(ServerAddresses.reachable(new Bind.Interface("en0"), nics)).containsExactly(ip("192.168.1.20"));
        assertThat(ServerAddresses.reachable(Bind.ALL, List.of(nics.getFirst()))).as("nothing else up")
            .containsExactly(InetAddress.getLoopbackAddress());
    }

    @Test void formatsUrlsPerProtocol() {
        assertThat(ServerAddresses.urls(Protocol.HTTP, false, List.of(ip("192.168.1.20"), ip("2001:db8::5")), 8080, Optional.of("me")))
            .containsExactly(URI.create("http://192.168.1.20:8080/"), URI.create("http://[2001:db8:0:0:0:0:0:5]:8080/"));
        assertThat(ServerAddresses.urls(Protocol.HTTP, true, List.of(ip("10.0.0.2")), 8443, Optional.empty()))
            .containsExactly(URI.create("https://10.0.0.2:8443/"));
        assertThat(ServerAddresses.urls(Protocol.SFTP, false, List.of(ip("10.0.0.2")), 2222, Optional.of("me")))
            .containsExactly(URI.create("sftp://me@10.0.0.2:2222/"));
    }

    @Test void choosesTheBindAddressOrSaysWhyNot() throws Exception {
        assertThat(ServerAddresses.bindAddress(Bind.LOOPBACK, nics).isLoopbackAddress()).isTrue();
        assertThat(ServerAddresses.bindAddress(Bind.ALL, nics).isAnyLocalAddress()).isTrue();
        assertThat(ServerAddresses.bindAddress(new Bind.Interface("en0"), nics)).isEqualTo(ip("192.168.1.20"));
        assertThatThrownBy(() -> ServerAddresses.bindAddress(new Bind.Interface("en7"), nics)).isInstanceOf(EngineFailure.class)
            .hasMessage("Network interface en7 is not available");
        assertThatThrownBy(() -> ServerAddresses.bindAddress(new Bind.Interface("en1"), nics)).hasMessage("Network interface en1 is not available");
        assertThat(ServerAddresses.describe(ServerAddresses.bindAddress(Bind.ALL, nics))).isEqualTo("0.0.0.0");
        assertThat(nics.get(1).label()).isEqualTo("en0 (192.168.1.20)");
    }

    @Test void bindFailuresSayWhatToDo() {
        assertThat(EngineFailure.bind(new BindException("Address already in use"), 2121, "0.0.0.0", Protocol.FTP))
            .hasMessage("Port 2121 is already in use on 0.0.0.0");
        assertThat(EngineFailure.bind(new SocketException("Permission denied"), 21, "0.0.0.0", Protocol.FTP))
            .hasMessage("Port 21 needs administrator rights on this system — use a port above 1024 (for example 8021)");
        assertThat(EngineFailure.bind(new BindException("Cannot assign requested address"), 8080, "10.9.9.9", Protocol.HTTP))
            .hasMessage("Could not listen on port 8080: Cannot assign requested address");
    }
}
