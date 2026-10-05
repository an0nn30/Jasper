package dev.jasper.indexer.search;

import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Strategy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FileMatcherTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app"));
    static final ProjectKey LIB = ProjectKey.local(Path.of("/w/lib"));

    static ProjectIndex index(ProjectKey key, String... files) { return new ProjectIndex(key, List.of(files), Strategy.WALK, Instant.EPOCH, false); }

    static List<String> paths(List<FileMatcher.Hit> hits) { return hits.stream().map(hit -> hit.index().file(hit.file())).toList(); }

    static List<FileMatcher.Hit> search(String query, Optional<ProjectKey> current, ProjectIndex... indexes) {
        return FileMatcher.search(query, List.of(indexes), current, 50, Duration.ofSeconds(5), System::nanoTime);
    }

    @Test void tiersRankExactThenPrefixThenInitialsThenSubstringThenPath() {
        var app = index(APP, "docs/scope/notes.txt", "src/MyScope.java", "src/ScopeSpec.java", "src/scope", "src/Telescope.java",
            "src/SomeCoolPart.java");
        var hits = search("scope", Optional.empty(), app);
        assertThat(paths(hits)).containsExactly("src/scope", "src/ScopeSpec.java", "src/MyScope.java", "src/Telescope.java",
            "docs/scope/notes.txt");
        assertThat(hits).extracting(FileMatcher.Hit::tier).containsExactly(FileMatcher.EXACT, FileMatcher.PREFIX,
            FileMatcher.INITIALS, FileMatcher.SUBSTRING, FileMatcher.PATH);
        assertThat(paths(search("scp", Optional.empty(), app))).containsExactly("src/SomeCoolPart.java");
    }

    @Test void initialsFollowHumpsAndWordStarts() {
        assertThat(FileMatcher.initials("fsc", "FilesScope.java")).isTrue();
        assertThat(FileMatcher.initials("ps", "project_store.rs")).isTrue();
        assertThat(FileMatcher.initials("fsj", "FilesScope.java")).isTrue();
        assertThat(FileMatcher.initials("fx", "FilesScope.java")).isFalse();
        assertThat(FileMatcher.initials("ilscope", "FilesScope.java")).as("must start at a hump").isFalse();
    }

    @Test void everyWordMustMatchAndSlashesMatchSegmentsInOrder() {
        var app = index(APP, "plugins/indexer/ui/FilesScope.java", "plugins/editor/ui/Files.java", "ui/plugins/Other.java");
        assertThat(paths(search("files indexer", Optional.empty(), app))).containsExactly("plugins/indexer/ui/FilesScope.java");
        assertThat(paths(search("ui/Files", Optional.empty(), app)))
            .containsExactly("plugins/editor/ui/Files.java", "plugins/indexer/ui/FilesScope.java");
        assertThat(paths(search("plugins/ui/other", Optional.empty(), app))).isEmpty();
        assertThat(search("   ", Optional.empty(), app)).isEmpty();
    }

    @Test void theCurrentProjectComesFirstWithinATierThenShorterPaths() {
        var app = index(APP, "src/deep/Main.java");
        var lib = index(LIB, "Main.java", "a/Main.java");
        assertThat(search("main.java", Optional.of(APP), lib, app)).extracting(hit -> hit.index().key() + "|" + hit.index().file(hit.file()))
            .containsExactly(APP + "|src/deep/Main.java", LIB + "|Main.java", LIB + "|a/Main.java");
        assertThat(search("main.java", Optional.empty(), lib, app)).first().extracting(hit -> hit.ref().path()).isEqualTo("Main.java");
    }

    @Test void resultsStopAtTheLimit() {
        var many = index(APP, "a1.txt", "a2.txt", "a3.txt", "a4.txt");
        assertThat(FileMatcher.search("a", List.of(many), Optional.empty(), 2, Duration.ofSeconds(5), System::nanoTime))
            .extracting(hit -> hit.ref().path()).containsExactly("a1.txt", "a2.txt");
    }

    @Test void theBudgetStopsTheScanButKeepsTheCurrentProject() {
        String[] files = new String[5_000];
        for (int i = 0; i < files.length; i++) files[i] = "f" + i + ".txt";
        var other = index(LIB, files);
        var app = index(APP, "f.txt");
        var clock = new AtomicLong();
        var hits = FileMatcher.search("f", List.of(other, app), Optional.of(APP), 10_000, Duration.ofMillis(50),
            () -> clock.addAndGet(Duration.ofMillis(20).toNanos()));
        assertThat(hits).isNotEmpty().first().extracting(hit -> hit.index().key()).isEqualTo(APP);
        assertThat(hits).as("the scan of the other project was cut").hasSizeLessThan(5_001);
    }
}
