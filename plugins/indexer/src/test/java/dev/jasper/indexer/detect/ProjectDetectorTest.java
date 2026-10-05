package dev.jasper.indexer.detect;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectDetectorTest {
    static final Markers MARKERS = Markers.parse(Markers.DEFAULTS);

    @Test void aStrongMarkerAnywhereUpTheTreeWinsOverANearerWeakOne() {
        Map<String, Set<String>> tree = Map.of("/w/app/crates/core", Set.of("Cargo.toml"), "/w/app", Set.of(".git", "Cargo.toml"));
        var found = ProjectDetector.root(List.of("/w/app/crates/core", "/w/app/crates", "/w/app", "/w"),
            folder -> tree.getOrDefault(folder, Set.of()), MARKERS);
        assertThat(found).contains(new Detection<>("/w/app", new Markers.Match(".git", ".git")));
    }

    @Test void withoutAStrongMarkerTheNearestWeakOneWins() {
        Map<String, Set<String>> tree = Map.of("/w/app/web", Set.of("package.json"), "/w/app", Set.of("Makefile"));
        var found = ProjectDetector.root(List.of("/w/app/web/src", "/w/app/web", "/w/app"),
            folder -> tree.getOrDefault(folder, Set.of()), MARKERS);
        assertThat(found).contains(new Detection<>("/w/app/web", new Markers.Match("package.json", "package.json")));
    }

    @Test void noMarkerMeansNoProject() {
        assertThat(ProjectDetector.root(List.of("/a/b", "/a"), folder -> Set.<String>of(), MARKERS)).isEmpty();
        assertThat(ProjectDetector.root(List.<String>of(), folder -> Set.of(".git"), MARKERS)).isEmpty();
    }

    @Test void eachFolderIsProbedOnce() {
        var probed = new ArrayList<String>();
        ProjectDetector.root(List.of("/a/b", "/a"), folder -> { probed.add(folder); return Set.of(); }, MARKERS);
        assertThat(probed).containsExactly("/a/b", "/a");
    }

    @Test void localAncestorsStopBelowHomeAndTheFileSystemRoot() {
        Path home = Path.of("/Users/me");
        assertThat(ProjectDetector.localAncestors(Path.of("/Users/me/src/app/lib"), home))
            .containsExactly(Path.of("/Users/me/src/app/lib"), Path.of("/Users/me/src/app"), Path.of("/Users/me/src"));
        assertThat(ProjectDetector.localAncestors(home, home)).isEmpty();
        assertThat(ProjectDetector.localAncestors(Path.of("/opt/tool"), home)).containsExactly(Path.of("/opt/tool"), Path.of("/opt"));
        assertThat(ProjectDetector.localAncestors(Path.of("/"), home)).isEmpty();
        assertThat(ProjectDetector.localAncestors(Path.of("/Users/me/src/../src/app"), home))
            .as("normalized first").containsExactly(Path.of("/Users/me/src/app"), Path.of("/Users/me/src"));
    }
}
