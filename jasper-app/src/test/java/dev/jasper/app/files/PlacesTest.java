package dev.jasper.app.files;

import static dev.jasper.app.files.Places.Section.*;
import dev.jasper.app.platform.AppIcons;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlacesTest {
    private final Path home = Path.of("/home/me").toAbsolutePath();
    private final Path work = Path.of("/work").toAbsolutePath();

    @Test void listsTheTerminalFolderStandardFoldersPinnedAndRecent() {
        Set<Path> folders = Set.of(work, home, home.resolve("Downloads"));
        var places = Places.build(Optional.of(work), home, List.of(Path.of("/pin").toAbsolutePath()),
            List.of(Path.of("/r1").toAbsolutePath()), folders::contains);
        assertThat(places).extracting(Places.Place::section, Places.Place::label).containsExactly(
            tuple(PLACES, "Terminal folder"), tuple(PLACES, "me"), tuple(PLACES, "Downloads"), tuple(PINNED, "pin"), tuple(RECENT, "r1"));
        assertThat(places.getFirst().folder()).isEqualTo(work);
        assertThat(places).extracting(Places.Place::icon).allSatisfy(id -> assertThat(AppIcons.bundledIds()).contains(id));
    }

    @Test void aMissingTerminalFolderIsLeftOut() {
        var places = Places.build(Optional.of(work), home, List.of(), List.of(), Set.of(home)::contains);
        assertThat(places).extracting(Places.Place::label).containsExactly("me");
    }

    @Test void aRootIsLabelledWithItsWholePath() {
        Path root = Path.of("/").toAbsolutePath().getRoot();
        assertThat(Places.label(root)).isEqualTo(root.toString());
    }
}
