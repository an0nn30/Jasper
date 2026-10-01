package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PumpTest {
    @Test void copiesBothWaysCountsBytesAndPassesTheHalfCloseOn() throws Exception {
        try (var echo = new EchoServer(); var front = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            var counters = new Counters();
            Thread pumping = Thread.ofVirtual().start(() -> {
                try {
                    Socket near = front.accept();
                    Socket far = new Socket(InetAddress.ofLiteral("127.0.0.1"), echo.port());
                    Pump.run(Pump.of(near), Pump.of(far), counters);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            assertThat(EchoServer.roundTrip(front.getLocalPort(), "hello tunnel")).isEqualTo("hello tunnel");
            pumping.join(10_000);
            assertThat(pumping.isAlive()).as("both directions ended").isFalse();
            assertThat(counters.sent()).isEqualTo(12);
            assertThat(counters.received()).isEqualTo(12);
        }
    }

    @Test void countersStartEmptyAndTheLatestRefusalIsClearedByTheNextConnection() {
        var counters = new Counters();
        assertThat(counters.open()).isZero();
        assertThat(counters.warning()).isEmpty();
        counters.refused("Connections are being refused: Connection refused");
        assertThat(counters.warning()).contains("Connections are being refused: Connection refused");
        counters.opened();
        assertThat(counters.warning()).isEmpty();
        assertThat(counters.open()).isEqualTo(1);
        counters.closed();
        assertThat(counters.open()).isZero();
    }
}
