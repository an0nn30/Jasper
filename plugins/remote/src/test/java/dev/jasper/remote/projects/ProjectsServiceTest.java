package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.sdk.files.FileLocation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SuppressWarnings("try")
class ProjectsServiceTest {
    static final ListOptions OPTIONS = new ListOptions(List.of(), 1_000, Duration.ofSeconds(30), () -> false);

    @Test void backgroundCallsNeedAnOpenConnection(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir)) {
            var opened = new ArrayList<String>();
            var service = new ProjectsService(fixture.connections, id -> id.equals(fixture.host.id()) ? Optional.of(fixture.host) : Optional.empty(),
                pane -> Optional.empty(), (file, window) -> opened.add("open " + file.path()), (folder, window) -> opened.add("terminal " + folder.path()),
                fixture.background);
            UUID id = fixture.host.id();
            Path app = Files.createDirectories(fixture.home.resolve("src/app"));
            Files.createDirectories(app.resolve(".git"));
            Files.writeString(app.resolve("README.md"), "");
            assertThat(fixture.onUi(() -> service.connected(id))).isFalse();
            var refused = fixture.onUi(() -> service.exists(id, List.of(app.toString()), Set.of(".git")).toCompletableFuture());
            assertThatThrownBy(() -> refused.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(NotConnectedException.class);
            var unlisted = fixture.onUi(() -> service.list(id, app.toString(), OPTIONS, Optional.empty()).toCompletableFuture());
            assertThatThrownBy(() -> unlisted.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(NotConnectedException.class);
            try (var held = fixture.lease()) {
                assertThat(fixture.onUi(() -> service.connected(id))).isTrue();
                var found = fixture.onUi(() -> service.exists(id, List.of(app.toString(), fixture.home.toString()), Set.of(".git")).toCompletableFuture())
                    .get(10, TimeUnit.SECONDS);
                assertThat(found).containsOnlyKeys(app.toString());
                var listing = fixture.onUi(() -> service.list(id, app.toString(), OPTIONS, Optional.empty()).toCompletableFuture())
                    .get(30, TimeUnit.SECONDS);
                assertThat(listing.files()).contains("README.md");
            }
            assertThat(service.hostName(id)).contains("build");
            assertThat(service.hostName(UUID.randomUUID())).isEmpty();
            assertThat(service.fileSourceId()).isEqualTo("dev.jasper.remote.hosts");
            assertThat(service.folder(new FileLocation.Sourced("dev.jasper.remote.hosts", id.toString(), "/srv/app")))
                .contains(new RemoteFolder(id, "build", "/srv/app"));
            assertThat(service.folder(new FileLocation.Sourced("dev.jasper.remote.hosts", id.toString(), "~"))).isEmpty();
            assertThat(service.folder(new FileLocation.Sourced("other.source", id.toString(), "/srv/app"))).isEmpty();
            service.open(id, "/srv/app/README.md", null);
            service.openTerminal(id, "/srv/app", null);
            service.open(UUID.randomUUID(), "/x", null);
            assertThat(opened).containsExactly("open /srv/app/README.md", "terminal /srv/app");
        }
    }
}
