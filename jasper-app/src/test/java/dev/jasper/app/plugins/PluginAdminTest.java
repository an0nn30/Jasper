package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginAdminTest {
    private static final Version SDK = Version.parse("0.3.0");
    @TempDir Path root;

    private Path user() { return root.resolve("plugins"); }
    private PluginStateStore store() {
        return new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2));
    }

    private void install(String id, String version, String extraToml) throws Exception {
        PluginJars.build(user().resolve(id), "main.jar", PluginJars.descriptor(id, version, "fix.Main") + extraToml, Map.of(), List.of());
    }

    /** An admin for a process that launched with the disk and state as they are right now. */
    private PluginAdmin admin() throws Exception {
        var options = new PluginRuntime.Options(null, user(), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock"),
            root.resolve("plugin-data"));
        List<PluginCandidate> candidates = PluginDiscovery.scan(user(), PluginCandidate.Origin.USER, new ArrayList<>());
        var resolution = PluginResolver.resolve(candidates, store().read(), SDK, false);
        List<PluginStatus> statuses = new ArrayList<>(resolution.rejected());
        resolution.load().forEach(candidate -> statuses.add(PluginStatus.of(candidate, PluginStatus.State.ACTIVE, "")));
        var launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), false);
        return new PluginAdmin(options, SDK, () -> launch, id -> 0, Duration.ofSeconds(2));
    }

    private static PluginRuntime.Row row(PluginRuntime.Snapshot snapshot, String id) {
        return snapshot.rows().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void reviewingThenDisablingThenRemovingWalksTheStateFile() throws Exception {
        install("dev.example.tool", "1.0.0", "capabilities = [\"terminal.observe\"]\n");
        PluginAdmin admin = admin();
        assertThat(row(admin.snapshot(), "dev.example.tool").needsConsent()).isTrue();
        assertThatThrownBy(() -> admin.setEnabled("dev.example.tool", false)).isInstanceOf(IOException.class).hasMessageContaining("Review");
        assertThatThrownBy(() -> admin.consent("dev.example.tool", List.of())).as("not what the descriptor declares")
            .isInstanceOf(IOException.class).hasMessageContaining("changed");

        var consented = admin.consent("dev.example.tool", List.of("terminal.observe"));
        assertThat(consented.restartNeeded()).isTrue();
        assertThat(row(consented, "dev.example.tool").pending()).isEqualTo("Will load after restart");
        assertThat(store().read().get("dev.example.tool")).isEqualTo(new PluginStateStore.Entry(true, Set.of("terminal.observe"), false));

        var disabled = admin.setEnabled("dev.example.tool", false);
        assertThat(disabled.restartNeeded()).as("it was not running, and will not").isFalse();
        assertThat(row(disabled, "dev.example.tool").enabled()).isFalse();

        var removing = admin.remove("dev.example.tool", true);
        assertThat(row(removing, "dev.example.tool").pendingRemoval()).isTrue();
        assertThat(user().resolve("dev.example.tool")).as("jars go at the next launch").exists();
        assertThat(row(admin.remove("dev.example.tool", false), "dev.example.tool").pendingRemoval()).isFalse();
        assertThatThrownBy(() -> admin.remove("dev.example.absent", true)).isInstanceOf(IOException.class);
    }

    @Test void installingAZipStagesItUntilTheNextLaunchAndCanBeDiscarded() throws Exception {
        Path built = Files.createTempDirectory(root, "built");
        PluginJars.build(built, "main.jar", PluginJars.descriptor("dev.example.zipped", "2.1.0", "fix.Main")
            + "description = \"Zipped\"\nvendor = \"Example\"\ncapabilities = [\"terminal.inject\"]\n", Map.of(), List.of());
        Path zip = root.resolve("zipped.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("main.jar"));
            out.write(Files.readAllBytes(built.resolve("main.jar")));
            out.closeEntry();
        }
        PluginAdmin admin = admin();
        PluginRuntime.Inspection inspection = admin.inspect(zip);
        assertThat(inspection.id()).isEqualTo("dev.example.zipped");
        assertThat(inspection.version()).isEqualTo("2.1.0");
        assertThat(inspection.vendor()).isEqualTo("Example");
        assertThat(inspection.capabilities()).containsExactly("terminal.inject");
        assertThat(inspection.update()).isFalse();
        assertThatThrownBy(() -> admin.install(root.resolve("elsewhere"), List.of("terminal.inject")))
            .as("only a staging directory this installer made").isInstanceOf(IOException.class);

        var installed = admin.install(inspection.staged(), inspection.capabilities());
        assertThat(installed.restartNeeded()).isTrue();
        assertThat(row(installed, "dev.example.zipped").pendingInstall()).isTrue();
        assertThat(row(installed, "dev.example.zipped").needsConsent()).isFalse();

        var discarded = admin.discardInstall("dev.example.zipped");
        assertThat(discarded.rows()).isEmpty();
        assertThat(discarded.restartNeeded()).isFalse();
        assertThat(store().read()).isEmpty();
    }
}
