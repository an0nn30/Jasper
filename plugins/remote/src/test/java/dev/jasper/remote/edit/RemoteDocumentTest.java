package dev.jasper.remote.edit;

import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.EndpointFactory;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.sftp.SftpFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class RemoteDocumentTest {
    @TempDir Path temp;
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final List<TestEndpoint> opened = new CopyOnWriteArrayList<>();
    final EditChannels channels = EditChannelsTest.channels((identity, owner) -> {
        var endpoint = new TestEndpoint(new LocalEndpoint());
        opened.add(endpoint);
        return CompletableFuture.completedFuture(endpoint);
    }, identity -> true, background, ui);
    final ConnectionIdentity prod = EditChannelsTest.identity("prod");

    @AfterEach void stop() throws Exception {
        ui.submit(channels::close).get(10, TimeUnit.SECONDS);
        ui.shutdownNow(); background.shutdownNow();
    }

    RemoteDocument document(Path file) { return new RemoteDocument(prod, file.toString(), channels, Optional::empty); }
    <T> T await(Callable<CompletableFuture<T>> call) throws Exception { return ui.submit(call).get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS); }

    @Test void itIsNamedByItsConnectionAndPath() {
        var document = new RemoteDocument(prod, "/etc/nginx/nginx.conf", channels, Optional::empty);
        var hop = prod.hops().getFirst();
        assertThat(document.key()).isEqualTo("remote:" + hop.host().id() + "/deploy:/etc/nginx/nginx.conf")
            .isEqualTo(RemoteDocument.key(prod, "/etc/nginx/nginx.conf"));
        assertThat(document.name()).isEqualTo("nginx.conf");
        assertThat(document.location()).isEqualTo("deploy@prod:/etc/nginx/nginx.conf");
        assertThat(document.host()).contains("prod");
    }

    @Test void aKeyParsesBackToItsRouteAndPath() {
        var target = RemoteHost.create("prod", "10.0.0.5", 22, "deploy", Auth.AGENT, "", Optional.empty());
        var jump = RemoteHost.create("bastion", "10.0.0.1", 22, "ops", Auth.AGENT, "", Optional.empty());
        var identity = new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(target, "deploy"), new ConnectionIdentity.Hop(jump, "ops")));
        for (String path : List.of("/", "/etc/nginx/nginx.conf", "/srv/a:b,c d/r\u00e9sum\u00e9.txt")) {
            assertThat(RemoteDocument.parseKey(RemoteDocument.key(identity, path))).contains(new RemoteDocument.ParsedKey(
                List.of(new RemoteDocument.KeyHop(target.id(), "deploy"), new RemoteDocument.KeyHop(jump.id(), "ops")), path));
        }
    }

    @Test void malformedKeysDoNotParse() {
        UUID id = UUID.randomUUID();
        assertThat(Stream.of("local:/etc/hosts", "remote:", "remote:" + id + "/deploy", "remote:" + id + "/deploy:relative",
                "remote:not-a-uuid/deploy:/etc/hosts", "remote:" + id + ":/etc/hosts", "remote:" + id + "/deploy,:/etc/hosts",
                "remote:" + id + "/a/b:/etc/hosts")
            .map(RemoteDocument::parseKey)).allMatch(Optional::isEmpty);
    }

    @Test void itReadsTheFileAtTheRevisionItReports() throws Exception {
        Path file = Files.writeString(temp.toRealPath().resolve("app.toml"), "a = 1\n");
        var document = document(file);
        var content = await(() -> document.read(1024));
        assertThat(new String(content.bytes(), StandardCharsets.UTF_8)).isEqualTo("a = 1\n");
        assertThat(content.revision()).isEqualTo(new Revision(Files.getLastModifiedTime(file).toMillis(), 6));
        assertThat(await(document::revision)).contains(content.revision());
    }

    @Test void aFileOverTheLimitIsRefusedBeforeAnythingIsRead() throws Exception {
        Path file = Files.writeString(temp.toRealPath().resolve("big.log"), "x".repeat(20));
        var document = document(file);
        assertThatThrownBy(() -> await(() -> document.read(10))).hasCauseInstanceOf(TooLargeException.class);
        assertThat(opened).allSatisfy(endpoint -> assertThat(endpoint.calls).noneMatch(call -> call.startsWith("read ")));
    }

    @Test void aFolderIsRefusedAndAMissingFileHasNoRevision() throws Exception {
        Path directory = Files.createDirectory(temp.toRealPath().resolve("conf.d"));
        assertThatThrownBy(() -> await(() -> document(directory).read(1024))).hasRootCauseMessage(directory + ": Is a folder");
        var missing = document(temp.toRealPath().resolve("gone.txt"));
        assertThat(await(missing::revision)).isEmpty();
        assertThatThrownBy(() -> await(() -> missing.read(1024))).hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test void writingFollowsTheContractAndReturnsTheNewRevision() throws Exception {
        Path file = Files.writeString(temp.toRealPath().resolve("app.toml"), "a = 1\n");
        var document = document(file);
        var revision = await(() -> document.write("a = 22\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(Files.readString(file)).isEqualTo("a = 22\n");
        assertThat(revision).isEqualTo(new Revision(Files.getLastModifiedTime(file).toMillis(), 7));
        assertThat(opened).anySatisfy(endpoint -> assertThat(endpoint.calls).anyMatch(call -> call.startsWith("publish ")));
    }

    @Test void cancellingAQueuedWriteWithdrawsIt() throws Exception {
        Path file = Files.writeString(temp.toRealPath().resolve("app.toml"), "a = 1\n");
        var document = document(file);
        var gate = new CountDownLatch(1);
        try {
            var ahead = ui.submit(() -> channels.submit(prod, Optional::empty, endpoint -> { gate.await(); return null; })).get(10, TimeUnit.SECONDS);
            var written = ui.submit(() -> document.write("a = 2\n".getBytes(StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
            assertThat(ui.submit(() -> written.cancel(true)).get(10, TimeUnit.SECONDS)).isTrue();
            gate.countDown();
            ahead.get(10, TimeUnit.SECONDS);
            assertThat(await(document::revision)).as("queued behind the withdrawn write").isPresent();
            assertThat(Files.readString(file)).isEqualTo("a = 1\n");
            assertThat(opened).allSatisfy(endpoint -> assertThat(endpoint.calls).noneMatch(call -> call.startsWith("create ")));
        } finally {
            gate.countDown();
        }
    }

    @Test void overSftpItReadsAndWritesThroughTheQueue() throws Exception {
        try (var fixture = new SftpFixture(temp)) {
            Path file = Files.writeString(fixture.files.resolve("site.yaml"), "port: 80\n");
            var endpoints = new EndpointFactory(fixture.connections, fixture.background, fixture.ui, () -> Duration.ofSeconds(5));
            var sftp = EditChannelsTest.channels((identity, owner) -> endpoints.open(Optional.of(identity), owner.orElse(null), status -> {}),
                fixture.connections::authenticated, fixture.background, fixture.ui);
            try (var held = fixture.lease()) {
                var document = new RemoteDocument(held.identity(), "/site.yaml", sftp, Optional::empty);
                var content = fixture.onUi(() -> document.read(1024)).get(10, TimeUnit.SECONDS);
                assertThat(new String(content.bytes(), StandardCharsets.UTF_8)).isEqualTo("port: 80\n");
                var revision = fixture.onUi(() -> document.write("port: 8080\n".getBytes(StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
                assertThat(Files.readString(file)).isEqualTo("port: 8080\n");
                assertThat(revision.size()).isEqualTo(11);
                assertThat(fixture.onUi(document::revision).get(10, TimeUnit.SECONDS)).contains(revision);
            } finally {
                fixture.onUi(() -> { sftp.close(); return null; });
            }
        }
    }

    /**
     * Final review F1: a failed write over SFTP aborts the endpoint, as a full disk does (OpenSSH answers the write
     * with SSH_FX_FAILURE), so SafeWrite cannot remove its sibling; a cleanup queued behind the write removes it.
     */
    @Test void aSaveThatFailsOverSftpLeavesNoSiblingBehind() throws Exception {
        var refused = new CopyOnWriteArrayList<String>();
        try (var fixture = new SftpFixture(temp, refusingSiblingWrites(refused))) {
            Path file = Files.writeString(fixture.files.resolve("site.yaml"), "port: 80\n");
            var endpoints = new EndpointFactory(fixture.connections, fixture.background, fixture.ui, () -> Duration.ofSeconds(5));
            var sftp = EditChannelsTest.channels((identity, owner) -> endpoints.open(Optional.of(identity), owner.orElse(null), status -> {}),
                fixture.connections::authenticated, fixture.background, fixture.ui);
            try (var held = fixture.lease()) {
                var document = new RemoteDocument(held.identity(), "/site.yaml", sftp, Optional::empty);
                var written = fixture.onUi(() -> document.write("port: 8080\n".getBytes(StandardCharsets.UTF_8)));
                assertThatThrownBy(() -> written.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).cause()
                    .isInstanceOfSatisfying(SftpException.class, failure -> assertThat(failure.getStatus()).isEqualTo(SftpConstants.SSH_FX_FAILURE));
                assertThat(refused).as("the write reached the sibling").singleElement().asString().matches("\\.site\\.yaml\\.jasper-[0-9a-f]+\\.tmp");
                // The cleanup was queued before the failure was reported, so this operation runs after it.
                assertThat(fixture.onUi(document::revision).get(10, TimeUnit.SECONDS)).isPresent();
                assertThat(SafeWriteTest.names(fixture.files)).as("the sibling is removed").containsExactly("site.yaml");
                assertThat(Files.readString(file)).isEqualTo("port: 80\n");
            } finally {
                fixture.onUi(() -> { sftp.close(); return null; });
            }
        }
    }

    /** Fails every write to a file named like a sibling, as a full disk or an exceeded quota would. */
    static SftpSubsystemFactory refusingSiblingWrites(List<String> refused) {
        return new SftpSubsystemFactory() {
            @Override public Command createSubsystem(ChannelSession channel) {
                return new SftpSubsystem(channel, this) {
                    @Override protected void doWrite(int id, String handle, long offset, int length, byte[] data, int off, int remaining)
                            throws IOException {
                        Handle open = handles.get(handle);
                        String name = open == null ? "" : open.getFile().getFileName().toString();
                        if (name.contains(".jasper-")) {
                            refused.add(name);
                            throw new IOException("No space left on device");
                        }
                        super.doWrite(id, handle, offset, length, data, off, remaining);
                    }
                };
            }
        };
    }
}
