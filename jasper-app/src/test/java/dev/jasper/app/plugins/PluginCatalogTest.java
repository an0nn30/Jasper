package dev.jasper.app.plugins;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluginCatalogTest {
    private static final Version SDK = Version.parse("0.3.0");

    private static PluginCandidate plugin(String id, String version, PluginCandidate.Origin origin, String... capabilities) {
        var descriptor = new PluginDescriptor(id, id.substring(id.lastIndexOf('.') + 1), Version.parse(version), "fix.Main",
            VersionRange.parse(">=0.1"), "Does " + id, "Example", Set.of(capabilities), Set.of(),
            List.of(new PluginDescriptor.Requirement("dev.example.base", VersionRange.parse(">=1.0"), true)));
        return new PluginCandidate(descriptor, Path.of("/plugins").resolve(id), List.of(), origin);
    }

    private static PluginCatalog.Launch launch(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state, boolean safeMode) {
        var resolution = PluginResolver.resolve(candidates, state, SDK, safeMode);
        List<PluginStatus> statuses = new java.util.ArrayList<>(resolution.rejected());
        resolution.load().forEach(candidate -> statuses.add(PluginStatus.of(candidate, PluginStatus.State.ACTIVE, "")));
        return new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), safeMode);
    }

    private static PluginRuntime.Row row(PluginRuntime.Snapshot snapshot, String id) {
        return snapshot.rows().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }

    private static final PluginStateStore.Entry REVIEWED = new PluginStateStore.Entry(true, Set.of("terminal.observe"), false);

    @Test void anUnchangedDiskNeedsNoRestartAndRowsDescribeWhatIsRunning() {
        var candidates = List.of(plugin("dev.example.bundled", "1.0.0", PluginCandidate.Origin.BUNDLED),
            plugin("dev.example.tool", "1.2.0", PluginCandidate.Origin.USER, "terminal.observe"));
        var state = Map.of("dev.example.tool", REVIEWED);
        var snapshot = PluginCatalog.compute(launch(candidates, state, false), new PluginCatalog.Disk(candidates, Map.of(), state), SDK,
            id -> id.equals("dev.example.tool") ? 3 : 0);
        assertThat(snapshot.restartNeeded()).isFalse();
        assertThat(snapshot.safeMode()).isFalse();
        assertThat(snapshot.rows()).extracting(PluginRuntime.Row::id).containsExactly("dev.example.bundled", "dev.example.tool");
        var tool = row(snapshot, "dev.example.tool");
        assertThat(tool.name()).isEqualTo("tool");
        assertThat(tool.version()).isEqualTo("1.2.0");
        assertThat(tool.origin()).isEqualTo("Installed");
        assertThat(tool.state()).isEqualTo("ACTIVE");
        assertThat(tool.capabilities()).containsExactly("terminal.observe");
        assertThat(tool.unconsented()).isEmpty();
        assertThat(tool.requires()).containsExactly("dev.example.base >=1.0.0 (optional)");
        assertThat(tool.errors()).isEqualTo(3);
        assertThat(tool.enabled()).isTrue();
        assertThat(tool.canToggle()).isTrue();
        assertThat(tool.canRemove()).isTrue();
        assertThat(tool.pending()).isEmpty();
        var bundled = row(snapshot, "dev.example.bundled");
        assertThat(bundled.origin()).isEqualTo("Bundled");
        assertThat(bundled.canRemove()).isFalse();
        assertThat(bundled.canToggle()).isTrue();
    }

    @Test void disablingALoadedPluginNeedsARestartAndTheRowSaysWhy() {
        var candidates = List.of(plugin("dev.example.tool", "1.2.0", PluginCandidate.Origin.USER, "terminal.observe"));
        var atLaunch = launch(candidates, Map.of("dev.example.tool", REVIEWED), false);
        var now = Map.of("dev.example.tool", new PluginStateStore.Entry(false, Set.of("terminal.observe"), false));
        var snapshot = PluginCatalog.compute(atLaunch, new PluginCatalog.Disk(candidates, Map.of(), now), SDK, id -> 0);
        assertThat(snapshot.restartNeeded()).isTrue();
        var tool = row(snapshot, "dev.example.tool");
        assertThat(tool.state()).as("still running").isEqualTo("ACTIVE");
        assertThat(tool.enabled()).isFalse();
        assertThat(tool.pending()).isEqualTo("Will not load after restart: disabled by the user");
    }

    @Test void anUnreviewedUserPluginOffersReviewOnlyAndAnUpdateListsItsNewCapabilities() {
        var found = plugin("dev.example.found", "1.0.0", PluginCandidate.Origin.USER, "terminal.inject", "terminal.observe");
        var grown = plugin("dev.example.tool", "2.0.0", PluginCandidate.Origin.USER, "terminal.inject", "terminal.observe");
        var candidates = List.of(found, grown);
        var state = Map.of("dev.example.tool", REVIEWED);
        var snapshot = PluginCatalog.compute(launch(candidates, state, false), new PluginCatalog.Disk(candidates, Map.of(), state), SDK, id -> 0);
        assertThat(snapshot.restartNeeded()).isFalse();
        var unreviewed = row(snapshot, "dev.example.found");
        assertThat(unreviewed.state()).isEqualTo("NEEDS_CONSENT");
        assertThat(unreviewed.needsConsent()).isTrue();
        assertThat(unreviewed.canToggle()).as("an entry would make it look reviewed").isFalse();
        assertThat(unreviewed.unconsented()).containsExactly("terminal.inject", "terminal.observe");
        var update = row(snapshot, "dev.example.tool");
        assertThat(update.needsConsent()).isTrue();
        assertThat(update.canToggle()).isTrue();
        assertThat(update.unconsented()).containsExactly("terminal.inject");
    }

    @Test void pendingInstallsAndRemovalsAreDescribedAndAPluginFoundSinceLaunchIsNotLoaded() {
        var running = plugin("dev.example.tool", "1.0.0", PluginCandidate.Origin.USER);
        var doomed = plugin("dev.example.doomed", "1.0.0", PluginCandidate.Origin.USER);
        var reviewed = new PluginStateStore.Entry(true, Set.of(), false);
        var atLaunch = launch(List.of(running, doomed), Map.of("dev.example.tool", reviewed, "dev.example.doomed", reviewed), false);
        var dropped = plugin("dev.example.dropped", "0.1.0", PluginCandidate.Origin.USER);
        var disk = new PluginCatalog.Disk(List.of(running, doomed, dropped),
            Map.of("dev.example.tool", plugin("dev.example.tool", "2.0.0", PluginCandidate.Origin.USER),
                "dev.example.fresh", plugin("dev.example.fresh", "1.0.0", PluginCandidate.Origin.USER)),
            Map.of("dev.example.tool", reviewed, "dev.example.fresh", reviewed, "dev.example.dropped", reviewed,
                "dev.example.doomed", new PluginStateStore.Entry(true, Set.of(), true)));
        var snapshot = PluginCatalog.compute(atLaunch, disk, SDK, id -> 0);
        assertThat(snapshot.restartNeeded()).isTrue();
        assertThat(row(snapshot, "dev.example.tool").version()).as("the row is about what the next launch runs").isEqualTo("2.0.0");
        assertThat(row(snapshot, "dev.example.tool").pendingInstall()).isTrue();
        assertThat(row(snapshot, "dev.example.tool").pending()).isEqualTo("Version 2.0.0 will be installed at restart");
        assertThat(row(snapshot, "dev.example.fresh").state()).isEqualTo("NOT_LOADED");
        assertThat(row(snapshot, "dev.example.fresh").pending()).isEqualTo("Version 1.0.0 will be installed at restart");
        assertThat(row(snapshot, "dev.example.doomed").pendingRemoval()).isTrue();
        assertThat(row(snapshot, "dev.example.doomed").canToggle()).isFalse();
        assertThat(row(snapshot, "dev.example.doomed").pending()).isEqualTo("Will be removed at restart");
        assertThat(row(snapshot, "dev.example.dropped").state()).isEqualTo("NOT_LOADED");
        assertThat(row(snapshot, "dev.example.dropped").pending()).isEqualTo("Will load after restart");
    }

    @Test void safeModeNeverAsksForAnOrdinaryRestart() {
        var candidates = List.of(plugin("dev.example.tool", "1.0.0", PluginCandidate.Origin.USER));
        var reviewed = new PluginStateStore.Entry(true, Set.of(), false);
        var atLaunch = launch(candidates, Map.of("dev.example.tool", reviewed), true);
        var disabled = Map.of("dev.example.tool", new PluginStateStore.Entry(false, Set.of(), false));
        var snapshot = PluginCatalog.compute(atLaunch, new PluginCatalog.Disk(candidates, Map.of(), disabled), SDK, id -> 0);
        assertThat(snapshot.safeMode()).isTrue();
        assertThat(snapshot.restartNeeded()).as("the safe-mode banner offers Restart normally instead").isFalse();
        assertThat(row(snapshot, "dev.example.tool").state()).isEqualTo("DISABLED");
        assertThat(row(snapshot, "dev.example.tool").reason()).isEqualTo("safe mode");
        assertThat(row(snapshot, "dev.example.tool").enabled()).isFalse();
    }
}
