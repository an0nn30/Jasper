package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubcommandsTest {
    private static PluginCandidate candidate(String id, String name, PluginCandidate.Origin origin, String... names) {
        var subcommands = Arrays.stream(names).map(each -> new PluginDescriptor.Subcommand(each, "Summary of " + each)).toList();
        var descriptor = new PluginDescriptor(id, name, Version.parse("1.0.0"), id + ".Main", VersionRange.parse(">=0.17"),
            "", "", Set.of(), Set.of(), List.of(), "", "", dev.jasper.ui.model.Nodes.Hue.NEUTRAL,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED, subcommands);
        return new PluginCandidate(descriptor, Path.of("plugins", id), List.of(), origin);
    }

    @Test void aBundledPluginKeepsItsNameAndOtherwiseTheFirstIdWins() {
        var problems = new ArrayList<String>();
        Subcommands table = Subcommands.choose(List.of(
            candidate("zz.user", "Zed Tool", PluginCandidate.Origin.USER, "editor", "zap"),
            candidate("dev.jasper.editor", "Editor", PluginCandidate.Origin.BUNDLED, "editor"),
            candidate("aa.user", "Early", PluginCandidate.Origin.USER, "zap"),
            candidate("mm.dev", "Dev", PluginCandidate.Origin.DEV, "lone")), (title, details) -> problems.add(title + "|" + details));
        assertThat(table.owner("editor")).map(Subcommands.Owner::pluginId).contains("dev.jasper.editor");
        assertThat(table.owner("zap")).map(Subcommands.Owner::pluginId).contains("aa.user");
        assertThat(table.owner("lone")).map(Subcommands.Owner::pluginName).contains("Dev");
        assertThat(table.owner("editor").orElseThrow().summary()).isEqualTo("Summary of editor");
        assertThat(table.owner("missing")).isEmpty();
        assertThat(table.all()).extracting(Subcommands.Owner::name).containsExactly("editor", "lone", "zap");
        assertThat(problems).containsExactlyInAnyOrder(
            "A plugin command was not added|Zed Tool: the subcommand editor is already provided by Editor",
            "A plugin command was not added|Zed Tool: the subcommand zap is already provided by Early");
    }

    @Test void aBundledPluginWinsEvenWhenItsIdSortsAfterTheOtherPlugins() {
        var problems = new ArrayList<String>();
        Subcommands table = Subcommands.choose(List.of(
            candidate("aa.user", "Early User", PluginCandidate.Origin.USER, "shared"),
            candidate("zz.bundled", "Late Bundled", PluginCandidate.Origin.BUNDLED, "shared")), (title, details) -> problems.add(title + "|" + details));
        assertThat(table.owner("shared")).map(Subcommands.Owner::pluginId).contains("zz.bundled");
        assertThat(table.owner("shared")).map(Subcommands.Owner::pluginName).contains("Late Bundled");
        assertThat(problems).containsExactly(
            "A plugin command was not added|Early User: the subcommand shared is already provided by Late Bundled");
    }

    @Test void aDevPluginLosesToABundledOneEvenWhenItsIdSortsFirst() {
        var problems = new ArrayList<String>();
        Subcommands table = Subcommands.choose(List.of(
            candidate("aa.dev", "Early Dev", PluginCandidate.Origin.DEV, "shared"),
            candidate("zz.bundled", "Late Bundled", PluginCandidate.Origin.BUNDLED, "shared")), (title, details) -> problems.add(title + "|" + details));
        assertThat(table.owner("shared")).map(Subcommands.Owner::pluginId).contains("zz.bundled");
        assertThat(problems).containsExactly(
            "A plugin command was not added|Early Dev: the subcommand shared is already provided by Late Bundled");
    }

    @Test void twoBundledPluginsContestingANameGoToTheFirstById() {
        var problems = new ArrayList<String>();
        Subcommands table = Subcommands.choose(List.of(
            candidate("dev.jasper.second", "Second", PluginCandidate.Origin.BUNDLED, "shared"),
            candidate("dev.jasper.first", "First", PluginCandidate.Origin.BUNDLED, "shared")), (title, details) -> problems.add(title + "|" + details));
        assertThat(table.owner("shared")).map(Subcommands.Owner::pluginId).contains("dev.jasper.first");
        assertThat(problems).containsExactly(
            "A plugin command was not added|Second: the subcommand shared is already provided by First");
    }

    @Test void noPluginsMeansNoSubcommands() {
        assertThat(Subcommands.choose(List.of(), (title, details) -> { }).all()).isEmpty();
        assertThat(Subcommands.NONE.owner("editor")).isEmpty();
        var owner = new Subcommands.Owner("greet", "Say hello", "test.a", "A");
        assertThat(Subcommands.of(List.of(owner)).owner("greet")).contains(owner);
    }
}
