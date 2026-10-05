package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.sftp.client.RawSftpClient;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.CopyMode;
import org.apache.sshd.sftp.client.SftpClient.OpenMode;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.SftpVersionSelector;
import org.apache.sshd.sftp.client.impl.SftpResponse;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

class SftpEngineTest {
    static final ResolvedLogin DUSTIN = ResolvedLogin.password("dustin", "pw".toCharArray());
    @TempDir Path temp;
    Path shared, outside;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);
    final Clients clients = new Clients();
    SftpEngine engine;
    int port;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(outside.resolve("secret.txt"), "secret");
    }

    @AfterEach void stop() {
        if (engine != null) engine.stop();
        clients.close();
    }

    SftpEngine engine(ResolvedLogin login, boolean writable, List<String> keys) throws IOException {
        port = Ports.free();
        var base = ServerDefinition.create("sftp", Protocol.SFTP, shared);
        var definition = new ServerDefinition(base.id(), "sftp", Protocol.SFTP, shared, base.bind(), port, Optional.empty(), writable,
            login.isAnonymous() ? Login.ANONYMOUS : Login.SESSION, keys, false, false, false);
        engine = new SftpEngine(definition, new ShareRoot(shared, !writable), login, new ServerEvents(Clock.systemUTC(), events::add), throttle,
            List::of, new HostKey(temp.resolve("hostkey")));
        return engine;
    }

    SftpClient start(boolean writable) throws Exception {
        engine(DUSTIN, writable, List.of()).start();
        return Clients.sftp(clients.password(port, "dustin", "pw"));
    }

    static String read(SftpClient sftp, String path) throws IOException {
        try (var in = sftp.read(path)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    static void write(SftpClient sftp, String path, String text) throws IOException {
        try (var out = sftp.write(path)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }

    static void link(Path link, Path target) {
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | IOException | SecurityException refused) { abort("symbolic links are not available: " + refused); }
    }

    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    List<String> docs() throws IOException {
        try (var listing = Files.list(shared.resolve("docs"))) { return listing.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    @Test void aPasswordLoginReadsAndListsInsideTheShare() throws Exception {
        var sftp = start(false);
        assertThat(engine.addresses()).containsExactly(URI.create("sftp://dustin@127.0.0.1:" + port + "/"));
        assertThat(sftp.canonicalPath(".")).isEqualTo("/");
        assertThat(sftp.canonicalPath("/docs/../..")).isEqualTo("/");
        assertThat(sftp.canonicalPath("docs")).isEqualTo("/docs");
        assertThat(Clients.names(sftp, "/")).containsExactly("docs");
        assertThat(Clients.names(sftp, "/docs")).containsExactly("a.txt");
        assertThat(read(sftp, "/docs/a.txt")).isEqualTo("hello");
        assertThat(sftp.stat("/docs/a.txt").getSize()).isEqualTo(5);
        assertThatThrownBy(() -> read(sftp, "/../outside/secret.txt")).as(".. stops at the root").isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.stat("/../../etc/hosts")).isInstanceOf(IOException.class);
    }

    /** A raw REALPATH. From version 6 it may carry a control byte and extra parts, which sshd joins with Path.resolve. */
    static SftpResponse realPath(SftpClient sftp, String path, Integer control, String... extras) throws IOException {
        var buffer = new ByteArrayBuffer();
        buffer.putString(path);
        if (control != null) buffer.putByte((byte) control.intValue());
        for (String extra : extras) buffer.putString(extra);
        var raw = (RawSftpClient) sftp;
        int id = raw.send(SftpConstants.SSH_FXP_REALPATH, buffer);
        return SftpResponse.parse(SftpConstants.SSH_FXP_REALPATH, raw.receive(id, Duration.ofSeconds(5)));
    }

    static int status(SftpResponse response) {
        assertThat(response.getType()).as("a status reply").isEqualTo(SftpConstants.SSH_FXP_STATUS);
        return response.getBuffer().getInt();
    }

    /** The NAME reply's file name and, from a version 3 reply, its long name. */
    static List<String> names(SftpResponse response, boolean longName) {
        assertThat(response.getType()).as("a name reply").isEqualTo(SftpConstants.SSH_FXP_NAME);
        var buffer = response.getBuffer();
        assertThat(buffer.getInt()).isEqualTo(1);
        String name = buffer.getString();
        return longName ? List.of(name, buffer.getString()) : List.of(name);
    }

    @Test void realPathsExtraPartsCannotLeaveTheShare() throws Exception {
        var sftp = start(false);
        assertThat(sftp.getVersion()).isEqualTo(SftpConstants.SFTP_V6);
        assertThat(names(realPath(sftp, "/", SftpConstants.SSH_FXP_REALPATH_NO_CHECK, "docs", "a.txt"), false)).containsExactly("/docs/a.txt");
        assertThat(names(realPath(sftp, "/docs", SftpConstants.SSH_FXP_REALPATH_STAT_ALWAYS, "..", "docs"), false)).containsExactly("/docs");
        for (int control : new int[]{SftpConstants.SSH_FXP_REALPATH_NO_CHECK, SftpConstants.SSH_FXP_REALPATH_STAT_IF, SftpConstants.SSH_FXP_REALPATH_STAT_ALWAYS})
            for (String extra : new String[]{outside.resolve("secret.txt").toString(), outside.resolve("missing.txt").toString(), "../..", "../../..", temp.toString()})
                assertThat(status(realPath(sftp, "/", control, extra))).as("control %d, extra %s", control, extra)
                    .isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED);
        assertThat(sftp.canonicalPath("/docs")).as("the channel is still open").isEqualTo("/docs");
    }

    @Test void realPathsExtraPartsDoNotFollowOutwardLinks() throws Exception {
        link(shared.resolve("out"), outside);
        var sftp = start(false);
        for (String name : new String[]{"secret.txt", "missing.txt"})
            assertThat(status(realPath(sftp, "/out", SftpConstants.SSH_FXP_REALPATH_STAT_ALWAYS, name))).as(name)
                .isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED);
    }

    @Test void aVersion3RealPathNamesNoHostFolder() throws Exception {
        engine(DUSTIN, false, List.of()).start();
        var sftp = SftpClientFactory.instance().createSftpClient(clients.password(port, "dustin", "pw"),
            SftpVersionSelector.fixedVersionSelector(SftpConstants.SFTP_V3));
        assertThat(sftp.getVersion()).isEqualTo(SftpConstants.SFTP_V3);
        for (String path : new String[]{"/", ".", "/docs/..", "/docs/a.txt"}) {
            List<String> reply = names(realPath(sftp, path, null), true);
            assertThat(reply.getFirst()).as(path).isEqualTo(path.equals("/docs/a.txt") ? "/docs/a.txt" : "/");
            assertThat(reply.getLast()).as("the long name of %s", path).doesNotContain(temp.toString()).doesNotContain("shared");
        }
    }

    @Test void aReadOnlyShareRefusesEveryChange() throws Exception {
        var sftp = start(false);
        assertThatThrownBy(() -> write(sftp, "/new.txt", "x")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.mkdir("/made")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.remove("/docs/a.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.rename("/docs/a.txt", "/docs/b.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.setStat("/docs/a.txt", new SftpClient.Attributes().perms(0600))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.open("/docs/a.txt", OpenMode.Write)).isInstanceOf(IOException.class);
        assertThat(shared.resolve("new.txt")).doesNotExist();
        assertThat(shared.resolve("made")).doesNotExist();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
    }

    @Test void linksNeverLeadOutAndCannotBeMadeOrRead() throws Exception {
        link(shared.resolve("out"), outside);
        link(shared.resolve("docs/to-secret"), outside.resolve("secret.txt"));
        link(shared.resolve("inside"), shared.resolve("docs"));
        var sftp = start(true);
        assertThat(Clients.names(sftp, "/")).as("outward links are not listed").containsExactly("docs", "inside");
        assertThat(read(sftp, "/inside/a.txt")).as("an inside link works").isEqualTo("hello");
        assertThatThrownBy(() -> read(sftp, "/out/secret.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> read(sftp, "/docs/to-secret")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> Clients.names(sftp, "/out")).isInstanceOfAny(IOException.class, UncheckedIOException.class);
        assertThatThrownBy(() -> write(sftp, "/out/new.txt", "x")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.open("/docs/to-secret", OpenMode.Write)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.remove("/out/secret.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.symLink("/docs/mylink", "/docs/a.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.readLink("/inside")).isInstanceOf(IOException.class);
        assertThat(outside.resolve("secret.txt")).hasContent("secret");
        assertThat(outside.resolve("new.txt")).doesNotExist();
        assertThat(shared.resolve("docs/mylink")).doesNotExist();
    }

    static int refusal(ThrowingRunnable action) {
        try {
            action.run();
        } catch (UncheckedIOException wrapped) {
            if (wrapped.getCause() instanceof SftpException refused) return refused.getStatus();
            throw wrapped;
        } catch (SftpException refused) {
            return refused.getStatus();
        } catch (Exception other) {
            throw new AssertionError("not an SFTP refusal", other);
        }
        throw new AssertionError("not refused");
    }

    interface ThrowingRunnable { void run() throws Exception; }

    @Test void outwardLinksShowNothingOfWhatTheyPointAt() throws Exception {
        link(shared.resolve("out"), outside);
        link(shared.resolve("docs/to-secret"), outside.resolve("secret.txt"));
        link(shared.resolve("docs/to-nowhere"), outside.resolve("missing.txt"));
        var sftp = start(true);
        for (String path : new String[]{"/docs/to-secret", "/docs/to-nowhere", "/out", "/out/secret.txt", "/out/missing.txt"})
            assertThat(refusal(() -> sftp.stat(path))).as("stat " + path).isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED);
        for (String path : new String[]{"/out", "/docs/to-secret"})
            assertThat(refusal(() -> Clients.names(sftp, path))).as("list " + path).isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED);
        assertThat(sftp.lstat("/docs/to-secret").isSymbolicLink()).as("the link itself").isTrue();
        assertThat(sftp.stat("/docs/a.txt").getSize()).isEqualTo(5);
        // The refusing exceptions carry host paths; the client gets only sshd's generic status text.
        assertThatThrownBy(() -> sftp.open("/docs/to-secret", OpenMode.Write)).isInstanceOf(SftpException.class)
            .satisfies(refused -> assertThat(refused.getMessage()).doesNotContain(temp.toString()).doesNotContain(temp.toRealPath().toString()));
        assertThatThrownBy(() -> sftp.stat("/out/secret.txt")).isInstanceOf(SftpException.class)
            .satisfies(refused -> assertThat(refused.getMessage()).doesNotContain(temp.toString()).doesNotContain(temp.toRealPath().toString()));
    }

    @Test void deepPathsBehindAnOutwardLinkAllFailAlike() throws Exception {
        Files.createDirectories(outside.resolve("present/deeper"));
        link(shared.resolve("out"), outside);
        var sftp = start(true);
        for (String path : new String[]{"/out/present/b/x", "/out/missing/b/x", "/out/present/deeper/c/x", "/out/missing/b"}) {
            assertThat(refusal(() -> sftp.stat(path))).as("stat " + path).isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED);
            assertThat(refusal(() -> sftp.mkdir(path))).as("mkdir " + path).isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED);
        }
        assertThat(refusal(() -> sftp.stat("/docs/missing/x"))).as("inside, missing is missing").isEqualTo(SftpConstants.SSH_FX_NO_SUCH_FILE);
    }

    @Test void ownersAndGroupsAreRefusedWithoutLookingTheNameUp() throws Exception {
        if (!Files.getFileStore(shared).supportsFileAttributeView("posix")) abort("no POSIX owners here");
        var attributes = Files.readAttributes(shared.resolve("docs/a.txt"), PosixFileAttributes.class);
        String owner = attributes.owner().getName(), group = attributes.group().getName();
        for (boolean writable : new boolean[]{false, true}) {
            if (engine != null) engine.stop();
            var sftp = start(writable);
            assertThat(sftp.getVersion()).isEqualTo(SftpConstants.SFTP_V6);
            // The client sends an owner only together with a group (SSH_FILEXFER_ATTR_OWNERGROUP).
            String unknown = "no-such-principal-zz";
            for (String[] pair : new String[][]{{owner, group}, {unknown, group}, {owner, unknown}, {unknown, unknown}}) {
                assertThat(refusal(() -> sftp.setStat("/docs/a.txt", new SftpClient.Attributes().owner(pair[0]).group(pair[1]))))
                    .as("owner %s, group %s, writable %s", pair[0], pair[1], writable).isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED);
            }
        }
    }

    @Test void anUploadAppearsWhenItsHandleClosesAndNotBefore() throws Exception {
        var sftp = start(true);
        try (var out = sftp.write("/docs/new.txt")) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertThat(shared.resolve("docs/new.txt")).as("not before the close").doesNotExist();
        }
        assertThat(shared.resolve("docs/new.txt")).hasContent("abc");
        write(sftp, "/docs/a.txt", "replaced");
        assertThat(shared.resolve("docs/a.txt")).hasContent("replaced");
        assertThat(docs()).as("no temporary files remain").containsExactly("a.txt", "new.txt");
    }

    @Test void aDroppedConnectionAbandonsTheUpload() throws Exception {
        engine(DUSTIN, true, List.of()).start();
        ClientSession session = clients.password(port, "dustin", "pw");
        var sftp = Clients.sftp(session);
        var handle = sftp.open("/docs/a.txt", OpenMode.Write, OpenMode.Create, OpenMode.Truncate);
        sftp.write(handle, 0, "partial".getBytes(StandardCharsets.UTF_8));
        session.close(true);
        await(() -> { try { return docs().equals(List.of("a.txt")); } catch (IOException e) { return false; } }, "the temporary file is removed");
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
    }

    @Test void otherWritesHappenInPlace() throws Exception {
        var sftp = start(true);
        try (var handle = sftp.open("/docs/a.txt", OpenMode.Write)) { sftp.write(handle, 0, "J".getBytes(StandardCharsets.UTF_8)); }
        assertThat(shared.resolve("docs/a.txt")).hasContent("Jello");
        try (var handle = sftp.open("/docs/a.txt", OpenMode.Write, OpenMode.Append)) { sftp.write(handle, 0, "!".getBytes(StandardCharsets.UTF_8)); }
        assertThat(shared.resolve("docs/a.txt")).hasContent("Jello!");
    }

    @Test void foldersRenamesAndRemovals() throws Exception {
        var sftp = start(true);
        sftp.mkdir("/docs/sub");
        assertThat(shared.resolve("docs/sub")).isDirectory();
        write(sftp, "/docs/b.txt", "bee");
        assertThatThrownBy(() -> sftp.rename("/docs/a.txt", "/docs/b.txt")).as("no silent replace").isInstanceOf(IOException.class);
        sftp.rename("/docs/a.txt", "/docs/b.txt", CopyMode.Overwrite);
        assertThat(shared.resolve("docs/b.txt")).hasContent("hello");
        sftp.rename("/docs/b.txt", "/docs/sub/c.txt");
        sftp.remove("/docs/sub/c.txt");
        sftp.rmdir("/docs/sub");
        assertThat(docs()).isEmpty();
    }

    @Test void permissionsAndTimesMayChangeOnAWritableShare() throws Exception {
        if (!Files.getFileStore(shared).supportsFileAttributeView("posix")) abort("no POSIX permissions here");
        var sftp = start(true);
        sftp.setStat("/docs/a.txt", new SftpClient.Attributes().perms(0600));
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(shared.resolve("docs/a.txt")))).isEqualTo("rw-------");
    }

    @Test void keysWorkForTheirUserAndKeysOnlyServersForAnyUser() throws Exception {
        KeyPair key = Clients.newKey(), stranger = Clients.newKey();
        String line = PublicKeyEntry.toString(key.getPublic());
        engine(DUSTIN, false, List.of(line)).start();
        assertThat(read(Clients.sftp(clients.key(port, "dustin", key)), "/docs/a.txt")).isEqualTo("hello");
        assertThatThrownBy(() -> clients.key(port, "mallory", key)).as("a password server's keys are for its user").isInstanceOf(SshException.class);
        assertThatThrownBy(() -> clients.key(port, "dustin", stranger)).isInstanceOf(SshException.class);
        engine.stop();
        engine(ResolvedLogin.anonymous(), false, List.of(line)).start();
        assertThat(read(Clients.sftp(clients.key(port, "anyone", key)), "/docs/a.txt")).isEqualTo("hello");
        assertThatThrownBy(() -> clients.password(port, "anyone", "pw")).as("keys only").isInstanceOf(SshException.class);
    }

    @Test void aWrongPasswordIsThrottled() throws Exception {
        engine(DUSTIN, false, List.of()).start();
        assertThatThrownBy(() -> clients.password(port, "dustin", "bad")).isInstanceOf(SshException.class);
        assertThatThrownBy(() -> clients.password(port, "dustin", "pw")).as("too soon after a failure").isInstanceOf(SshException.class);
        clock.advance(Duration.ofSeconds(2));
        assertThat(clients.password(port, "dustin", "pw").isAuthenticated()).isTrue();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginFailed).hasSize(1);
    }

    @Test void onlyTheSftpSubsystemIsServed() throws Exception {
        engine(DUSTIN, false, List.of()).start();
        ClientSession session = clients.password(port, "dustin", "pw");
        try (ChannelExec exec = session.createExecChannel("echo hi")) {
            exec.open().verify(Duration.ofSeconds(5));
            assertThat(exec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(5))).contains(ClientChannelEvent.CLOSED);
            assertThat(exec.getExitStatus()).isNull();
        }
        try (ChannelShell shell = session.createShellChannel()) {
            shell.open().verify(Duration.ofSeconds(5));
            assertThat(shell.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(5))).contains(ClientChannelEvent.CLOSED);
        }
        assertThatThrownBy(() -> session.createDirectTcpipChannel(new SshdSocketAddress("127.0.0.1", 0), new SshdSocketAddress("127.0.0.1", port))
            .open().verify(Duration.ofSeconds(5))).isInstanceOf(IOException.class);
        clients.client.setUserAuthFactoriesNameList("keyboard-interactive");
        assertThatThrownBy(() -> clients.password(port, "dustin", "pw")).as("no keyboard-interactive login").isInstanceOf(SshException.class);
    }

    @Test void aBusyPortFailsWithAClearMessageAndStopFreesThePort() throws Exception {
        engine(DUSTIN, false, List.of());
        try (var taken = new ServerSocket()) {
            taken.setReuseAddress(false);
            taken.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        }
        assertThat(engine.addresses()).isEmpty();
        engine.start();
        assertThat(engine.port()).isEqualTo(port);
        engine.stop();
        engine.stop();
        assertThat(engine.addresses()).isEmpty();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), port).close()).isInstanceOf(ConnectException.class);
    }

    @Test void anUnusableAuthorizedKeyFailsTheStart() throws Exception {
        engine(DUSTIN, false, List.of("ssh-ed25519 AAAAnotakey"));
        assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessageStartingWith("Authorized key 1 can't be used");
    }

    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    @Test void transfersAndLoginsAreReported() throws Exception {
        var sftp = start(true);
        assertThat(read(sftp, "/docs/a.txt")).isEqualTo("hello");
        write(sftp, "/docs/new.txt", "abc");
        await(() -> transferred().size() == 2, "both transfers end");
        assertThat(transferred()).extracting(ServerEvent.Transferred::path, ServerEvent.Transferred::direction,
                ServerEvent.Transferred::bytes, ServerEvent.Transferred::complete)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("/docs/a.txt", ServerEvent.Direction.DOWNLOAD, 5L, true),
                org.assertj.core.groups.Tuple.tuple("/docs/new.txt", ServerEvent.Direction.UPLOAD, 3L, true));
        assertThat(events).anyMatch(e -> e instanceof ServerEvent.Connected c && c.remote().equals("127.0.0.1"));
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginSucceeded).singleElement()
            .satisfies(e -> assertThat(((ServerEvent.LoginSucceeded) e).user()).isEqualTo("dustin"));
        var started = (ServerEvent.TransferStarted) events.stream().filter(e -> e instanceof ServerEvent.TransferStarted).findFirst().orElseThrow();
        assertThat(started.total()).as("a download knows its size").isEqualTo(5);
    }

    @Test void aPartialReadOrADroppedUploadIsIncomplete() throws Exception {
        engine(DUSTIN, true, List.of()).start();
        ClientSession session = clients.password(port, "dustin", "pw");
        var sftp = Clients.sftp(session);
        try (var handle = sftp.open("/docs/a.txt", OpenMode.Read)) { sftp.read(handle, 0, new byte[2]); }
        await(() -> transferred().size() == 1, "the partial read ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        var upload = sftp.open("/docs/b.txt", OpenMode.Write, OpenMode.Create, OpenMode.Truncate);
        sftp.write(upload, 0, "xy".getBytes(StandardCharsets.UTF_8));
        session.close(true);
        await(() -> transferred().size() == 2, "the dropped upload ends");
        assertThat(transferred().get(1).direction()).isEqualTo(ServerEvent.Direction.UPLOAD);
        assertThat(transferred().get(1).complete()).isFalse();
        assertThat(shared.resolve("docs/b.txt")).doesNotExist();
    }

    @Test void changesAreReportedAndRefusalsAreNot() throws Exception {
        var sftp = start(true);
        sftp.mkdir("/docs/sub");
        sftp.rename("/docs/a.txt", "/docs/sub/a.txt");
        sftp.remove("/docs/sub/a.txt");
        sftp.rmdir("/docs/sub");
        assertThatThrownBy(() -> sftp.rmdir("/docs/missing")).isInstanceOf(IOException.class);
        await(() -> events.stream().filter(e -> e instanceof ServerEvent.Changed).count() == 4, "four changes");
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Changed).extracting(e -> ((ServerEvent.Changed) e).description())
            .containsExactly("created /docs/sub", "renamed /docs/a.txt to /docs/sub/a.txt", "deleted /docs/sub/a.txt", "deleted /docs/sub");
    }
}
