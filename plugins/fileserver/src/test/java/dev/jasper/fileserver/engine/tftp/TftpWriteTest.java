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

    void start(boolean writable, Duration timeout) throws Exception {
        port = Ports.free();
        var base = ServerDefinition.create("tftp", Protocol.TFTP, shared);
        var definition = new ServerDefinition(base.id(), "tftp", Protocol.TFTP, shared, base.bind(), port, Optional.empty(), writable,
            base.login(), List.of(), false, false, false);
        engine = new TftpEngine(definition, new ShareRoot(shared, !writable), new ServerEvents(Clock.systemUTC(), events::add), List::of,
            timeout, 5, 16);
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
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
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
        start(true, Duration.ofMillis(100));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/a.txt", "octet"));
            var ack0 = client.next();
            assertThat(ack0.packet()).isEqualTo(new TftpPacket.Ack(0));
            client.send(ack0.port(), new TftpPacket.Data(1, new byte[512]));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Ack(1));
        }
        await(() -> transferred().size() == 1, "the abandoned write ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(docs()).containsExactly("a.txt");
    }

    @Test void optionsAndALostFinalAck() throws Exception {
        start(true, Duration.ofMillis(500));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/b.txt", "octet", "blksize", "1024", "tsize", "5"));
            var oack = client.next();
            var expected = new LinkedHashMap<String, String>();
            expected.put("blksize", "1024");
            expected.put("tsize", "5");
            assertThat(oack.packet()).isEqualTo(new TftpPacket.OptionAck(expected));
            client.send(oack.port(), new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Ack(1));
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
}
