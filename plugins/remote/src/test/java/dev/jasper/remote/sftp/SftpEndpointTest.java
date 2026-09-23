package dev.jasper.remote.sftp;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.client.*;
import dev.jasper.remote.hosts.*;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.vault.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.sftp.server.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SftpEndpointTest {
    @TempDir Path root;
    final class Fixture implements AutoCloseable {
        final LoopbackServer server = new LoopbackServer();
        final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
        final ExecutorService ui = Executors.newSingleThreadExecutor();
        final Connections connections;
        RemoteHost host;
        final Map<UUID, RemoteHost> hosts = new HashMap<>();
        Fixture(SftpSubsystemFactory factory) throws Exception {
            server.server.setFileSystemFactory(new VirtualFileSystemFactory(root.toRealPath()));
            server.server.setSubsystemFactories(List.of(factory));
            host = RemoteHost.create("files", "127.0.0.1", server.port(), "deploy", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
            hosts.put(host.id(), host);
            var settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO, false, false);
            connections = new Connections(() -> settings, new KnownHosts(root.resolve("trust"), Optional.empty()), Optional.empty(),
                id -> Optional.ofNullable(hosts.get(id)), Optional.of(id -> CompletableFuture.completedFuture(Optional.of(
                    new Credential(id, "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null)))),
                question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.ONCE), background, ui, (delay, work) -> () -> {});
        }
        SessionLease lease() throws Exception { return ui.submit(() -> connections.lease(connections.identity(host.id()), null, s -> {})).get().get(10,TimeUnit.SECONDS); }
        SftpEndpoint endpoint(Duration timeout) throws Exception { return new SftpEndpoint(lease(), timeout); }
        @Override public void close() throws Exception { ui.submit(connections::close).get(); background.close(); ui.close(); server.close(); }
    }
    @Test void sftpV3ContractAndSubsystemCloseKeepsSharedSessionAlive() throws Exception {
        try (var fixture = new Fixture(new SftpSubsystemFactory()); var surviving = fixture.lease()) {
            try (var endpoint = fixture.endpoint(Duration.ofSeconds(3))) { FileEndpointTest.contract(endpoint, "/"); }
            assertThat(surviving.session().isOpen()).isTrue();
        }
    }
    @Test void sftpUsesProxyJumpAndLeavesItsShellAlive() throws Exception {
        try (var jump = new LoopbackServer(); var fixture = new Fixture(new SftpSubsystemFactory())) {
            var bastion = RemoteHost.create("jump", "127.0.0.1", jump.port(), "deploy", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
            fixture.hosts.put(bastion.id(), bastion);
            var original = fixture.host;
            fixture.host = original.withEdited(original.name(), original.hostname(), original.port(), original.username(), original.auth(), "", Optional.of(bastion.id()));
            fixture.hosts.put(original.id(), fixture.host);
            var shell = fixture.ui.submit(() -> fixture.connections.shell(original.id(), 80, 24, status -> {})).get().get(10,TimeUnit.SECONDS);
            try (var endpoint = fixture.endpoint(Duration.ofSeconds(3))) {
                try (var out = endpoint.write("/via-jump",0,true)) { out.write(57); }
                assertThat(Files.readAllBytes(root.resolve("via-jump"))).containsExactly(57);
                assertThat(fixture.ui.submit(() -> fixture.connections.connected(bastion.id())).get()).isTrue();
            }
            shell.connection().input().write('a'); shell.connection().input().flush();
            String ready = fixture.background.submit(() -> new String(shell.connection().output().readNBytes("READY xterm-256color 80x24\r\na".length()), java.nio.charset.StandardCharsets.UTF_8)).get(5,TimeUnit.SECONDS);
            assertThat(ready).startsWith("READY").endsWith("a");
            shell.connection().close().run();
        }
    }

    @Test void stalledWriterStopsAtItsRequestBudgetAndAbortDoesNotCloseSibling() throws Exception {
        var writes = new CountDownLatch(16);
        var factory = withholdingWrites(writes);
        try (var fixture = new Fixture(factory); var sibling = fixture.lease(); var endpoint = fixture.endpoint(Duration.ofSeconds(30))) {
            var out = endpoint.write("/bounded", 0, true);
            var copy = fixture.background.submit(() -> { out.write(new byte[4 * 1024 * 1024]); return null; });
            assertThat(writes.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(copy.isDone()).isFalse();
            assertThat(Files.size(root.resolve("bounded"))).isEqualTo(2L * 1024 * 1024);
            out.abort();
            assertThatThrownBy(() -> copy.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IOException.class);
            assertThat(sibling.session().isOpen()).isTrue();
        }
    }

    private static SftpSubsystemFactory withholdingWrites(CountDownLatch written) {
        return new SftpSubsystemFactory() {
            @Override public Command createSubsystem(ChannelSession channel) {
                return new SftpSubsystem(channel, this) {
                    final Set<Integer> withheld = new HashSet<>();
                    @Override protected void doWrite(int id, String handle, long offset, int length, byte[] data, int off, int remaining) throws IOException {
                        super.doWrite(id, handle, offset, length, data, off, remaining); withheld.add(id); written.countDown();
                    }
                    @Override protected void sendStatus(Buffer buffer, int id, int status, String message, String language) throws IOException {
                        if (!withheld.remove(id)) super.sendStatus(buffer, id, status, message, language);
                    }
                };
            }
        };
    }

    @Test void boundedPipelinesHandleShortReadsAndLongOffsets() throws Exception {
        try (var fixture = new Fixture(new SftpSubsystemFactory()); var endpoint = fixture.endpoint(Duration.ofSeconds(3))) {
            org.apache.sshd.sftp.SftpModuleProperties.MAX_READDATA_PACKET_LENGTH.set(fixture.server.server, 32 * 1024);
            byte[] bytes = new byte[5 * 1024 * 1024 + 17]; new Random(42).nextBytes(bytes);
            try (var out = endpoint.write("/binary", 0, true)) { out.write(bytes); assertThat(out.checkpoint()).isEqualTo(bytes.length); }
            try (var in = endpoint.read("/binary", 13)) { assertThat(in.readAllBytes()).isEqualTo(Arrays.copyOfRange(bytes,13,bytes.length)); }
            long offset = 3L * 1024 * 1024 * 1024;
            try (var out = endpoint.write("/sparse", offset, true)) { out.write(41); assertThat(out.checkpoint()).isEqualTo(offset + 1); }
            try (var in = endpoint.read("/sparse", offset)) { assertThat(in.read()).isEqualTo(41); assertThat(in.read()).isEqualTo(-1); }
        }
    }
    @Test void unsupportedAtomicReplaceLeavesBothFilesUnchanged() throws Exception {
        try (var fixture = new Fixture(new SftpSubsystemFactory())) {
            org.apache.sshd.sftp.SftpModuleProperties.OPENSSH_EXTENSIONS.set(fixture.server.server, ",");
            try (var endpoint = fixture.endpoint(Duration.ofSeconds(3))) {
                try (var out = endpoint.write("/original", 0, true)) { out.write(1); }
                try (var out = endpoint.write("/temp", 0, true)) { out.write(2); }
                assertThatThrownBy(() -> endpoint.publish("/temp", "/original", true)).isInstanceOf(IOException.class).hasMessageContaining("atomic replacement");
                assertThat(Files.readAllBytes(root.resolve("original"))).containsExactly(1);
                assertThat(Files.readAllBytes(root.resolve("temp"))).containsExactly(2);
            }
        }
    }

    @Test void unacknowledgedWritesNeverPassCheckpointEvenWhenTheBytesReachedDisk() throws Exception {
        var written = new CountDownLatch(1);
        var factory = new SftpSubsystemFactory() {
            @Override public Command createSubsystem(ChannelSession channel) {
                return new SftpSubsystem(channel, this) {
                    final Set<Integer> withheld = new HashSet<>();
                    @Override protected void doWrite(int id, String handle, long offset, int length, byte[] data, int off, int remaining) throws IOException {
                        super.doWrite(id, handle, offset, length, data, off, remaining); withheld.add(id); written.countDown();
                    }
                    @Override protected void sendStatus(Buffer buffer, int id, int status, String message, String language) throws IOException {
                        if (!withheld.remove(id)) super.sendStatus(buffer, id, status, message, language);
                    }
                };
            }
        };
        try (var fixture = new Fixture(factory); var sibling = fixture.lease(); var endpoint = fixture.endpoint(Duration.ofMillis(300))) {
            var out = endpoint.write("/partial", 0, true); out.write(new byte[]{1,2,3});
            assertThat(written.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(Files.readAllBytes(root.resolve("partial"))).containsExactly(1,2,3);
            assertThatThrownBy(out::checkpoint).isInstanceOf(IOException.class).hasMessageContaining("acknowledgement");
            assertThatThrownBy(out::close).isInstanceOf(IOException.class);
            assertThat(sibling.session().isOpen()).isTrue();
        }
    }
}
