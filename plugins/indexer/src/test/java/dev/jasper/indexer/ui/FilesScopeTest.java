package dev.jasper.indexer.ui;

import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FilesScopeTest {
    static final ProjectKey APP = ProjectKey.local(Path.of("/w/app").toAbsolutePath());
    static final ProjectKey LIB = ProjectKey.local(Path.of("/w/lib").toAbsolutePath());
    @TempDir Path root;

    final Projects projects = new Projects();
    final List<String> opened = new ArrayList<>();
    final List<String> terminals = new ArrayList<>();
    final List<String> copied = new ArrayList<>();
    final AtomicReference<Optional<ProjectKey>> current = new AtomicReference<>(Optional.empty());
    final AtomicReference<Optional<String>> blocked = new AtomicReference<>(Optional.empty());
    final AtomicReference<dev.jasper.sdk.palette.PaletteQuery> lastQuery = new AtomicReference<>();

    @BeforeEach void index() {
        projects.loaded(new Project(APP, "app", false), Optional.of(new ProjectIndex(APP,
            List.of("src/FilesScope.java", "README.md"), Strategy.GIT, Instant.EPOCH, false)));
        projects.loaded(new Project(LIB, "lib", false), Optional.of(new ProjectIndex(LIB,
            List.of("FilesScope.java"), Strategy.GIT, Instant.EPOCH, false)));
    }

    FilesScope scope() {
        return new FilesScope(projects, new FilesScope.Hooks(query -> { lastQuery.set(query); return current.get(); },
            key -> blocked.get(), key -> key.isLocal(),
            key -> Optional.empty(), key -> true, (ref, window) -> opened.add(ref.path()), (ref, window) -> terminals.add(ref.path()),
            ref -> ref.localPath().toString(), copied::add), "dev.jasper.indexer.goto", null);
    }

    FakePluginHost host(FilesScope scope) {
        var host = new FakePluginHost(root);
        host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> {
            context.actions().register(dev.jasper.sdk.ui.ActionSpec.of("dev.jasper.indexer.goto", "Go to File…"), invoked -> { });
            context.palette().register(scope);
        });
        return host;
    }

    static String id(ProjectKey key, String path) { return FilesScope.rowId(new FileRef(key, path)); }

    @Test void declaresTheScope() {
        try (var host = host(scope())) {
            assertThat(host.scopes()).containsExactly("dev.jasper.indexer.files|Files|open,copy-path,terminal");
            assertThat(host.scopePresentation(FilesScope.ID)).as("spec 14.6").isEqualTo("AMBER|file|STANDARD,STANDARD,STANDARD");
            assertThat(host.scopesInAll()).contains(FilesScope.ID);
        }
    }

    @Test void rowsNameTheFileItsPathAndItsProjectWithTheCurrentProjectFirst() {
        var scope = scope();
        try (var host = host(scope)) {
            UUID window = host.addTerminalWindow();
            current.set(Optional.of(LIB));
            assertThat(host.searchScope(FilesScope.ID, "fsc", window, null)).containsExactly(
                id(LIB, "FilesScope.java") + "|FilesScope.java|true", id(APP, "src/FilesScope.java") + "|FilesScope.java|true");
            var results = scope.search("fsc", lastQuery.get());
            assertThat(results.rows()).extracting(row -> row.detail().orElse("") + " / " + row.tag().orElse(""))
                .containsExactly("FilesScope.java / lib", "src/FilesScope.java / app");
        }
    }

    @Test void enterOpensAndRemembersCopyCopiesAndShiftEnterOpensATerminal() {
        try (var host = host(scope())) {
            UUID window = host.addTerminalWindow();
            current.set(Optional.of(APP));
            assertThat(host.searchScope(FilesScope.ID, "", window, null)).as("nothing opened yet").isEmpty();
            host.searchScope(FilesScope.ID, "readme", window, null);
            String readme = id(APP, "README.md");
            host.executeInScope(FilesScope.ID, readme, "open", window, null);
            host.executeInScope(FilesScope.ID, readme, "copy-path", window, null);
            host.executeInScope(FilesScope.ID, readme, "terminal", window, null);
            assertThat(opened).containsExactly("README.md");
            assertThat(copied).containsExactly(Path.of("/w/app/README.md").toAbsolutePath().toString());
            assertThat(terminals).containsExactly("README.md");
            assertThat(host.searchScope(FilesScope.ID, "", window, null)).containsExactly(readme + "|README.md|true");
            current.set(Optional.of(LIB));
            assertThat(host.searchScope(FilesScope.ID, "", window, null)).as("recent files of the current project only").isEmpty();
        }
    }

    @Test void aBlockedOpenDimsTheRowButCopyStillWorks() {
        try (var host = host(scope())) {
            UUID window = host.addTerminalWindow();
            blocked.set(Optional.of("Install the Editor plugin to open files"));
            String readme = id(APP, "README.md");
            assertThat(host.searchScope(FilesScope.ID, "readme", window, null)).containsExactly(readme + "|README.md|false");
            assertThat(host.availableInScope(FilesScope.ID, readme, "open", window, null)).isFalse();
            assertThat(host.availableInScope(FilesScope.ID, readme, "copy-path", window, null)).isTrue();
            assertThat(host.availableInScope(FilesScope.ID, readme, "terminal", window, null)).isTrue();
        }
    }

    @Test void indexChangesReachThePalette() {
        var scope = scope();
        var heard = new ArrayList<String>();
        scope.onChanged(() -> heard.add("changed"));
        projects.remove(LIB);
        assertThat(heard).containsExactly("changed");
    }
}
