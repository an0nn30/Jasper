package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.presentation.PresentationFacts;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 4.4 and 4.5: one presentation at a time, chosen inside the pure resolver. */
class PresentationSelectionTest {
    static final Version SDK = Version.parse("0.21.0");
    static final String BUILD = "/app/jasper-app.jar@1";

    static PluginCandidate provider(String id, PluginCandidate.Origin origin, PluginDescriptor.Requirement... requires) {
        var descriptor = new PluginDescriptor(id, "Look " + id, Version.parse("0.1.0"), "x.Main", VersionRange.parse(">=0.21.0, <0.22"),
            "", "", Set.of("presentation.provide"), Set.of(), List.of(requires), "", "", dev.jasper.ui.model.Nodes.Hue.NEUTRAL,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED, List.of(), "look.kit.toml", List.of(), true);
        return new PluginCandidate(descriptor, Path.of(id), List.of(), origin);
    }

    static PluginCandidate plain(String id, PluginDescriptor.Requirement... requires) {
        var descriptor = new PluginDescriptor(id, id, Version.parse("1.0.0"), "x.Main", VersionRange.parse(">=0.21.0, <0.22"), "", "",
            Set.of(), Set.of(), List.of(requires), "", "");
        return new PluginCandidate(descriptor, Path.of(id), List.of(), PluginCandidate.Origin.BUNDLED);
    }

    static Presentations facts(Map<PluginCandidate, PresentationFacts> facts, AttemptStore.Record attempts) {
        var byKey = new java.util.HashMap<String, PresentationFacts>();
        facts.forEach((candidate, value) -> byKey.put(Presentations.key(candidate), value));
        return new Presentations(byKey, attempts, BUILD);
    }

    static PresentationFacts valid(String digest) { return new PresentationFacts(digest, Optional.empty(), Optional.empty()); }

    static Map<String, String> reasons(PluginResolver.Resolution resolution) {
        return resolution.rejected().stream().collect(java.util.stream.Collectors.toMap(PluginStatus::id, status -> status.state() + ": " + status.reason()));
    }

    @Test void aSingleValidProviderIsElectedAndLoads() {
        var look = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var resolution = PluginResolver.resolve(List.of(look, plain("a.other")), Map.of(), SDK, false,
            facts(Map.of(look, valid("d1")), AttemptStore.Record.EMPTY));
        assertThat(resolution.presentation()).contains(look);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("a.other", "b.look");
        assertThat(resolution.rejected()).isEmpty();
    }

    @Test void twoEnabledProvidersElectOneByOriginThenIdAndLosersPostNoProblem() {
        PluginCandidate bundledB = provider("b.look", PluginCandidate.Origin.BUNDLED), bundledA = provider("a.look", PluginCandidate.Origin.BUNDLED);
        PluginCandidate user = provider("0.look", PluginCandidate.Origin.USER), dev = provider("z.look", PluginCandidate.Origin.DEV);
        var reviewed = new PluginStateStore.Entry(true, Set.of("presentation.provide"), false);
        var all = facts(Map.of(bundledA, valid("a"), bundledB, valid("b"), user, valid("u"), dev, valid("z")), AttemptStore.Record.EMPTY);
        var resolution = PluginResolver.resolve(List.of(bundledA, bundledB, user, dev), Map.of("0.look", reviewed), SDK, false, all);
        assertThat(resolution.presentation()).contains(dev);
        assertThat(reasons(resolution)).containsOnly(Map.entry("a.look", "SKIPPED: the Look z.look look is active"),
            Map.entry("b.look", "SKIPPED: the Look z.look look is active"), Map.entry("0.look", "SKIPPED: the Look z.look look is active"));
        assertThat(resolution.rejected()).allSatisfy(status -> assertThat(status.quiet()).as(status.id()).isTrue());
        var withoutDev = PluginResolver.resolve(List.of(bundledB, user, bundledA), Map.of("0.look", reviewed), SDK, false, all);
        assertThat(withoutDev.presentation()).as("BUNDLED before USER, then the lowest id").contains(bundledA);
    }

    @Test void aProviderWithAProblemIsSkippedWithItsMessageAndDoesNotWin() {
        PluginCandidate broken = provider("a.look", PluginCandidate.Origin.DEV), good = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var resolution = PluginResolver.resolve(List.of(broken, good), Map.of(), SDK, false, facts(Map.of(
            broken, new PresentationFacts("x", Optional.of("[metrics].row_height: 40 is outside 18-28"), Optional.empty()), good, valid("g")),
            AttemptStore.Record.EMPTY));
        assertThat(resolution.presentation()).contains(good);
        assertThat(reasons(resolution)).containsEntry("a.look", "SKIPPED: Presentation invalid: [metrics].row_height: 40 is outside 18-28");
        assertThat(resolution.rejected()).singleElement().satisfies(status -> assertThat(status.quiet()).isFalse());
        var unread = PluginResolver.resolve(List.of(good), Map.of(), SDK, false);
        assertThat(reasons(unread)).containsEntry("b.look", "SKIPPED: Presentation invalid: presentation: look.kit.toml could not be read");
    }

    @Test void safeModeDisablesEveryPresentationWhateverItsOrigin() {
        PluginCandidate bundled = provider("a.look", PluginCandidate.Origin.BUNDLED), dev = provider("b.look", PluginCandidate.Origin.DEV),
            user = provider("c.look", PluginCandidate.Origin.USER);
        var resolution = PluginResolver.resolve(List.of(bundled, dev, user, plain("d.other")), Map.of(), SDK, true,
            facts(Map.of(bundled, valid("a"), dev, valid("b"), user, valid("c")), AttemptStore.Record.EMPTY));
        assertThat(resolution.presentation()).isEmpty();
        assertThat(reasons(resolution)).containsOnly(Map.entry("a.look", "DISABLED: safe mode uses the standard look"),
            Map.entry("b.look", "DISABLED: safe mode uses the standard look"), Map.entry("c.look", "DISABLED: safe mode uses the standard look"));
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("d.other");
    }

    @Test void aMatchingPendingOrFailedAttemptSkipsTheWinnerAndLeavesTheOthersLosers() {
        PluginCandidate winner = provider("a.look", PluginCandidate.Origin.BUNDLED), other = provider("b.look", PluginCandidate.Origin.BUNDLED);
        for (var state : List.of(AttemptStore.State.PENDING, AttemptStore.State.FAILED)) {
            var record = new AttemptStore.Record(Map.of("a.look", AttemptStore.Attempt.NONE.tried("0.1.0", "d1", BUILD, state)));
            var resolution = PluginResolver.resolve(List.of(winner, other), Map.of(), SDK, false,
                facts(Map.of(winner, valid("d1"), other, valid("d2")), record));
            assertThat(resolution.presentation()).as(state.name()).isEmpty();
            assertThat(reasons(resolution)).containsOnly(Map.entry("a.look", "SKIPPED: did not finish starting with this look last time"),
                Map.entry("b.look", "SKIPPED: the Look a.look look is active"));
            assertThat(resolution.rejected()).allSatisfy(status -> assertThat(status.quiet()).isTrue());
        }
        var proven = new AttemptStore.Record(Map.of("a.look", AttemptStore.Attempt.NONE.tried("0.1.0", "d1", BUILD, AttemptStore.State.PROVEN)));
        assertThat(PluginResolver.resolve(List.of(winner), Map.of(), SDK, false, facts(Map.of(winner, valid("d1")), proven)).presentation()).contains(winner);
        var changed = new AttemptStore.Record(Map.of("a.look", AttemptStore.Attempt.NONE.tried("0.1.0", "old", BUILD, AttemptStore.State.PENDING)));
        assertThat(PluginResolver.resolve(List.of(winner), Map.of(), SDK, false, facts(Map.of(winner, valid("d1")), changed)).presentation())
            .as("a changed kit gets a fresh try").contains(winner);
    }

    @Test void hardDependentsOfASkippedProviderAreSkippedAsUnmetAndOptionalOnesLoad() {
        PluginCandidate winner = provider("a.look", PluginCandidate.Origin.DEV), loser = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var hard = plain("c.hard", new PluginDescriptor.Requirement("b.look", VersionRange.ANY, false));
        var optional = plain("d.soft", new PluginDescriptor.Requirement("b.look", VersionRange.ANY, true));
        var resolution = PluginResolver.resolve(List.of(winner, loser, hard, optional), Map.of(), SDK, false,
            facts(Map.of(winner, valid("a"), loser, valid("b")), AttemptStore.Record.EMPTY));
        assertThat(reasons(resolution)).containsEntry("c.hard", "SKIPPED: requires b.look, which is not available");
        assertThat(resolution.load()).extracting(PluginCandidate::id).contains("a.look", "d.soft").doesNotContain("b.look", "c.hard");
    }

    @Test void aProviderThatNeedsALoserCannotLoadSoTheNextProviderWins() {
        PluginCandidate direct = provider("a.look", PluginCandidate.Origin.DEV, new PluginDescriptor.Requirement("b.look", VersionRange.ANY, false));
        PluginCandidate through = provider("a.look", PluginCandidate.Origin.DEV, new PluginDescriptor.Requirement("c.lib", VersionRange.ANY, false));
        PluginCandidate next = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var library = plain("c.lib", new PluginDescriptor.Requirement("b.look", VersionRange.ANY, false));
        for (PluginCandidate winner : List.of(direct, through)) {
            var resolution = PluginResolver.resolve(List.of(winner, next, library), Map.of(), SDK, false,
                facts(Map.of(winner, valid("a"), next, valid("b")), AttemptStore.Record.EMPTY));
            assertThat(resolution.presentation()).as(winner.descriptor().requires().toString()).contains(next);
            assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("b.look", "c.lib");
            assertThat(resolution.load()).containsAll(resolution.presentation().stream().toList());
            assertThat(reasons(resolution)).containsOnly(Map.entry("a.look", "SKIPPED: " + PluginStatus.NEEDS_LOOK));
            assertThat(resolution.rejected()).singleElement().satisfies(status -> assertThat(status.quiet()).isFalse());
        }
        var alone = PluginResolver.resolve(List.of(direct, provider("b.look", PluginCandidate.Origin.USER)), Map.of(), SDK, false,
            facts(Map.of(direct, valid("a")), AttemptStore.Record.EMPTY));
        assertThat(alone.presentation()).as("its dependency is unreadable, so neither loads").isEmpty();
        assertThat(alone.load()).isEmpty();
    }

    @Test void onlyTheLookStatusesAndSupersededCopiesAreQuiet() {
        var other = new PluginDescriptor("e.sdk", "e.sdk", Version.parse("1.0.0"), "x.Main", VersionRange.parse(">=0.22.0"), "", "",
            Set.of(), Set.of(), List.of(), "", "");
        PluginCandidate broken = provider("f.look", PluginCandidate.Origin.DEV), needs = provider("g.look", PluginCandidate.Origin.DEV,
            new PluginDescriptor.Requirement("h.look", VersionRange.ANY, false)), winner = provider("h.look", PluginCandidate.Origin.DEV);
        var candidates = List.of(plain("a.cycle", new PluginDescriptor.Requirement("b.cycle", VersionRange.ANY, false)),
            plain("b.cycle", new PluginDescriptor.Requirement("a.cycle", VersionRange.ANY, false)),
            plain("c.missing", new PluginDescriptor.Requirement("nowhere", VersionRange.ANY, false)),
            plain("d.version", new PluginDescriptor.Requirement("i.plain", VersionRange.parse(">=2.0.0"), false)),
            new PluginCandidate(other, Path.of("e.sdk"), List.of(), PluginCandidate.Origin.BUNDLED), broken, needs, winner, plain("i.plain"));
        var resolution = PluginResolver.resolve(candidates, Map.of(), SDK, false, facts(Map.of(broken,
            new PresentationFacts("x", Optional.of("[metrics].row_height: 40 is outside 18-28"), Optional.empty()), needs, valid("g"),
            winner, valid("h")), AttemptStore.Record.EMPTY));
        assertThat(resolution.presentation()).contains(winner);
        assertThat(resolution.rejected()).extracting(PluginStatus::id)
            .containsExactlyInAnyOrder("a.cycle", "b.cycle", "c.missing", "d.version", "e.sdk", "f.look", "g.look");
        assertThat(resolution.rejected()).allSatisfy(status -> assertThat(status.quiet()).as(status.formatted()).isFalse());
    }

    @org.junit.jupiter.api.io.TempDir Path home;

    private PluginAdmin admin(PluginCatalog.Launch launch) {
        var options = new PluginRuntime.Options(home.resolve("bundled"), home.resolve("plugins"), null, false,
            home.resolve("plugins.toml"), home.resolve("plugins.lock"));
        return new PluginAdmin(options, SDK, () -> launch, id -> 0, java.time.Duration.ofSeconds(2));
    }

    private static String look(String id, String name, boolean onByDefault) {
        return dev.jasper.app.testsupport.LookFixtures.descriptor(id, name) + dev.jasper.app.testsupport.LookFixtures.provider("look.kit.toml")
            + (onByDefault ? "" : "enabled_by_default = false\n");
    }

    @Test void enablingAProviderTurnsTheOtherEnabledProviderOffInOneTransaction() throws Exception {
        dev.jasper.app.testsupport.LookFixtures.plugin(home.resolve("bundled"), "a.look", look("a.look", "Alpha", true),
            Map.of("look.kit.toml", dev.jasper.app.testsupport.LookFixtures.KIT));
        dev.jasper.app.testsupport.LookFixtures.plugin(home.resolve("bundled"), "b.look", look("b.look", "Beta", false),
            Map.of("look.kit.toml", dev.jasper.app.testsupport.LookFixtures.KIT));
        var snapshot = admin(new PluginCatalog.Launch(List.of(), List.of(), Map.of(), false)).setEnabled("b.look", true);
        var state = new PluginStateStore(home.resolve("plugins.toml"), home.resolve("plugins.lock"), java.time.Duration.ofSeconds(2)).read();
        assertThat(state).containsEntry("b.look", new PluginStateStore.Entry(true, Set.of(), false, true))
            .containsEntry("a.look", new PluginStateStore.Entry(false, Set.of(), false, false));
        assertThat(snapshot.turnedOff()).contains("Alpha");
        assertThat(snapshot.restartNeeded()).isTrue();
        assertThat(dev.jasper.app.pluginmanager.PluginManagerAccess.restartText(snapshot))
            .isEqualTo("Restart Jasper to apply your changes. Alpha was turned off: one look can be active at a time.");
        var plain = PluginStateStore.consentingPresentation("u.look", Set.of("presentation.provide"), Set.of("a.look", "b.look"))
            .apply(new java.util.TreeMap<>(state));
        assertThat(plain).containsEntry("u.look", new PluginStateStore.Entry(true, Set.of("presentation.provide"), false, false))
            .containsEntry("b.look", new PluginStateStore.Entry(false, Set.of(), false, false));
    }

    @Test void theCatalogPredictsTheLaunchIncludingAnAttemptSkip() throws Exception {
        dev.jasper.app.testsupport.LookFixtures.plugin(home.resolve("bundled"), "a.look", look("a.look", "Alpha", true),
            Map.of("look.kit.toml", dev.jasper.app.testsupport.LookFixtures.KIT));
        List<String> problems = new java.util.ArrayList<>();
        var candidates = PluginDiscovery.scan(home.resolve("bundled"), PluginCandidate.Origin.BUNDLED, problems);
        String digest = Presentations.read(candidates, AttemptStore.Record.EMPTY, BUILD).of(candidates.getFirst()).orElseThrow().digest();
        for (boolean skipped : new boolean[] {false, true}) {
            var store = new AttemptStore(home);
            store.update("a.look", attempt -> skipped ? attempt.tried("0.1.0", digest, BUILD, AttemptStore.State.PENDING) : AttemptStore.Attempt.NONE,
                AttemptStore.WAIT);
            var launched = PluginResolver.resolve(candidates, Map.of(), SDK, false, Presentations.read(candidates, store.read(), BUILD));
            var launch = new PluginCatalog.Launch(candidates, launched.rejected(), PluginCatalog.versions(launched.load()), false, BUILD);
            var snapshot = admin(launch).snapshot();
            assertThat(snapshot.restartNeeded()).as("skipped: " + skipped).isFalse();
            assertThat(launched.presentation().isPresent()).isEqualTo(!skipped);
        }
    }

    private PluginStateStore store() {
        return new PluginStateStore(home.resolve("plugins.toml"), home.resolve("plugins.lock"), java.time.Duration.ofSeconds(2));
    }

    private PluginRuntime.Options options() {
        return new PluginRuntime.Options(home.resolve("bundled"), home.resolve("plugins"), null, false, home.resolve("plugins.toml"),
            home.resolve("plugins.lock"));
    }

    /** The launch a process makes from the disk as it is now: its resolution, every loaded plugin active. */
    private PluginCatalog.Launch launchNow() {
        var selection = PluginSelection.scan(options(), BUILD, SDK, PluginSelection.host());
        List<PluginStatus> statuses = new java.util.ArrayList<>(selection.resolution().rejected());
        selection.resolution().load().forEach(candidate -> statuses.add(PluginStatus.of(candidate, PluginStatus.State.ACTIVE, "")));
        return new PluginCatalog.Launch(selection.candidates(), statuses, PluginCatalog.versions(selection.resolution().load()), false, BUILD);
    }

    private static Path bundledLook(Path root, String id, String name, boolean onByDefault) throws Exception {
        return dev.jasper.app.testsupport.LookFixtures.plugin(root, id, look(id, name, onByDefault),
            Map.of("look.kit.toml", dev.jasper.app.testsupport.LookFixtures.KIT));
    }

    @Test void removingAUserCopyBehindAnOptedInBundledProviderPredictsTheBundledLook() throws Exception {
        bundledLook(home.resolve("bundled"), "dev.example.look", "Look", false);
        bundledLook(home.resolve("plugins"), "dev.example.look", "Look", true);
        store().transact(state -> { state.put("dev.example.look", new PluginStateStore.Entry(true, Set.of(), false, true)); return state; });
        var launch = launchNow();
        assertThat(launch.selected()).as("the user copy is not reviewed, and it supersedes the bundled one").isEmpty();
        var removing = admin(launch).remove("dev.example.look", true);
        assertThat(removing.restartNeeded()).as("the restart switches the look").isTrue();
        PluginMaintenance.apply(home.resolve("plugins"), store(), null, SDK);
        assertThat(store().read()).containsEntry("dev.example.look", new PluginStateStore.Entry(true, Set.of(), false, true));
        var next = PluginSelection.scan(options(), BUILD, SDK, PluginSelection.host());
        assertThat(next.presentation().look()).as("the launch runs what the page predicted").isEqualTo("dev.example.look");
        assertThat(PluginStateStore.afterRemoval(new PluginStateStore.Entry(true, Set.of("x"), true, false))).isEmpty();
    }

    @Test void installingAProviderFromThePluginsPageTurnsTheOtherProvidersOff() throws Exception {
        bundledLook(home.resolve("bundled"), "a.look", "Alpha", true);
        Path built = bundledLook(java.nio.file.Files.createTempDirectory(home, "built"), "u.look", "User look", true);
        Path zip = home.resolve("u.zip");
        try (var out = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(zip))) {
            out.putNextEntry(new java.util.zip.ZipEntry("u.look.jar"));
            out.write(java.nio.file.Files.readAllBytes(built.resolve("jars/u.look.jar")));
            out.closeEntry();
        }
        var admin = admin(launchNow());
        var inspection = admin.inspect(zip);
        var installed = admin.install(inspection.staged(), inspection.capabilities());
        assertThat(store().read()).containsEntry("u.look", new PluginStateStore.Entry(true, Set.of("presentation.provide"), false, false))
            .containsEntry("a.look", new PluginStateStore.Entry(false, Set.of(), false, false));
        assertThat(installed.turnedOff()).contains("Alpha");
        assertThat(installed.restartNeeded()).isTrue();
    }

    @Test void consentingToAUserProviderTurnsTheEnabledProviderOff() throws Exception {
        bundledLook(home.resolve("bundled"), "a.look", "Alpha", true);
        bundledLook(home.resolve("plugins"), "u.look", "User look", true);
        var admin = admin(launchNow());
        var consented = admin.consent("u.look", List.of("presentation.provide"));
        assertThat(store().read()).containsEntry("u.look", new PluginStateStore.Entry(true, Set.of("presentation.provide"), false, false))
            .containsEntry("a.look", new PluginStateStore.Entry(false, Set.of(), false, false));
        assertThat(consented.turnedOff()).contains("Alpha");
    }

    @Test void aProviderThatFailedToStartNeedsNoRestartBanner() throws Exception {
        bundledLook(home.resolve("bundled"), "a.look", "Alpha", true);
        var selection = PluginSelection.scan(options(), BUILD, SDK, PluginSelection.host());
        assertThat(selection.presentation().look()).isEqualTo("a.look");
        List<PluginStatus> statuses = new java.util.ArrayList<>(selection.resolution().rejected());
        selection.resolution().load().forEach(candidate -> statuses.add(PluginStatus.of(candidate, PluginStatus.State.FAILED, "boom")));
        selection.attempt().failed();
        var launch = new PluginCatalog.Launch(selection.candidates(), statuses, PluginCatalog.versions(selection.resolution().load()), false, BUILD);
        var snapshot = admin(launch).snapshot();
        assertThat(snapshot.restartNeeded()).as("it runs neither now nor after a restart").isFalse();
        assertThat(snapshot.rows()).filteredOn(row -> row.id().equals("a.look")).singleElement()
            .satisfies(row -> assertThat(row.pending()).startsWith("Will not load after restart"));
    }

    @Test void onlyAdmittedProvidersHaveTheirFilesRead() {
        var on = provider("a.look", PluginCandidate.Origin.BUNDLED);
        var disabled = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var unreviewed = provider("c.look", PluginCandidate.Origin.USER);
        var marked = provider("d.look", PluginCandidate.Origin.BUNDLED);
        var state = Map.of("b.look", new PluginStateStore.Entry(false, Set.of(), false), "d.look", new PluginStateStore.Entry(true, Set.of(), true));
        assertThat(PluginResolver.admitted(List.of(on, disabled, unreviewed, marked), state, SDK, false)).containsExactly(on);
        assertThat(PluginResolver.admitted(List.of(on), Map.of(), SDK, true)).as("safe mode").isEmpty();
    }
}
