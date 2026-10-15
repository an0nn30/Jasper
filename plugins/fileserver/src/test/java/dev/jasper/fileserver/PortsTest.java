package dev.jasper.fileserver;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.sftp.HostKey;
import dev.jasper.fileserver.engine.sftp.SftpEngine;
import dev.jasper.fileserver.engine.tftp.TftpEngine;
import dev.jasper.fileserver.share.ShareRoot;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortsTest {
    @TempDir Path temp;
    final ServerEvents events = new ServerEvents(Clock.systemUTC(), event -> { });

    ServerDefinition definition(Protocol protocol, int port) {
        var base = ServerDefinition.create("test", protocol, temp);
        return new ServerDefinition(base.id(), base.name(), protocol, temp, base.bind(), port, Optional.empty(), false, base.login(),
            List.of(), false, false, false);
    }

    @Test void aStartMovesToAnotherPortWhenAnotherSocketTakesTheFirst() throws Exception {
        var offered = new ArrayList<Integer>();
        try (var other = new DatagramSocket(null)) {
            TftpEngine engine = Ports.start(port -> {
                offered.add(port);
                if (offered.size() == 1) other.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port)); // it wins the race to the bind
                return new TftpEngine(definition(Protocol.TFTP, port), new ShareRoot(temp, true), events, List::of);
            });
            try {
                assertThat(offered).hasSize(2).doesNotHaveDuplicates();
                assertThat(engine.port()).isEqualTo(offered.get(1));
            } finally {
                engine.stop();
            }
        }
    }

    @Test void aPassiveRangeLiesBelowTheEphemeralPorts() {
        PortRange range = Ports.freeRange(21);
        assertThat(range.last() - range.first()).isEqualTo(20);
        assertThat(range.last()).as("below Linux's first ephemeral port, the lowest default").isLessThan(32768);
    }

    @Test void anyOtherFailureIsNotRetried() {
        var offered = new ArrayList<Integer>();
        assertThatThrownBy(() -> Ports.start(port -> {
            offered.add(port);
            return new SftpEngine(definition(Protocol.SFTP, port), new ShareRoot(temp, true), ResolvedLogin.anonymous(), events,
                new LoginThrottle(new TestClock()), List::of, new HostKey(temp.resolve("hostkey")));
        })).isInstanceOf(EngineFailure.class).hasMessage("SFTP needs a password or authorized keys");
        assertThat(offered).hasSize(1);
    }
}
