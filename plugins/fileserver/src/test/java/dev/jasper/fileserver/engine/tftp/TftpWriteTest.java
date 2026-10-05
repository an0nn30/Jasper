package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
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

class TftpWriteTest {
    static final byte[] BIG = TftpReadTest.pattern(2000);
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    TftpEngine engine;
    int port;

    @BeforeEach void files() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    void start(boolean writable, Duration timeout) throws Exception { start(writable, timeout, 5); }

    void start(boolean writable, Duration timeout, int retries) throws Exception {
        port = Ports.free();
        var base = ServerDefinition.create("tftp", Protocol.TFTP, shared);
        var definition = new ServerDefinition(base.id(), "tftp", Protocol.TFTP, shared, base.bind(), port, Optional.empty(), writable,
            base.login(), List.of(), false, false, false);
        engine = new TftpEngine(definition, new ShareRoot(shared, !writable), new ServerEvents(Clock.systemUTC(), events::add), List::of,
            timeout, retries, 16);
        engine.start();
    }

    void put(String name, int mode, byte[] content) throws IOException {
        var client = new TFTPClient();
        client.setDefaultTimeout(Duration.ofSeconds(3));
        client.open();
        try { client.sendFile(name, mode, new ByteArrayInputStream(content), InetAddress.getLoopbackAddress(), port); }
        finally { client.close(); }
    }

    List<String> docs() throws IOException {
        try (var listing = Files.list(shared.resolve("docs"))) { return listing.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos(); // generous: a loaded parallel build
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    @Test void aWritableShareTakesNewAndReplacedFiles() throws Exception {
        start(true, Duration.ofMillis(300));
        put("docs/new.bin", TFTP.BINARY_MODE, BIG);
        assertThat(shared.resolve("docs/new.bin")).hasBinaryContent(BIG);
        put("/docs/a.txt", TFTP.BINARY_MODE, "replaced".getBytes(StandardCharsets.UTF_8));
        assertThat(shared.resolve("docs/a.txt")).hasContent("replaced");
        assertThat(docs()).as("no temporary files remain").containsExactly("a.txt", "new.bin");
        await(() -> transferred().size() == 2, "both writes end");
        assertThat(transferred()).allSatisfy(done -> {
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.UPLOAD);
            assertThat(done.complete()).isTrue();
        });
        assertThat(transferred().getFirst().bytes()).isEqualTo(2000);
    }

    @Test void netasciiWritesBecomeLocalLineEnds() throws Exception {
        start(true, Duration.ofMillis(300));
        put("docs/lines.txt", TFTP.ASCII_MODE, ("a" + System.lineSeparator() + "b" + System.lineSeparator()).getBytes(StandardCharsets.US_ASCII));
        assertThat(shared.resolve("docs/lines.txt")).hasContent("a\nb\n");
    }

    @Test void aReadOnlyShareAndAFolderAreRefused() throws Exception {
        start(false, Duration.ofMillis(300));
        assertThatThrownBy(() -> put("docs/new.txt", TFTP.BINARY_MODE, new byte[]{1})).hasMessageContaining("Error code 2");
        assertThat(shared.resolve("docs/new.txt")).doesNotExist();
        engine.stop();
        start(true, Duration.ofMillis(300));
        assertThatThrownBy(() -> put("docs", TFTP.BINARY_MODE, new byte[]{1})).hasMessageContaining("Error code 2");
    }

    @Test void anUnfinishedWriteLeavesTheOriginal() throws Exception {
        start(true, Duration.ofMillis(500), 1);
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/a.txt", "octet"));
            var ack0 = client.next();
            assertThat(ack0.packet()).isEqualTo(new TftpPacket.Ack(0));
            client.send(ack0.port(), new TftpPacket.Data(1, new byte[512]));
            assertThat(nextOtherThan(client, new TftpPacket.Ack(0)).packet()).isEqualTo(new TftpPacket.Ack(1));
        }
        await(() -> transferred().size() == 1, "the abandoned write ends (after 1 s with no block 2, plus a margin)");
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(docs()).containsExactly("a.txt");
    }

    @Test void optionsAndALostFinalAck() throws Exception {
        start(true, Duration.ofSeconds(1));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/b.txt", "octet", "blksize", "1024", "tsize", "5"));
            var oack = client.next();
            var expected = new LinkedHashMap<String, String>();
            expected.put("blksize", "1024");
            expected.put("tsize", "5");
            assertThat(oack.packet()).isEqualTo(new TftpPacket.OptionAck(expected));
            client.send(oack.port(), new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertThat(nextOtherThan(client, oack.packet()).packet()).isEqualTo(new TftpPacket.Ack(1));
            client.send(oack.port(), new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertThat(client.next().packet()).as("the final ACK again, for a client that lost it").isEqualTo(new TftpPacket.Ack(1));
        }
        assertThat(shared.resolve("docs/b.txt")).hasContent("hello");
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/huge.bin", "octet", "tsize", String.valueOf(Long.MAX_VALUE)));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Error(3, "Disk full or allocation exceeded"));
        }
        assertThat(shared.resolve("docs/huge.bin")).doesNotExist();
    }

    List<String> tempFiles() throws IOException {
        try (var listing = Files.list(shared.resolve("docs"))) {
            return listing.map(p -> p.getFileName().toString()).filter(n -> n.startsWith(".jasper-upload-")).toList();
        }
    }

    @Test void aWrongLengthIsRefusedAndTheOriginalKept() throws Exception {
        start(true, Duration.ofMillis(300));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/a.txt", "octet", "tsize", "6"));
            var oack = client.next();
            assertThat(oack.packet()).isInstanceOf(TftpPacket.OptionAck.class);
            client.send(oack.port(), new TftpPacket.Data(1, "short".getBytes(StandardCharsets.UTF_8)));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Error(0, "Transfer size mismatch"));
        }
        await(() -> transferred().size() == 1, "the refused write ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(tempFiles()).isEmpty();
    }

    @Test void aNetasciiWriteIsNotCheckedAgainstItsTsize() throws Exception {
        start(true, Duration.ofSeconds(1));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/n.txt", "netascii", "tsize", "999"));
            var ack0 = client.next();
            assertThat(ack0.packet()).as("tsize is not answered in netascii").isEqualTo(new TftpPacket.Ack(0));
            client.send(ack0.port(), new TftpPacket.Data(1, "a\r\nb\r\n".getBytes(StandardCharsets.US_ASCII)));
            assertThat(nextOtherThan(client, new TftpPacket.Ack(0)).packet()).isEqualTo(new TftpPacket.Ack(1));
        }
        assertThat(shared.resolve("docs/n.txt")).as("the size on the wire differs from the file's, so it is committed").hasContent("a\nb\n");
        await(() -> transferred().size() == 1, "the write ends");
        assertThat(transferred().getFirst().complete()).isTrue();
    }

    @Test void anOversizedBlockIsIllegal() throws Exception {
        start(true, Duration.ofMillis(300));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/a.txt", "octet", "blksize", "16"));
            var oack = client.next();
            assertThat(oack.packet()).isInstanceOf(TftpPacket.OptionAck.class);
            client.send(oack.port(), new TftpPacket.Data(1, new byte[17]));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Error(4, "Illegal TFTP operation"));
        }
        await(() -> transferred().size() == 1, "the refused write ends");
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(tempFiles()).isEmpty();
    }

    @Test void aDatagramLargerThanTheLargestBlockIsIllegal() throws Exception {
        start(true, Duration.ofSeconds(1));
        try (var client = new RawClient()) {
            client.socket.setSendBufferSize(1 << 17);
            client.send(port, RawClient.write("docs/a.txt", "octet", "blksize", "65464"));
            var oack = client.next();
            assertThat(oack.packet()).isEqualTo(new TftpPacket.OptionAck(java.util.Map.of("blksize", "65464")));
            client.send(oack.port(), new TftpPacket.Data(1, new byte[TftpPacket.MAX_BLOCK + 20]));
            assertThat(client.next().packet()).as("not truncated to a full block and acknowledged").isEqualTo(TftpTransfer.ILLEGAL);
        }
        await(() -> transferred().size() == 1, "the refused write ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(tempFiles()).isEmpty();
    }

    @Test void theFinalAckIsRepeatedForTheWholeDally() throws Exception {
        start(true, Duration.ofMillis(300));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/c.txt", "octet"));
            var ack0 = client.next();
            assertThat(ack0.packet()).isEqualTo(new TftpPacket.Ack(0));
            client.send(ack0.port(), new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertThat(nextOtherThan(client, new TftpPacket.Ack(0)).packet()).isEqualTo(new TftpPacket.Ack(1));
            Thread.sleep(600); // two timeouts: a client with a slower retransmit timer than the server's
            client.send(ack0.port(), new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertThat(client.next().packet()).as("the dally lasts timeout x retries (1.5 s here)").isEqualTo(new TftpPacket.Ack(1));
        }
        assertThat(shared.resolve("docs/c.txt")).hasContent("hello");
    }

    @Test void aFailedWriteIsLoggedByClassNameAndNotAtAllOnceStopped() throws Exception {
        var sink = new ServerEvents(Clock.systemUTC(), events::add);
        var socket = new java.net.DatagramSocket(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        try (var client = new RawClient()) {
            var transfer = new TftpTransfer(new ShareRoot(shared, false), sink, (java.net.InetSocketAddress) client.socket.getLocalSocketAddress(),
                RawClient.read("missing.txt", "octet"), Duration.ofMillis(100), 0);
            transfer.run(socket);
            assertThat(client.next().packet()).isEqualTo(TftpTransfer.NOT_FOUND);
            events.clear();
            transfer.failWrite(new IOException((String) null));
            assertThat(client.next().packet()).as("the client still learns why").isEqualTo(TftpTransfer.FAILED);
            assertThat(events).singleElement().satisfies(event ->
                assertThat(((ServerEvent.Failed) event).message()).isEqualTo("TFTP transfer failed: IOException"));
            events.clear();
            socket.close();
            transfer.failWrite(new IOException("Stream Closed"));
            assertThat(events).as("a stop during disk I/O is not an error").isEmpty();
        } finally {
            socket.close();
        }
    }

    static RawClient.Reply nextOtherThan(RawClient client, TftpPacket resent) throws IOException {
        RawClient.Reply reply;
        do reply = client.next(); while (reply.packet().equals(resent));
        return reply;
    }

    @Test void aDuplicatedBlockIsAcknowledgedAgainAndWrittenOnce() throws Exception {
        start(true, Duration.ofMillis(500));
        byte[] first = TftpReadTest.pattern(512);
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/dup.bin", "octet"));
            var ack0 = client.next();
            assertThat(ack0.packet()).isEqualTo(new TftpPacket.Ack(0));
            client.send(ack0.port(), new TftpPacket.Data(1, first));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Ack(1));
            client.send(ack0.port(), new TftpPacket.Data(1, first));
            assertThat(client.next().packet()).as("the duplicate is acknowledged again").isEqualTo(new TftpPacket.Ack(1));
            client.send(ack0.port(), new TftpPacket.Data(2, "end".getBytes(StandardCharsets.UTF_8)));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Ack(2));
        }
        byte[] expected = new byte[515];
        System.arraycopy(first, 0, expected, 0, 512);
        System.arraycopy("end".getBytes(StandardCharsets.UTF_8), 0, expected, 512, 3);
        assertThat(shared.resolve("docs/dup.bin")).hasBinaryContent(expected);
    }

    @Test void stoppingTheServerMidWriteKeepsTheOriginal() throws Exception {
        start(true, Duration.ofSeconds(5));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/a.txt", "octet"));
            var ack0 = client.next();
            client.send(ack0.port(), new TftpPacket.Data(1, new byte[512]));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Ack(1));
            engine.stop();
            await(() -> transferred().size() == 1, "the stopped write ends");
        }
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(tempFiles()).isEmpty();
    }
}
