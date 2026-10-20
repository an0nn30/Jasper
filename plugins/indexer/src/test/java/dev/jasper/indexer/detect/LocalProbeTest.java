package dev.jasper.indexer.detect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LocalProbeTest {
    static final Markers MARKERS = Markers.parse(Markers.DEFAULTS);
    @TempDir Path home;

    @Test void findsTheRepositoryRootFromASubfolder() throws IOException {
        Path app = Files.createDirectories(home.resolve("src/app"));
        Files.createDirectory(app.resolve(".git"));
        Path lib = Files.createDirectories(app.resolve("crates/lib"));
        Files.writeString(lib.resolve("Cargo.toml"), "");
        var found = LocalProbe.detect(lib, home, MARKERS);
        assertThat(found).map(Detection::root).contains(app);
        assertThat(found.orElseThrow().marker().kind()).isEqualTo("Git repository");
    }

    @Test void aGlobMarkerIsFoundByListingTheFolder() throws IOException {
        Path app = Files.createDirectories(home.resolve("src/Shop"));
        Files.writeString(app.resolve("Shop.sln"), "");
        assertThat(LocalProbe.present(app, MARKERS)).containsExactly("Shop.sln");
        assertThat(LocalProbe.detect(app, home, MARKERS)).map(found -> found.marker().kind()).contains("Visual Studio solution");
    }

    @Test void theHomeFolderIsNeverAProjectEvenWithMarkers() throws IOException {
        Files.createDirectory(home.resolve(".git"));
        Path notes = Files.createDirectories(home.resolve("notes"));
        assertThat(LocalProbe.detect(notes, home, MARKERS)).isEmpty();
        assertThat(LocalProbe.detect(home, home, MARKERS)).isEmpty();
    }

    @Test void aMissingFolderHasNoMarkers() {
        assertThat(LocalProbe.present(home.resolve("missing"), MARKERS)).isEmpty();
    }
}
