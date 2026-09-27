package dev.jasper.remote.edit;

import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.EndpointFactory;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.sftp.SftpFixture;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    final EditChannels channels = new EditChannels((identity, owner) -> {
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

    @Test void overSftpItReadsAndWritesThroughTheQueue() throws Exception {
        try (var fixture = new SftpFixture(temp)) {
            Path file = Files.writeString(fixture.files.resolve("site.yaml"), "port: 80\n");
            var endpoints = new EndpointFactory(fixture.connections, fixture.background, fixture.ui, () -> Duration.ofSeconds(5));
            var sftp = new EditChannels((identity, owner) -> endpoints.open(Optional.of(identity), owner.orElse(null), status -> {}),
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
}
