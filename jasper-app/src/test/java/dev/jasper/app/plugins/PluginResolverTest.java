package dev.jasper.app.plugins;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluginResolverTest {
    private static final Version SDK = Version.parse("0.1.0");

    private static PluginCandidate plugin(String id, String version, PluginCandidate.Origin origin,
                                          Set<String> capabilities, PluginDescriptor.Requirement... requires) {
        return plugin(id, version, origin, capabilities, ">=0.1", requires);
    }

    private static PluginCandidate plugin(String id, String version, PluginCandidate.Origin origin, Set<String> capabilities,
                                          String sdk, PluginDescriptor.Requirement... requires) {
        var descriptor = new PluginDescriptor(id, id, Version.parse(version), "x.Main", VersionRange.parse(sdk), "", "",
            capabilities, Set.of(), List.of(requires));
        return new PluginCandidate(descriptor, Path.of(id), List.of(), origin);
    }

    private static PluginDescriptor.Requirement hard(String id, String range) {
        return new PluginDescriptor.Requirement(id, VersionRange.parse(range), false);
    }

    private static PluginDescriptor.Requirement optional(String id) {
        return new PluginDescriptor.Requirement(id, VersionRange.ANY, true);
    }

    private static Map<String, String> reasons(PluginResolver.Resolution resolution) {
        return resolution.rejected().stream().collect(java.util.stream.Collectors.toMap(PluginStatus::id,
            status -> status.state() + ": " + status.reason()));
    }

    @Test void ordersDependenciesFirstAndIsStableById() {
        var resolution = PluginResolver.resolve(List.of(
            plugin("b.ssh", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.vault", ">=1.0"), optional("c.extra")),
            plugin("c.extra", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("a.vault", "1.2.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("a.alone", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of())), Map.of(), SDK, false);
        assertThat(resolution.rejected()).isEmpty();
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("a.alone", "a.vault", "c.extra", "b.ssh");
    }

    @Test void userPluginsNeedConsentForEveryCapabilityAndBundledOnesDoNot() {
        var inject = Set.of("terminal.inject", "terminal.observe");
        var state = Map.of(
            "u.partial", new PluginStateStore.Entry(true, Set.of("terminal.observe"), false),
            "u.full", new PluginStateStore.Entry(true, inject, false),
            "u.off", new PluginStateStore.Entry(false, inject, false),
            "u.removed", new PluginStateStore.Entry(true, inject, true));
        var resolution = PluginResolver.resolve(List.of(
            plugin("u.new", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.partial", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.full", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.off", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.removed", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.harmless", "1.0.0", PluginCandidate.Origin.USER, Set.of()),
            plugin("b.bundled", "1.0.0", PluginCandidate.Origin.BUNDLED, inject),
            plugin("d.dev", "1.0.0", PluginCandidate.Origin.DEV, inject)), state, SDK, false);
        assertThat(resolution.load()).extracting(PluginCandidate::id)
            .containsExactly("b.bundled", "d.dev", "u.full");
        assertThat(reasons(resolution)).containsOnlyKeys("u.new", "u.partial", "u.off", "u.removed", "u.harmless")
            .containsEntry("u.off", "DISABLED: disabled by the user")
            .containsEntry("u.removed", "DISABLED: marked for removal");
        assertThat(reasons(resolution).get("u.partial")).startsWith("NEEDS_CONSENT").contains("terminal.inject");
        assertThat(reasons(resolution).get("u.harmless")).as("a new user plugin is inert until reviewed, even with no capabilities")
            .startsWith("NEEDS_CONSENT");
    }

    @Test void safeModeDropsUserPluginsOnly() {
        var state = Map.of("u.tool", new PluginStateStore.Entry(true, Set.of(), false));
        var resolution = PluginResolver.resolve(List.of(
            plugin("u.tool", "1.0.0", PluginCandidate.Origin.USER, Set.of()),
            plugin("b.bundled", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of())), state, SDK, true);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("b.bundled");
        assertThat(reasons(resolution)).containsEntry("u.tool", "DISABLED: safe mode");
    }

    @Test void theHigherVersionOfADuplicateIdWins() {
        var state = Map.of("x.tool", new PluginStateStore.Entry(true, Set.of(), false));
        var resolution = PluginResolver.resolve(List.of(
            plugin("x.tool", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("x.tool", "1.1.0", PluginCandidate.Origin.USER, Set.of())), state, SDK, false);
        assertThat(resolution.load()).singleElement().satisfies(candidate -> {
            assertThat(candidate.origin()).isEqualTo(PluginCandidate.Origin.USER);
            assertThat(candidate.descriptor().version()).isEqualTo(Version.parse("1.1.0"));
        });
        assertThat(resolution.rejected()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(PluginStatus.State.SKIPPED);
            assertThat(status.reason()).contains("superseded", "1.1.0");
        });
    }

    @Test void skipsIncompatibleSdkMissingOrIncompatibleDependenciesAndCascades() {
        var resolution = PluginResolver.resolve(List.of(
            plugin("a.future", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), ">=0.2"),
            plugin("b.needs-future", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.future", ">=1.0")),
            plugin("c.needs-b", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("b.needs-future", "")),
            plugin("d.old-vault", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("e.vault", ">=2.0")),
            plugin("e.vault", "1.5.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("f.optional-missing", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), optional("z.absent"))),
            Map.of(), SDK, false);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("e.vault", "f.optional-missing");
        var reasons = reasons(resolution);
        assertThat(reasons.get("a.future")).startsWith("SKIPPED").contains("SDK", "0.1.0");
        assertThat(reasons.get("b.needs-future")).startsWith("SKIPPED").contains("a.future");
        assertThat(reasons.get("c.needs-b")).startsWith("SKIPPED").contains("b.needs-future");
        assertThat(reasons.get("d.old-vault")).startsWith("SKIPPED").contains("e.vault", ">=2.0.0", "1.5.0");
    }

    @Test void everyMemberOfACycleIsSkippedAndSoAreItsDependents() {
        var resolution = PluginResolver.resolve(List.of(
            plugin("a.one", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("b.two", "")),
            plugin("b.two", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.one", "")),
            plugin("c.leaf", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.one", "")),
            plugin("d.free", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of())), Map.of(), SDK, false);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("d.free");
        assertThat(reasons(resolution).get("a.one")).contains("cycle");
        assertThat(reasons(resolution).get("b.two")).contains("cycle");
        assertThat(reasons(resolution).get("c.leaf")).contains("a.one");
    }
}
