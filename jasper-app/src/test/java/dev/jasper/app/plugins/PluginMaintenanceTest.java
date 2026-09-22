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
        apply();
        assertThat(installed()).extracting(PluginCandidate::id).containsExactly("dev.example.kept");
        assertThat(user().resolve(".pending").resolve("dev.example.gone")).doesNotExist();
        assertThat(store().read()).containsOnlyKeys("dev.example.kept");
    }

    @Test void aPendingInstallReplacesTheInstalledVersionAndKeepsItsConsent() throws Exception {
        plugin(user().resolve("dev.example.tool"), "dev.example.tool", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.tool"), "dev.example.tool", "2.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.fresh"), "dev.example.fresh", "0.1.0");
        store().transact(PluginStateStore.consenting("dev.example.tool", Set.of("terminal.observe")));
        apply();
        assertThat(installed()).extracting(candidate -> candidate.id() + " " + candidate.descriptor().version())
            .containsExactly("dev.example.fresh 0.1.0", "dev.example.tool 2.0.0");
        try (var left = Files.list(user().resolve(".pending"))) { assertThat(left).isEmpty(); }
        try (var all = Files.list(user())) {
            assertThat(all.map(path -> path.getFileName().toString()).filter(name -> name.startsWith(".trash-"))).isEmpty();
        }
        assertThat(store().read().get("dev.example.tool").consented()).containsExactly("terminal.observe");
    }

    @Test void nothingPendingWritesNothing() {
        apply();
        assertThat(root.resolve("plugins.toml")).doesNotExist();
        assertThat(root.resolve("plugins.lock")).doesNotExist();
    }

    @Test void anUnusablePendingInstallIsDiscardedAndTheInstalledPluginSurvives() throws Exception {
        plugin(user().resolve("dev.example.tool"), "dev.example.tool", "1.0.0");
        plugin(user().resolve(".pending").resolve("dev.example.tool"), "dev.example.other", "2.0.0");
        apply();
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

        private static final Version SDK = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);

        private void apply() { PluginMaintenance.apply(user(), store(), root.resolve("plugin-data"), SDK); }

        @Test void theOldLayoutMigratesOnceJarsIntoJarsAndPluginDataIntoData() throws Exception {
            plugin(user().resolve("dev.example.old"), "dev.example.old", "1.0.0");
            Files.createDirectories(root.resolve("plugin-data/dev.example.old"));
            Files.writeString(root.resolve("plugin-data/dev.example.old/state"), "kept");
            Files.createDirectories(root.resolve("plugin-data/dev.example.orphan"));
            apply();
            assertThat(user().resolve("dev.example.old/jars/main.jar")).isRegularFile();
            assertThat(user().resolve("dev.example.old/main.jar")).doesNotExist();
            assertThat(user().resolve("dev.example.old/data/state")).hasContent("kept");
            assertThat(user().resolve("dev.example.orphan/data")).as("data of a plugin no longer installed still moves").isDirectory();
            assertThat(root.resolve("plugin-data")).as("removed once empty").doesNotExist();
            assertThat(installed()).as("a folder with settings or data but no jars is not a plugin and not a problem")
                .extracting(PluginCandidate::id).containsExactly("dev.example.old");
        }

        @Test void aZipDroppedIntoPluginsIsStagedForInstallWithoutConsentAndABadOneIsSetAside() throws Exception {
            Path build = root.resolve("build");
            plugin(build, "dev.example.drop", "1.0.0");
            Path zip = user().resolve("dev.example.drop-1.0.0.zip");
            Files.createDirectories(user());
            try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new java.util.zip.ZipEntry("main.jar")); Files.copy(build.resolve("main.jar"), out); out.closeEntry();
            }
            Files.writeString(user().resolve("junk.zip"), "not a zip");
            apply();
            assertThat(zip).as("consumed").doesNotExist();
            assertThat(user().resolve("junk.zip")).doesNotExist();
            assertThat(user().resolve("junk.zip.rejected")).isRegularFile();
            assertThat(user().resolve("dev.example.drop/jars/main.jar")).as("installed in the same launch, from .pending").isRegularFile();
            assertThat(store().read()).as("no consent was recorded").doesNotContainKey("dev.example.drop");
            assertThat(installed()).extracting(PluginCandidate::id).containsExactly("dev.example.drop");
        }

        @Test void anUpdateReplacesOnlyTheJarsAndARemovalTakesTheWholeFolder() throws Exception {
            plugin(user().resolve("dev.example.up/jars"), "dev.example.up", "1.0.0");
            Files.writeString(user().resolve("dev.example.up/dev.example.up.toml"), "greeting = 'hi'\n");
            Files.createDirectories(user().resolve("dev.example.up/data"));
            Files.writeString(user().resolve("dev.example.up/data/state"), "kept");
            plugin(user().resolve(".pending/dev.example.up"), "dev.example.up", "2.0.0");
            apply();
            assertThat(installed()).singleElement().satisfies(candidate -> assertThat(candidate.descriptor().version().toString()).isEqualTo("2.0.0"));
            assertThat(user().resolve("dev.example.up/dev.example.up.toml")).hasContent("greeting = 'hi'\n");
            assertThat(user().resolve("dev.example.up/data/state")).hasContent("kept");
            store().transact(PluginStateStore.consenting("dev.example.up", Set.of()));
            store().transact(PluginStateStore.removing("dev.example.up", true));
            apply();
            assertThat(user().resolve("dev.example.up")).as("jars, settings and data go together").doesNotExist();
            assertThat(store().read()).doesNotContainKey("dev.example.up");
        }
}
