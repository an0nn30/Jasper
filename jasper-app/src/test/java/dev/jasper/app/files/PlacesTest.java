package dev.jasper.app.files;

import static dev.jasper.app.files.Places.Section.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlacesTest {
    private final Path home = Path.of("/home/me").toAbsolutePath();
    private final Location work = Location.local(Path.of("/work"));

    static ChooserSource hosts(String id, ChooserSource.Root... roots) {
        return new ChooserSource() {
            @Override public String id() { return id; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(roots); }
            @Override public Runnable onChanged(Runnable listener) { return () -> { }; }
            @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) { return new CompletableFuture<>(); }
        };
    }

    @Test void listsPlacesPinnedRecentAndEachSourcesRoots() {
        Set<Path> folders = Set.of(work.localPath(), home, home.resolve("Downloads"));
        var box = hosts("dev.x.hosts", new ChooserSource.Root("box", "Box", "me@box:22", true));
        var remote = new Location("dev.x.hosts", "box", "/srv/app");
        var places = Places.build(Optional.of(work), home, List.of(Location.local(Path.of("/pin")), remote),
            List.of(Location.local(Path.of("/r1")), new Location("dev.gone", "x", "/y")), List.of(box), folders::contains);
        assertThat(places).extracting(Places.Place::section, Places.Place::label).containsExactly(
            tuple(PLACES, "Terminal folder"), tuple(PLACES, "me"), tuple(PLACES, "Downloads"),
            tuple(PINNED, "pin"), tuple(PINNED, "app \u00B7 Box"), tuple(RECENT, "r1"), tuple(SOURCE, "Box"));
        var root = places.getLast();
        assertThat(root.heading()).isEqualTo("Remote");
        assertThat(root.location()).isEqualTo(Location.home("dev.x.hosts", "box"));
        assertThat(root.tooltip()).isEqualTo("me@box:22");
        assertThat(root.connected()).isTrue();
        try (var glyphs = new dev.jasper.ui.icons.IconRegistry()) {
            assertThat(places).extracting(Places.Place::icon).allSatisfy(id -> assertThat(glyphs.names()).as("named kit glyph").contains(id));
        }
    }

    @Test void aMissingTerminalFolderIsLeftOutAndARemoteOneNeedsItsSource() {
        assertThat(Places.build(Optional.of(work), home, List.of(), List.of(), List.of(), Set.of(home)::contains))
            .extracting(Places.Place::label).containsExactly("me");
        var remote = new Location("dev.x.hosts", "box", "/srv");
        assertThat(Places.build(Optional.of(remote), home, List.of(), List.of(), List.of(), Set.of(home)::contains))
            .extracting(Places.Place::label).containsExactly("me");
        var box = hosts("dev.x.hosts", new ChooserSource.Root("box", "Box", "", false));
        assertThat(Places.build(Optional.of(remote), home, List.of(), List.of(), List.of(box), Set.of(home)::contains))
            .extracting(Places.Place::label).containsExactly("Terminal folder", "me", "Box");
    }

    @Test void aRootIsLabelledWithItsWholePath() {
        Path root = Path.of("/").toAbsolutePath().getRoot();
        assertThat(Places.label(root)).isEqualTo(root.toString());
    }
}
