package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.abort;

class TftpReadTest {
    static final byte[] BIG = pattern(3000);
    @TempDir Path temp;
    Path shared, outside;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    TftpEngine engine;
    int port;

    static byte[] pattern(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) (i * 7);
        return bytes;
    }

    @BeforeEach void files() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.write(shared.resolve("big.bin"), BIG);
        Files.writeString(shared.resolve("lines.txt"), "a\nb\n");
        Files.writeString(outside.resolve("secret.txt"), "secret");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    TftpEngine engine(Duration timeout, int retries, int maxTransfers) throws IOException {
        port = Ports.free();
        var base = ServerDefinition.create("tftp", Protocol.TFTP, shared);
        var definition = new ServerDefinition(base.id(), "tftp", Protocol.TFTP, shared, base.bind(), port, Optional.empty(), false,
            base.login(), List.of(), false, false, false);
        engine = new TftpEngine(definition, new ShareRoot(shared, true), new ServerEvents(Clock.systemUTC(), events::add), List::of,
            timeout, retries, maxTransfers);
        return engine;
    }

    void start() throws Exception { engine(Duration.ofMillis(300), 5, 16).start(); }

    byte[] fetch(String name, int mode) throws IOException {
        var client = new TFTPClient();
        client.setDefaultTimeout(Duration.ofSeconds(3));
        client.open();
        try {
            var out = new ByteArrayOutputStream();
            client.receiveFile(name, mode, out, InetAddress.getLoopbackAddress(), port);
            return out.toByteArray();
        } finally {
            client.close();
        }
    }

    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    @Test void readsFilesOfAnySize() throws Exception {
        start();
        assertThat(engine.addresses()).containsExactly(URI.create("tftp://127.0.0.1:" + port + "/"));
        assertThat(fetch("docs/a.txt", TFTP.BINARY_MODE)).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(fetch("/big.bin", TFTP.BINARY_MODE)).isEqualTo(BIG);
        await(() -> transferred().size() == 2, "both reads end");
        assertThat(transferred()).extracting(ServerEvent.Transferred::path, ServerEvent.Transferred::bytes, ServerEvent.Transferred::complete)
            .containsExactly(tuple("/docs/a.txt", 5L, true), tuple("/big.bin", 3000L, true));
    }

    @Test void netasciiSendsCrLf() throws Exception {
        start();
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("lines.txt", "netascii"));
            var first = client.next();
            assertThat(first.packet()).isEqualTo(new TftpPacket.Data(1, "a\r\nb\r\n".getBytes(StandardCharsets.US_ASCII)));
            client.send(first.port(), new TftpPacket.Ack(1));
        }
        String separator = System.lineSeparator();
        assertThat(new String(fetch("lines.txt", TFTP.ASCII_MODE), StandardCharsets.US_ASCII)).isEqualTo("a" + separator + "b" + separator);
    }

    @Test void refusalsAreErrorPacketsWithoutHostPaths() throws Exception {
        try { Files.createSymbolicLink(shared.resolve("out"), outside); }
        catch (UnsupportedOperationException | IOException refused) { abort("symbolic links are not available: " + refused); }
        start();
        assertThatThrownBy(() -> fetch("missing.txt", TFTP.BINARY_MODE)).hasMessageContaining("Error code 1");
        assertThatThrownBy(() -> fetch("../outside/secret.txt", TFTP.BINARY_MODE)).as(".. stops at the root").hasMessageContaining("Error code 1");
        assertThatThrownBy(() -> fetch("out/secret.txt", TFTP.BINARY_MODE)).as("an outward link").hasMessageContaining("Error code 2");
        assertThatThrownBy(() -> fetch("docs", TFTP.BINARY_MODE)).as("a folder").hasMessageContaining("Error code 2");
        assertThatThrownBy(() -> fetch("CON", TFTP.BINARY_MODE)).as("a refused name").hasMessageContaining("Error code 2");
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "mail"));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Error(4, "Illegal TFTP operation"));
            client.send(port, new TftpPacket.Ack(1));
            assertThat(client.next()).as("not a request").isEqualTo(new RawClient.Reply(port, new TftpPacket.Error(4, "Illegal TFTP operation")));
        }
        await(() -> events.stream().anyMatch(e -> e instanceof ServerEvent.Failed f && f.message().equals("File not found: /missing.txt")), "logged");
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Failed)
            .noneMatch(e -> ((ServerEvent.Failed) e).message().contains(temp.toString()));
    }

    @Test void blksizeAndTsizeAreNegotiated() throws Exception {
        start();
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("big.bin", "octet", "blksize", "1024", "tsize", "0"));
            var oack = client.next();
            assertThat(oack.port()).as("a transfer has its own port").isNotEqualTo(port);
            var expected = new LinkedHashMap<String, String>();
            expected.put("blksize", "1024");
            expected.put("tsize", "3000");
            assertThat(oack.packet()).isEqualTo(new TftpPacket.OptionAck(expected));
            int[] lengths = new int[3];
            client.send(oack.port(), new TftpPacket.Ack(0));
            for (int block = 1; block <= 3; block++) {
                var data = (TftpPacket.Data) client.next().packet();
                assertThat(data.block()).isEqualTo(block);
                lengths[block - 1] = data.bytes().length;
                client.send(oack.port(), new TftpPacket.Ack(block));
            }
            assertThat(lengths).containsExactly(1024, 1024, 952);
        }
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet", "blksize", "4"));
            assertThat(client.next().packet()).as("blksize below 8 is ignored").isEqualTo(new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
        }
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet", "blksize", "100000"));
            var oack = (TftpPacket.OptionAck) client.next().packet();
            assertThat(oack.options()).containsExactly(Map.entry("blksize", "65464"));
        }
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("lines.txt", "netascii", "tsize", "0"));
            assertThat(client.next().packet()).as("no tsize in netascii").isInstanceOf(TftpPacket.Data.class);
        }
    }

    @Test void aLostPacketIsResentThenTheTransferGivesUp() throws Exception {
        engine(Duration.ofMillis(200), 2, 16).start();
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet"));
            var first = client.next();
            var again = client.next();
            assertThat(again).as("resent after the timeout, from the same port").isEqualTo(first);
            client.send(first.port(), new TftpPacket.Ack(1));
        }
        await(() -> transferred().size() == 1 && transferred().getFirst().complete(), "completes once acknowledged");
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet"));
            int sends = 0;
            while (client.receive(Duration.ofMillis(600)).isPresent()) sends++;
            assertThat(sends).as("the first send and two retries").isEqualTo(3);
        }
        await(() -> transferred().size() == 2, "the second transfer ends");
        assertThat(transferred().get(1).complete()).isFalse();
    }

    @Test void theSeventeenthTransferIsBusyAndStrangersAreTurnedAway() throws Exception {
        engine(Duration.ofSeconds(2), 5, 1).start();
        try (var holder = new RawClient(); var other = new RawClient()) {
            holder.send(port, RawClient.read("docs/a.txt", "octet"));
            var held = holder.next();
            other.send(port, RawClient.read("docs/a.txt", "octet"));
            assertThat(other.next()).isEqualTo(new RawClient.Reply(port, new TftpPacket.Error(0, "Server busy")));
            other.send(held.port(), new TftpPacket.Ack(1));
            assertThat(other.next()).isEqualTo(new RawClient.Reply(held.port(), new TftpPacket.Error(5, "Unknown transfer ID")));
            holder.send(held.port(), new TftpPacket.Ack(1));
            await(() -> transferred().size() == 1, "the held transfer ends");
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            TftpPacket reply;
            do {
                other.send(port, RawClient.read("docs/a.txt", "octet"));
                reply = other.next().packet();
            } while (!(reply instanceof TftpPacket.Data) && System.nanoTime() < deadline);
            assertThat(reply).as("a free slot serves again").isInstanceOf(TftpPacket.Data.class);
        }
    }

    @Test void aBusyPortFailsClearlyAndStopReleasesEverything() throws Exception {
        engine(Duration.ofSeconds(2), 5, 16);
        try (var taken = new DatagramSocket(null)) {
            taken.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        }
        assertThat(engine.addresses()).isEmpty();
        engine.start();
        assertThat(engine.port()).isEqualTo(port);
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet"));
            client.next();
            engine.stop();
            engine.stop();
        }
        await(() -> transferred().size() == 1, "the stopped transfer ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(engine.addresses()).isEmpty();
        new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), port)).close();
    }
}
