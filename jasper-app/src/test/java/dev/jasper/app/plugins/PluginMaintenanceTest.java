package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PluginMaintenanceTest {
    @TempDir Path root;

    private Path user() { return root.resolve("plugins"); }
    private PluginStateStore store() {
        return new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2));
    }

    private static void plugin(Path directory, String id, String version) throws Exception {
        PluginJars.build(directory, "main.jar", PluginJars.descriptor(id, version, "fix.Main"), Map.of(), List.of());
    }

    private List<PluginCandidate> installed() {
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(user(), PluginCandidate.Origin.USER, problems);
        assertThat(problems).isEmpty();
        return found;
    }

    @Test void aRemovalDeletesThePluginItsPendingInstallAndItsEntry() throws Exception {
        plugin(user().resolve("dev.example.gone"), "dev.example.gone", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.gone"), "dev.example.gone", "2.0.0");
        plugin(user().resolve("dev.example.kept"), "dev.example.kept", "1.0.0");
        store().transact(PluginStateStore.consenting("dev.example.kept", Set.of()));
        store().transact(PluginStateStore.removing("dev.example.gone", true));
        PluginMaintenance.apply(user(), store());
        assertThat(installed()).extracting(PluginCandidate::id).containsExactly("dev.example.kept");
        assertThat(user().resolve(".pending").resolve("dev.example.gone")).doesNotExist();
        assertThat(store().read()).containsOnlyKeys("dev.example.kept");
    }

    @Test void aPendingInstallReplacesTheInstalledVersionAndKeepsItsConsent() throws Exception {
        plugin(user().resolve("dev.example.tool"), "dev.example.tool", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.tool"), "dev.example.tool", "2.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.fresh"), "dev.example.fresh", "0.1.0");
        store().transact(PluginStateStore.consenting("dev.example.tool", Set.of("terminal.observe")));
        PluginMaintenance.apply(user(), store());
        assertThat(installed()).extracting(candidate -> candidate.id() + " " + candidate.descriptor().version())
            .containsExactly("dev.example.fresh 0.1.0", "dev.example.tool 2.0.0");
        try (var left = Files.list(user().resolve(".pending"))) { assertThat(left).isEmpty(); }
        try (var all = Files.list(user())) {
            assertThat(all.map(path -> path.getFileName().toString()).filter(name -> name.startsWith(".trash-"))).isEmpty();
        }
        assertThat(store().read().get("dev.example.tool").consented()).containsExactly("terminal.observe");
    }

    @Test void nothingPendingWritesNothing() {
        PluginMaintenance.apply(user(), store());
        assertThat(root.resolve("plugins.toml")).doesNotExist();
        assertThat(root.resolve("plugins.lock")).doesNotExist();
    }

    @Test void anUnusablePendingInstallIsDiscardedAndTheInstalledPluginSurvives() throws Exception {
        plugin(user().resolve("dev.example.tool"), "dev.example.tool", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.tool"), "dev.example.other", "2.0.0");
        PluginMaintenance.apply(user(), store());
        assertThat(installed()).extracting(candidate -> candidate.descriptor().version().toString()).containsExactly("1.0.0");
        assertThat(user().resolve(".pending").resolve("dev.example.tool")).doesNotExist();
    }

    @Test void abandonedStagingAndTrashAreSweptButAFreshStagingDirectoryIsInUse() throws Exception {
        Path old = Files.createDirectories(user().resolve(".staging-old"));
        Files.writeString(old.resolve("a.jar"), "x");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        Path fresh = Files.createDirectories(user().resolve(".staging-fresh"));
        Path trash = Files.createDirectories(user().resolve(".trash-dev.example.tool-1"));
        Files.writeString(trash.resolve("a.jar"), "x");
        PluginRuntime.maintain(user(), root.resolve("plugins.toml"), root.resolve("plugins.lock"));
        assertThat(old).doesNotExist();
        assertThat(trash).doesNotExist();
        assertThat(fresh).exists();
    }
}
