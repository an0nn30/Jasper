package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FtpEngineTest {
    static final ResolvedLogin DUSTIN = ResolvedLogin.password("dustin", "pw".toCharArray());
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);
    FtpEngine engine;
    int port;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(temp.resolve("outside.txt"), "secret");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    FtpEngine engine(int port, ResolvedLogin login, boolean writable, boolean tls, boolean requireTls) throws IOException {
        this.port = port;
        var base = ServerDefinition.create("ftp", Protocol.FTP, shared);
        var definition = new ServerDefinition(base.id(), "ftp", Protocol.FTP, shared, base.bind(), port, Optional.empty(), writable,
            base.login(), List.of(), tls, requireTls, false);
        engine = new FtpEngine(definition, new ShareRoot(shared, !writable), login, new ServerEvents(Clock.systemUTC(), events::add), throttle,
            List::of, Ports.freeRange(21), new TlsMaterial(temp.resolve("tls"), Clock.systemUTC(), () -> ""));
        return engine;
    }

    FtpEngine start(ResolvedLogin login, boolean writable, boolean tls, boolean requireTls) throws Exception {
        return Ports.start(free -> engine(free, login, writable, tls, requireTls));
    }

    FTPClient client() throws IOException {
        var client = new FTPClient();
        client.setDefaultTimeout(5000);
        client.connect("127.0.0.1", port);
        client.enterLocalPassiveMode();
        return client;
    }

    FTPSClient secureClient() throws IOException {
        var client = new FTPSClient("TLS", false); // explicit: connect sends AUTH TLS
        client.setDefaultTimeout(5000);
        client.connect("127.0.0.1", port);
        client.enterLocalPassiveMode();
        return client;
    }

    static String fetch(FTPClient client, String path) throws IOException {
        var out = new ByteArrayOutputStream();
        assertThat(client.retrieveFile(path, out)).as("RETR " + path + ": " + client.getReplyString()).isTrue();
        return out.toString(StandardCharsets.UTF_8);
    }

    static boolean store(FTPClient client, String path, String text) throws IOException {
        return client.storeFile(path, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** A transfer's reply reaches the client before the ftplet commits it; wait for the result. */
    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    static String content(Path file) {
        try { return Files.readString(file); } catch (IOException missing) { return null; }
    }

    @Test void anonymousReadOnlyListsAndDownloads() throws Exception {
        start(ResolvedLogin.anonymous(), false, false, false);
        assertThat(engine.addresses()).containsExactly(URI.create("ftp://127.0.0.1:" + port + "/"));
        var client = client();
        assertThat(client.login("anonymous", "me@example.com")).isTrue();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        assertThat(client.listFiles("/")).extracting(FTPFile::getName).containsExactly("docs");
        assertThat(fetch(client, "/docs/a.txt")).isEqualTo("hello");
        assertThat(store(client, "/up.txt", "x")).as("read-only").isFalse();
        assertThat(client.makeDirectory("/x")).isFalse();
        assertThat(client.deleteFile("/docs/a.txt")).isFalse();
        assertThat(shared.resolve("up.txt")).doesNotExist();
        client.logout();
        client.disconnect();
        await(() -> events.stream().anyMatch(e -> e instanceof ServerEvent.Transferred done && done.complete()
            && done.path().equals("/docs/a.txt") && done.bytes() == 5), "the download is logged");
        assertThat(events).anyMatch(e -> e instanceof ServerEvent.Connected);
        assertThat(events).anyMatch(e -> e instanceof ServerEvent.LoginSucceeded ok && ok.user().equals("anonymous"));
        var named = client();
        assertThat(named.login("dustin", "pw")).as("an anonymous server has no named users").isFalse();
        named.disconnect();
    }

    @Test void theWorkingDirectoryStaysInsideTheShare() throws Exception {
        start(ResolvedLogin.anonymous(), false, false, false);
        var client = client();
        assertThat(client.login("anonymous", "x")).isTrue();
        assertThat(client.changeWorkingDirectory("..")).isTrue();
        assertThat(client.printWorkingDirectory()).isEqualTo("/");
        assertThat(client.changeWorkingDirectory("/docs")).isTrue();
        assertThat(client.changeWorkingDirectory("../../..")).isTrue();
        assertThat(client.printWorkingDirectory()).isEqualTo("/");
        assertThat(client.retrieveFile("../outside.txt", new ByteArrayOutputStream())).isFalse();
        assertThat(client.retrieveFile("/docs/../../outside.txt", new ByteArrayOutputStream())).isFalse();
        client.disconnect();
    }

    @Test void aUserWithWritesUploadsRenamesAndDeletes() throws Exception {
        start(DUSTIN, true, false, false);
        var client = client();
        assertThat(client.login("anonymous", "x")).as("no anonymous login").isFalse();
        client.disconnect(); // FtpServer closes the connection after refusing anonymous
        client = client();
        assertThat(client.login("dustin", "pw")).isTrue();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        assertThat(store(client, "/docs/new.txt", "abc")).isTrue();
        await(() -> "abc".equals(content(shared.resolve("docs/new.txt"))), "the upload is committed");
        assertThat(store(client, "/docs/a.txt", "replaced")).isTrue();
        await(() -> "replaced".equals(content(shared.resolve("docs/a.txt"))), "the file is replaced");
        assertThat(client.appendFile("/docs/a.txt", new ByteArrayInputStream("!".getBytes(StandardCharsets.UTF_8)))).isTrue();
        await(() -> "replaced!".equals(content(shared.resolve("docs/a.txt"))), "the append lands");
        client.setRestartOffset(2);
        assertThat(store(client, "/docs/a.txt", "zz")).as("resuming part-way through is refused").isFalse();
        assertThat(content(shared.resolve("docs/a.txt"))).isEqualTo("replaced!");
        assertThat(client.rename("/docs/new.txt", "/docs/renamed.txt")).isTrue();
        assertThat(client.makeDirectory("/docs/sub")).isTrue();
        assertThat(client.removeDirectory("/docs/sub")).isTrue();
        assertThat(client.deleteFile("/docs/renamed.txt")).isTrue();
        client.logout();
        client.disconnect();
        try (var listing = Files.list(shared.resolve("docs"))) {
            assertThat(listing.map(p -> p.getFileName().toString()).toList()).as("no temporary files remain").containsExactly("a.txt");
        }
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Changed).extracting(e -> ((ServerEvent.Changed) e).description())
            .containsExactly("renamed /docs/new.txt to /docs/renamed.txt", "created /docs/sub", "deleted /docs/sub", "deleted /docs/renamed.txt");
    }

    @Test void md5CompletesTheDownloadItReads() throws Exception {
        start(ResolvedLogin.anonymous(), false, false, false);
        var client = client();
        assertThat(client.login("anonymous", "me@example.com")).isTrue();
        assertThat(client.sendCommand("MD5 /docs/a.txt")).as(client.getReplyString()).isEqualTo(251);
        await(() -> transfers().size() == 1, "the checksum read is logged");
        assertThat(transfers()).singleElement().satisfies(done -> {
            assertThat(done.path()).isEqualTo("/docs/a.txt");
            assertThat(done.complete()).isTrue();
            assertThat(done.bytes()).isEqualTo(5);
        });
        client.setFileType(FTP.BINARY_FILE_TYPE);
        assertThat(fetch(client, "/docs/a.txt")).isEqualTo("hello");
        await(() -> transfers().size() == 2, "the download after it is logged");
        assertThat(transfers()).allMatch(ServerEvent.Transferred::complete);
        client.disconnect();
    }

    @Test void mmd5CompletesEveryFileItReads() throws Exception {
        Files.writeString(shared.resolve("b.txt"), "bee");
        start(ResolvedLogin.anonymous(), false, false, false);
        var client = client();
        assertThat(client.login("anonymous", "me@example.com")).isTrue();
        assertThat(client.sendCommand("MMD5 /docs/a.txt, /b.txt")).as(client.getReplyString()).isEqualTo(252);
        await(() -> transfers().size() == 2, "both reads are logged");
        assertThat(transfers()).extracting(ServerEvent.Transferred::path, ServerEvent.Transferred::complete, ServerEvent.Transferred::bytes)
            .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("/docs/a.txt", true, 5L), org.assertj.core.groups.Tuple.tuple("/b.txt", true, 3L));
        client.disconnect();
    }

    @Test void theWorkingDirectoryCannotBeRemoved() throws Exception {
        Files.createDirectories(shared.resolve("empty"));
        start(DUSTIN, true, false, false);
        var client = client();
        assertThat(client.login("dustin", "pw")).isTrue();
        assertThat(client.changeWorkingDirectory("/empty")).isTrue();
        assertThat(client.removeDirectory("/empty")).as("RMD of the working directory: " + client.getReplyString()).isFalse();
        assertThat(shared.resolve("empty")).isDirectory();
        assertThat(client.changeWorkingDirectory("/")).isTrue();
        assertThat(client.removeDirectory("/empty")).isTrue();
        assertThat(shared.resolve("empty")).doesNotExist();
        client.disconnect();
    }

    List<ServerEvent.Transferred> transfers() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    @Test void aWrongPasswordIsThrottled() throws Exception {
        start(DUSTIN, false, false, false);
        var first = client();
        assertThat(first.login("dustin", "bad")).isFalse();
        first.disconnect();
        var second = client();
        assertThat(second.login("dustin", "pw")).as("too soon after a failure").isFalse();
        second.disconnect();
        clock.advance(Duration.ofSeconds(2));
        var third = client();
        assertThat(third.login("dustin", "pw")).isTrue();
        third.disconnect();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginFailed).hasSize(1);
    }

    @Test void requireTlsRefusesPlainLoginsAndPlainData() throws Exception {
        start(DUSTIN, false, true, true);
        var plain = client();
        assertThat(plain.login("dustin", "pw")).isFalse();
        assertThat(plain.getReplyCode()).isEqualTo(530);
        plain.disconnect();
        var secure = secureClient();
        assertThat(secure.login("dustin", "pw")).isTrue();
        secure.setFileType(FTP.BINARY_FILE_TYPE);
        assertThat(secure.listNames("/")).as("no PROT P yet").isNull();
        assertThat(secure.getReplyCode()).isEqualTo(521);
        secure.execPBSZ(0);
        secure.execPROT("P");
        assertThat(fetch(secure, "/docs/a.txt")).isEqualTo("hello");
        secure.disconnect();
    }

    @Test void tlsWithoutRequireAllowsBoth() throws Exception {
        start(DUSTIN, false, true, false);
        var plain = client();
        assertThat(plain.login("dustin", "pw")).isTrue();
        assertThat(fetch(plain, "/docs/a.txt")).isEqualTo("hello");
        plain.disconnect();
        var secure = secureClient();
        assertThat(secure.login("dustin", "pw")).isTrue();
        secure.execPBSZ(0);
        secure.execPROT("P");
        assertThat(fetch(secure, "/docs/a.txt")).isEqualTo("hello");
        secure.disconnect();
    }

    @Test void withoutTlsAuthIsRefused() throws Exception {
        start(DUSTIN, false, false, false);
        var client = client();
        assertThat(client.sendCommand("AUTH", "TLS")).isEqualTo(431);
        client.disconnect();
    }

    @Test void aBusyPortFailsWithAClearMessageAndStopFreesThePort() throws Exception {
        engine(Ports.free(), ResolvedLogin.anonymous(), false, false, false);
        try (var taken = new ServerSocket()) {
            taken.setReuseAddress(false);
            taken.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        }
        assertThat(engine.addresses()).isEmpty();
        start(ResolvedLogin.anonymous(), false, false, false);
        assertThat(engine.port()).isEqualTo(port);
        engine.stop();
        engine.stop();
        assertThat(engine.addresses()).isEmpty();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), port).close()).isInstanceOf(ConnectException.class);
    }
}
