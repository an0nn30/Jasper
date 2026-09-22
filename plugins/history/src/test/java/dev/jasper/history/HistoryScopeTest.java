package dev.jasper.history;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HistoryScopeTest {
    static final String SCOPE = "dev.jasper.history.scope";
    static final PluginInfo INFO = new PluginInfo("dev.jasper.history", "Shell History", "0.1.0",
        Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT));

    static PaneInfo pane(Path directory) {
        return new PaneInfo("zsh", Optional.ofNullable(directory), Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty(), "zsh");
    }

    /** An index over no files whose worker runs inline and whose snapshots publish at once; the plugin closes it at stop. */
    static ShellHistoryIndex index(ShellHistoryEntry... entries) {
        var index = new ShellHistoryIndex(List.of(), HistoryTestSupport.inlineWorker(), Runnable::run);
        for (ShellHistoryEntry entry : entries) index.record(entry);
        return index;
    }

    static ShellHistoryEntry at(String command, long time, String shell, Path directory) {
        return new ShellHistoryEntry(command, time, Set.of(shell), directory, null);
    }

    @Test void emptyQueryShowsMostRecentAndTiersOrderPrefixWordThenSubstringWithADirectoryBonus() {
        var index = index(at("git status", 1, "zsh", Path.of("/a")), at("make git-hooks", 2, "zsh", Path.of("/b")),
            at("ls", 3, "zsh", Path.of("/a")), at("digit count", 4, "zsh", Path.of("/a")), at("git push", 5, "zsh", Path.of("/b")));
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> index));
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane(Path.of("/a")));
            assertThat(host.scopes()).containsExactly(SCOPE + "|History|paste,paste_run");
            List<String> recent = host.searchScope(SCOPE, "", window, pane);
            assertThat(recent).extracting(line -> line.split("\\|")[1]).as("newest first, trivial ls last")
                .containsExactly("git push", "digit count", "make git-hooks", "git status", "ls");
            assertThat(host.searchScope(SCOPE, "git", window, pane)).extracting(line -> line.split("\\|")[1])
                .as("prefix tier first (directory /a before /b), then word boundary, then substring")
                .containsExactly("git status", "git push", "make git-hooks", "digit count");
            assertThat(host.searchScope(SCOPE, "git st", window, pane)).extracting(line -> line.split("\\|")[1]).containsExactly("git status");
            assertThat(host.searchScope(SCOPE, "nothing", window, pane)).isEmpty();
        }
    }

    @Test void pasteAndPasteAndRunReachTheTargetOnlyWhileItIsLiveAndTagsAppearWithTwoShells() {
        var index = index(at("git status", 1, "zsh", null), at("ls -la", 2, "fish", null), at("a\nb", 3, "zsh", null));
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> index));
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t"), pane = host.addTerminalPane(tab, pane(null));
            List<String> rows = host.searchScope(SCOPE, "", window, pane);
            assertThat(rows.getFirst()).as("multi-line titles collapse").contains("|a ↵ b|");
            assertThat(rows).extracting(line -> line.split("\\|")[1]).as("trivial ls -la sinks below git status").containsExactly("a \u21b5 b", "git status", "ls -la");
            String status = rows.get(1).split("\\|")[0];
            assertThat(host.availableInScope(SCOPE, status, "paste", window, pane)).isTrue();
            assertThat(host.availableInScope(SCOPE, status, "paste", window, null)).as("no target").isFalse();
            host.executeInScope(SCOPE, status, "paste_run", window, pane);
            assertThat(host.sent(pane)).containsExactly("paste:git status", "write:\r");
        }
    }

    static ShellHistoryIndex fourCommands() {
        return index(at("cd /x", 1, "zsh", null), at("make", 2, "zsh", null), at("ls", 3, "zsh", null), at("clear", 4, "zsh", null));
    }

    @Test void trivialCommandsSortBelowRealWorkUnlessTheSettingIsOffAndAConfiguredListReplacesTheBuiltInOne() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("defaults").containsExactly("make", "clear", "ls", "cd /x");
        }
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.history", Map.of("deprioritize_trivial", false));
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("off").containsExactly("clear", "ls", "make", "cd /x");
        }
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.history", Map.of("trivial_commands", List.of("make")));
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("replaced").containsExactly("clear", "ls", "cd /x", "make");
            assertThat(host.searchScope(SCOPE, "make", window, null)).as("still found when searched for").hasSize(1);
        }
    }

    @Test void onlyAShortCommandWhoseFirstWordIsTrivialCounts() {
        var set = Set.copyOf(HistoryPlugin.DEFAULT_TRIVIAL);
        assertThat(HistoryScope.trivial("ls", set)).isTrue();
        assertThat(HistoryScope.trivial("LS -la", set)).isTrue();
        assertThat(HistoryScope.trivial("cd deep/path && make", set)).isFalse();
        assertThat(HistoryScope.trivial("clearcache", set)).isFalse();
        assertThat(HistoryScope.trivial("ls", Set.of())).isFalse();
        assertThat(HistoryScope.suggestedName("git log --oneline\nmore")).isEqualTo("git log");
        assertThat(HistoryScope.suggestedName("x".repeat(200))).hasSize(128);
        assertThat(HistoryScope.tier("git status", "git", new String[]{"git"})).isEqualTo(0);
        assertThat(HistoryScope.tier("make git-hooks", "git", new String[]{"git"})).isEqualTo(1);
        assertThat(HistoryScope.tier("digit", "git", new String[]{"git"})).isEqualTo(2);
        assertThat(HistoryScope.tier("digit", "git x", new String[]{"git", "x"})).isEqualTo(-1);
    }
}
