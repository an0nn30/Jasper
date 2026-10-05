package dev.jasper.indexer.remote;

import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.indexer.testsupport.FakeRemote;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.sdk.files.FileLocation;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteBridgeTest {
    static final UUID HOST = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");
    final FakeRemote fake = new FakeRemote();
    final RemoteBridge bridge = new RemoteBridge(fake);

    { fake.names.put(HOST, "build-01"); }

    @Test void placesComeFromPanesAndTheChooser() {
        UUID pane = UUID.randomUUID();
        fake.panes.put(pane, new RemoteFolder(HOST, "build-01", "/srv/app/src"));
        assertThat(bridge.locate(pane)).contains(new RemotePlace(HOST, "build-01", "/srv/app/src"));
        assertThat(bridge.folder(new FileLocation.Sourced(bridge.fileSourceId(), HOST.toString(), "/srv/lib")))
            .contains(new RemotePlace(HOST, "build-01", "/srv/lib"));
        assertThat(bridge.hostName(HOST)).contains("build-01");
    }

    @Test void detectionAsksForEveryAncestorInOneCall() {
        fake.connectedHosts.add(HOST);
        fake.markers.put("/srv/app", Set.of(".git"));
        fake.markers.put("/srv/app/src", Set.of("Cargo.toml"));
        Optional<Detection<String>> found = bridge.detect(new RemotePlace(HOST, "build-01", "/srv/app/src"), Markers.parse(Markers.DEFAULTS))
            .toCompletableFuture().join();
        assertThat(found).map(Detection::root).contains("/srv/app");
        assertThat(found.orElseThrow().marker().kind()).isEqualTo("Git repository");
        assertThat(fake.calls).containsExactly("exists [/srv/app/src, /srv/app, /srv]");
    }

    @Test void listingMapsTheStrategyAndQuietsADisconnectedBackgroundJob() {
        var key = ProjectKey.remote(HOST, "/srv/app");
        fake.listings.put("/srv/app", new RemoteListing(List.of("a.txt"), RemoteListing.Strategy.FIND, true));
        assertThatThrownBy(() -> bridge.list(key, List.of(), 10, () -> false, Optional.empty()).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class).hasCauseInstanceOf(CancellationException.class);
        Listing listing = bridge.list(key, List.of(), 10, () -> false, Optional.of(dummyWindow())).toCompletableFuture().join();
        assertThat(listing).isEqualTo(new Listing(List.of("a.txt"), Strategy.FIND, true));
        assertThat(fake.calls).containsExactly("list /srv/app", "list /srv/app signed-in");
        assertThatThrownBy(() -> bridge.list(ProjectKey.remote(HOST, "/gone"), List.of(), 10, () -> false, Optional.empty()).toCompletableFuture().join())
            .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test void filesOpenAndTerminalsStartInTheirFolder() {
        var ref = new FileRef(ProjectKey.remote(HOST, "/srv/app"), "src/main.rs");
        bridge.open(ref, null);
        bridge.openTerminal(ref, null);
        bridge.openTerminal(new FileRef(ProjectKey.remote(HOST, "/"), "top.txt"), null);
        assertThat(fake.calls).containsExactly("open /srv/app/src/main.rs", "terminal /srv/app/src", "terminal /");
    }

    static dev.jasper.sdk.terminal.WindowHandle dummyWindow() {
        return (dev.jasper.sdk.terminal.WindowHandle) java.lang.reflect.Proxy.newProxyInstance(RemoteBridgeTest.class.getClassLoader(),
            new Class<?>[] {dev.jasper.sdk.terminal.WindowHandle.class}, (proxy, method, args) -> null);
    }
}
