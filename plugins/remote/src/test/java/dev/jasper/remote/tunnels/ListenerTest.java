package dev.jasper.remote.tunnels;

import dev.jasper.remote.client.Failures;
import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ListenerTest {
    @Test void acceptsOnLocalhostUntilClosed() throws Exception {
        Listener listener = Listener.open(Tunnel.LOCALHOST, 0, socket -> {
            try (socket) { socket.getOutputStream().write("hi".getBytes(StandardCharsets.UTF_8)); }
            catch (IOException ignored) { }
        });
        int port = listener.port();
        assertThat(port).isPositive();
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            assertThat(new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hi");
        }
        listener.close();
        listener.close();
        assertThatThrownBy(() -> new Socket(InetAddress.ofLiteral("127.0.0.1"), port).close()).isInstanceOf(ConnectException.class);
    }

    @Test void onceClosedThePortRefusesConnectionsAndCanBeBoundAgainEvenOnABusyMachine() throws Exception {
        var accepted = new CountDownLatch(1);
        Listener listener = Listener.open("127.0.0.1", 0, socket -> {
            try (socket) { accepted.countDown(); } catch (IOException ignored) { }
        });
        int port = listener.port();
        new Socket(InetAddress.ofLiteral("127.0.0.1"), port).close();
        assertThat(accepted.await(5, TimeUnit.SECONDS)).as("the accepting thread runs").isTrue();
        // Every carrier busy for a moment, so the virtual thread accepting on the socket cannot run: the JDK closes a
        // socket that such a thread is accepting on only when that thread runs again.
        int carriers = Integer.getInteger("jdk.virtualThreadScheduler.parallelism", Runtime.getRuntime().availableProcessors());
        var busy = new CountDownLatch(carriers);
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
        var hogs = new ArrayList<Thread>();
        for (int i = 0; i < carriers; i++)
            hogs.add(Thread.ofVirtual().start(() -> { busy.countDown(); while (System.nanoTime() < until) Thread.onSpinWait(); }));
        try {
            busy.await();
            listener.close();
            assertThatThrownBy(() -> new Socket(InetAddress.ofLiteral("127.0.0.1"), port).close()).isInstanceOf(ConnectException.class);
            try (var again = new ServerSocket()) { again.bind(new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), port)); }
        } finally {
            for (Thread hog : hogs) hog.join();
        }
    }

    @Test void aBusyPortIsReportedInTheUsersWords() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            assertThatThrownBy(() -> Listener.open("127.0.0.1", busy.getLocalPort(), socket -> { }))
                .isInstanceOf(Failures.Failure.class).hasMessage("Port " + busy.getLocalPort() + " is already in use on this computer");
        }
    }

    @Test void anAddressThisComputerDoesNotHaveIsReported() {
        // 192.0.2.0/24 is TEST-NET-1: never assigned to a real interface.
        assertThatThrownBy(() -> Listener.open("192.0.2.1", 0, socket -> { }))
            .isInstanceOf(Failures.Failure.class).hasMessage("Cannot listen on 192.0.2.1: not an address of this computer");
    }

    @Test void bindFailuresNameTheirCause() {
        assertThat(Listener.bindMessage(new BindException("Permission denied"), "localhost", 80)).isEqualTo("Port 80 needs administrator rights");
        assertThat(Listener.bindMessage(new BindException("Address already in use"), "localhost", 5432))
            .isEqualTo("Port 5432 is already in use on this computer");
        assertThat(Listener.bindMessage(new BindException("Something else"), "localhost", 1)).isEqualTo("Cannot listen on localhost:1: Something else");
    }

    @Test void localhostMeansBothLoopbacksAndAStarMeansEveryAddress() {
        assertThat(Listener.addresses(Tunnel.LOCALHOST)).extracting(InetAddress::getHostAddress).containsExactly("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat(Listener.addresses(Tunnel.ALL)).singleElement().matches(InetAddress::isAnyLocalAddress);
        assertThat(Listener.addresses("10.1.2.3")).extracting(InetAddress::getHostAddress).containsExactly("10.1.2.3");
    }
}
