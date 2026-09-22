package dev.jasper.snippets;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.snippets.api.SnippetService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetsScopeTest {
    static final String SCOPE = "dev.jasper.snippets.scope";
    static final PluginInfo INFO = new PluginInfo("dev.jasper.snippets", "Snippets", "0.1.0",
        Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT));
    @TempDir Path home;

    static PaneInfo pane() {
        return new PaneInfo("zsh", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty(), "zsh");
    }

    /** The store works on its own thread and publishes on the EDT; wait for {@code condition}, pumping the EDT. */
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 500 && !condition.getAsBoolean(); i++) { javax.swing.SwingUtilities.invokeAndWait(() -> { }); Thread.sleep(10); }
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertThat(condition.getAsBoolean()).as("settled").isTrue();
    }

    /** A host whose data root is {@code home/plugins}, with the plugin started over a snippets file holding {@code toml}. */
    FakePluginHost started(String toml) throws Exception {
        var host = new FakePluginHost(home.resolve("plugins"));
        Path file = home.resolve("plugins/dev.jasper.snippets/data/snippets.toml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, toml);
        host.start(INFO, Set.of(), Set.of(), new SnippetsPlugin());
        UUID window = host.addTerminalWindow();
        await(() -> !host.searchScope(SCOPE, "", window, null).isEmpty());
        return host;
    }

    static final String THREE = """
        [[snippet]]
        name = "Deploy"
        command = "make deploy"
        keywords = ["ship"]

        [[snippet]]
        name = "Inspect branch"
        command = "git log {{branch}} --since={{since}}"

        [[snippet]]
        name = "Say hi"
        command = "echo \\\\{{not a placeholder}}"
        """;

    @Test void searchRanksNamesThenKeywordsThenCommandsAndTagsPlaceholderCounts() throws Exception {
        try (var host = started(THREE)) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane());
            assertThat(host.scopes()).containsExactly(SCOPE + "|Snippets|paste,paste_run,edit");
            assertThat(host.searchScope(SCOPE, "", window, pane)).containsExactly(
                SnippetService.rowId("Deploy") + "|Deploy|true",
                SnippetService.rowId("Inspect branch") + "|Inspect branch|true",
                SnippetService.rowId("Say hi") + "|Say hi|true");
            assertThat(host.searchScope(SCOPE, "ship", window, pane)).as("keyword").containsExactly(SnippetService.rowId("Deploy") + "|Deploy|true");
            assertThat(host.searchScope(SCOPE, "git", window, pane)).as("command text").containsExactly(SnippetService.rowId("Inspect branch") + "|Inspect branch|true");
            assertThat(host.searchScope(SCOPE, "in", window, pane)).as("name word prefix before command text")
                .startsWith(SnippetService.rowId("Inspect branch") + "|Inspect branch|true");
            assertThat(host.searchScope(SCOPE, "nothing here", window, pane)).isEmpty();
        }
    }

    @Test void placeholdersOpenAFillInStepThatRemembersValuesAndPlainSnippetsPasteAtOnce() throws Exception {
        try (var host = started(THREE)) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane());
            host.searchScope(SCOPE, "", window, pane);
            String inspect = SnippetService.rowId("Inspect branch"), deploy = SnippetService.rowId("Deploy"), hi = SnippetService.rowId("Say hi");
            assertThat(host.stepInScope(SCOPE, inspect, "paste", window, pane)).contains("Inspect branch");
            assertThat(host.stepInScope(SCOPE, deploy, "paste", window, pane)).isEmpty();
            assertThat(host.completeStep(SCOPE, inspect, "paste_run", window, pane, Map.of("branch", "main", "since", "1w"))).isEqualTo("done");
            assertThat(host.sent(pane)).containsExactly("paste:git log main --since=1w", "write:\r");
            host.executeInScope(SCOPE, deploy, "paste", window, pane);
            host.executeInScope(SCOPE, hi, "paste", window, pane);
            assertThat(host.sent(pane)).contains("paste:make deploy", "paste:echo {{not a placeholder}}");
            assertThat(host.availableInScope(SCOPE, deploy, "paste", window, null)).as("paste needs a live pane").isFalse();
            assertThat(host.availableInScope(SCOPE, deploy, "edit", window, null)).as("edit does not").isTrue();
        }
    }

    @Test void editFileOpensTheEditorAndABrokenFileShowsAnErrorRow() throws Exception {
        try (var host = started("[[snippet]\nname = \"Broken\"\n")) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane());
            List<String> rows = host.searchScope(SCOPE, "", window, pane);
            assertThat(rows).containsExactly("snippets.file-error|Snippets file has errors|false");
            assertThat(host.availableInScope(SCOPE, "snippets.file-error", "edit", window, pane)).isTrue();
            assertThat(host.availableInScope(SCOPE, "snippets.file-error", "paste", window, pane)).isFalse();
            host.executeInScope(SCOPE, "snippets.file-error", "edit", window, pane);
            await(() -> !host.openedInEditor().isEmpty());
            assertThat(host.openedInEditor()).containsExactly(home.resolve("plugins/dev.jasper.snippets/data/snippets.toml"));
        }
    }
}
