package dev.jasper.history;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.snippets.api.SnippetService;
import dev.jasper.snippets.api.SnippetView;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HistoryPluginTest {
    static final String SCOPE = HistoryScopeTest.SCOPE;

    /** A stand-in for the Snippets plugin's service. */
    static final class Snippets implements SnippetService {
        final List<String> appended = new java.util.ArrayList<>();
        String failWith;
        @Override public Optional<SnippetView> byName(String name) { return Optional.empty(); }
        @Override public void append(String name, String command, java.util.function.BiConsumer<Optional<SnippetView>, Optional<String>> done) {
            appended.add(name + "=" + command);
            if (failWith != null) done.accept(Optional.empty(), Optional.of(failWith));
            else done.accept(Optional.of(new SnippetView(name, command, List.of())), Optional.empty());
        }
    }

    @Test void liveCommandsAreCapturedWithTheirShellDirectoryAndExitStatus() {
        var index = HistoryScopeTest.index();
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t");
            UUID pane = host.addTerminalPane(tab, HistoryScopeTest.pane(Path.of("/src")));
            host.start(HistoryScopeTest.INFO, Set.of(), Set.of(), new HistoryPlugin(context -> index));
            host.commandFinished(pane, "make test", OptionalInt.of(2), Duration.ofSeconds(1));
            host.flush();
            List<String> rows = host.searchScope(SCOPE, "", window, pane);
            assertThat(rows).singleElement().satisfies(line -> assertThat(line).endsWith("|make test|true"));
            ShellHistoryEntry entry = index.snapshot().entries().getFirst();
            assertThat(entry.shells()).containsExactly("zsh");
            assertThat(entry.directory()).isEqualTo(Path.of("/src"));
            assertThat(entry.exitStatus()).isEqualTo(2);
            assertThat(host.invoke("dev.jasper.history.open", window, pane)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " " + SCOPE + " - -");
        }
    }

    @Test void saveAsSnippetIsAThirdVerbOnlyWithTheServiceAndReopensSnippetsOnTheSavedRow() {
        var index = HistoryScopeTest.index(HistoryScopeTest.at("git log --oneline", 1, "zsh", null));
        var snippets = new Snippets();
        try (var host = new FakePluginHost()) {
            host.start(new PluginInfo("dev.jasper.snippets", "Snippets", "0.1.0", Set.of()), Set.of(), Set.of(),
                context -> context.services().publish(SnippetService.class, snippets));
            host.start(HistoryScopeTest.INFO, Set.of(), Set.of("dev.jasper.snippets"), new HistoryPlugin(context -> index));
            UUID window = host.addTerminalWindow();
            assertThat(host.scopes()).containsExactly(SCOPE + "|History|paste,paste_run,save_snippet");
            String row = host.searchScope(SCOPE, "", window, null).getFirst().split("\\|")[0];
            assertThat(host.availableInScope(SCOPE, row, "save_snippet", window, null)).as("needs no live pane").isTrue();
            assertThat(host.stepInScope(SCOPE, row, "save_snippet", window, null)).contains("Save as snippet: git log --oneline");
            assertThat(host.completeStep(SCOPE, row, "save_snippet", window, null, Map.of("name", "Recent log")))
                .isEqualTo("reopen:" + SnippetService.SCOPE_ID + ":" + SnippetService.rowId("Recent log") + ":Recent log");
            assertThat(snippets.appended).containsExactly("Recent log=git log --oneline");
            snippets.failWith = "A snippet named Recent log exists";
            assertThat(host.completeStep(SCOPE, row, "save_snippet", window, null, Map.of("name", "Recent log"))).isEqualTo("error:A snippet named Recent log exists");
        }
        try (var host = new FakePluginHost()) {
            host.start(HistoryScopeTest.INFO, Set.of(), Set.of("dev.jasper.snippets"), new HistoryPlugin(context -> HistoryScopeTest.index()));
            assertThat(host.scopes()).as("without the service, no third verb").containsExactly(SCOPE + "|History|paste,paste_run");
        }
    }
}
