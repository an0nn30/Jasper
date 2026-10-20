package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.3: a bundled plugin can be off by default, and the user's opt-in survives every prune path. */
class PluginStateStoreOptInTest {
    private static final String ID = "dev.example.look";
    private static final Version SDK = Version.parse("0.21.0");
    @TempDir Path home;

    private PluginStateStore store() { return new PluginStateStore(home.resolve("plugins.toml"), home.resolve("plugins.lock"), Duration.ofSeconds(2)); }

    private static PluginCandidate candidate(PluginCandidate.Origin origin, boolean enabledByDefault, Set<String> capabilities) {
        var descriptor = new PluginDescriptor(ID, "Look", Version.parse("0.1.0"), "x.Main", VersionRange.parse(">=0.21.0, <0.22"),
            "", "", capabilities, Set.of(), List.of(), "", "", dev.jasper.ui.model.Nodes.Hue.NEUTRAL,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED, List.of(), "", List.of(), enabledByDefault);
        return new PluginCandidate(descriptor, Path.of(ID), List.of(), origin);
    }

    private PluginAdmin admin(Path bundled) {
        var options = new PluginRuntime.Options(bundled, home.resolve("plugins"), null, false, home.resolve("plugins.toml"), home.resolve("plugins.lock"));
        return new PluginAdmin(options, SDK, () -> new PluginCatalog.Launch(List.of(), List.of(), Map.of(), false), id -> 0, Duration.ofSeconds(2));
    }

    @Test void theEffectiveRuleKeepsOnlyABundledOffByDefaultPluginWithoutAnEntryOff() {
        var off = candidate(PluginCandidate.Origin.BUNDLED, false, Set.of());
        assertThat(PluginStateStore.effective(null, off)).isFalse();
        assertThat(PluginStateStore.effective(null, candidate(PluginCandidate.Origin.BUNDLED, true, Set.of()))).isTrue();
        assertThat(PluginStateStore.effective(null, candidate(PluginCandidate.Origin.DEV, false, Set.of()))).as("DEV ignores it").isTrue();
        assertThat(PluginStateStore.effective(null, candidate(PluginCandidate.Origin.USER, false, Set.of()))).as("USER ignores it").isTrue();
        assertThat(PluginStateStore.effective(new PluginStateStore.Entry(true, Set.of(), false, true), off)).isTrue();
        assertThat(PluginStateStore.effective(new PluginStateStore.Entry(false, Set.of(), false), candidate(PluginCandidate.Origin.BUNDLED, true, Set.of()))).isFalse();
        var resolution = PluginResolver.resolve(List.of(off), Map.of(), SDK, false);
        assertThat(resolution.load()).isEmpty();
        assertThat(resolution.rejected()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(PluginStatus.State.DISABLED);
            assertThat(status.reason()).isEqualTo("off by default");
        });
    }

    @Test void oneClickWritesTheOptInTheRowTurnsOnAndDisablingClearsIt() throws Exception {
        Path bundled = home.resolve("bundled");
        LookFixtures.plugin(bundled, ID, LookFixtures.descriptor(ID, "Look") + "enabled_by_default = false\n", Map.of());
        var admin = admin(bundled);
        var before = admin.snapshot().rows().stream().filter(row -> row.id().equals(ID)).findFirst().orElseThrow();
        assertThat(before.enabled()).as("unchecked, and the details button reads Enable").isFalse();
        assertThat(before.canToggle()).isTrue();
        var after = admin.setEnabled(ID, true);
        assertThat(Files.readString(home.resolve("plugins.toml"))).contains("[plugins.\"" + ID + "\"]\nenabled = true\nconsented = []\nremove = false\nopt_in = true\n");
        assertThat(after.rows().stream().filter(row -> row.id().equals(ID)).findFirst().orElseThrow().enabled()).isTrue();
        assertThat(after.restartNeeded()).as("the existing restart banner").isTrue();
        admin.setEnabled(ID, false);
        assertThat(store().read()).containsEntry(ID, new PluginStateStore.Entry(false, Set.of(), false, false));
    }

    @Test void theOptInSurvivesRemoveFalseDiscardPendingAndThePendingRemoval() throws Exception {
        var store = store();
        store.transact(state -> { state.put(ID, new PluginStateStore.Entry(true, Set.of(), false, true)); return state; });
        store.transact(PluginStateStore.removing(ID, false));
        assertThat(store.read()).as("remove(id, false) drops only DEFAULT entries").containsEntry(ID, new PluginStateStore.Entry(true, Set.of(), false, true));

        Path user = home.resolve("plugins");
        store.transact(state -> { state.put(ID, new PluginStateStore.Entry(true, Set.of("terminal.observe"), true, true)); return state; });
        PluginInstaller.discardPending(ID, user, store);
        assertThat(store.read()).as("discardPending keeps the opt-in and clears consent and the mark")
            .containsEntry(ID, new PluginStateStore.Entry(true, Set.of(), false, true));

        LookFixtures.plugin(user, ID, LookFixtures.descriptor(ID, "Look"), Map.of());
        store.transact(state -> { state.put(ID, new PluginStateStore.Entry(true, Set.of("terminal.observe"), true, true)); return state; });
        PluginMaintenance.apply(user, store, null, SDK);
        assertThat(Files.exists(user.resolve(ID).resolve("jars"))).as("the USER copy is removed").isFalse();
        assertThat(store.read()).containsEntry(ID, new PluginStateStore.Entry(true, Set.of(), false, true));
    }

    @Test void aUserCopyWithAnOptInEntryNeedsReviewAndConsentReplacesTheOptIn() throws Exception {
        var entry = new PluginStateStore.Entry(true, Set.of("terminal.observe"), false, true);
        var resolution = PluginResolver.resolve(List.of(candidate(PluginCandidate.Origin.USER, true, Set.of("terminal.observe"))),
            Map.of(ID, entry), SDK, false);
        assertThat(resolution.rejected()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(PluginStatus.State.NEEDS_CONSENT);
            assertThat(status.reason()).isEqualTo("not reviewed yet");
        });
        var state = new TreeMap<String, PluginStateStore.Entry>(Map.of(ID, entry));
        PluginStateStore.consenting(ID, Set.of("terminal.observe")).apply(state);
        assertThat(state).as("a Plugins-page install commits consent, which replaces the opt-in")
            .containsEntry(ID, new PluginStateStore.Entry(true, Set.of("terminal.observe"), false, false));
        var admin = admin(home.resolve("bundled"));
        LookFixtures.plugin(home.resolve("plugins"), ID, LookFixtures.descriptor(ID, "Look"), Map.of());
        store().transact(current -> { current.put(ID, entry); return current; });
        assertThatThrownBy(() -> admin.setEnabled(ID, false)).hasMessageContaining("Review Look");
    }
}
