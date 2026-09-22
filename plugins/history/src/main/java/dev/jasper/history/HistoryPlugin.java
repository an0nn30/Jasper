package dev.jasper.history;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.snippets.api.SnippetService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Shell history in the command palette: every history file the machine has plus the commands Jasper
 * sees run, searched from one index. Never writes history files.
 */
public class HistoryPlugin implements Plugin {
    static final String OPEN = "dev.jasper.history.open";
    /** The application's former default, unchanged. */
    static final List<String> DEFAULT_TRIVIAL = List.of("exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout");

    private final Function<PluginContext, ShellHistoryIndex> indexFactory;
    private ShellHistoryIndex index;

    /** Created by the runtime: an index over the history files discovered from the home directory and environment. */
    public HistoryPlugin() {
        this(context -> new ShellHistoryIndex(ShellHistorySource.discover(Path.of(System.getProperty("user.home")),
            System.getenv(), System.getProperty("os.name"))));
    }

    /** For tests: an index the caller built. */
    HistoryPlugin(Function<PluginContext, ShellHistoryIndex> indexFactory) { this.indexFactory = indexFactory; }

    @Override public void start(PluginContext context) throws Exception {
        index = indexFactory.apply(context);
        Optional<SnippetService> snippets = context.services().find(SnippetService.class);
        context.actions().register(ActionSpec.of(OPEN, "Search Shell History").withKeywords(List.of("history", "shell", "palette")).withDefaultBinding("cmd+r"),
            invoked -> context.palette().open(invoked.window(), HistoryScope.ID, Optional.empty(), Optional.empty()));
        context.palette().register(new HistoryScope(index, snippets, () -> trivial(context), OPEN));
        // Live capture: the pane's shell label tags the entry the way its history file would.
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, finished -> {
            String shell = context.terminals().pane(finished.paneId()).map(pane -> pane.info().shell()).filter(label -> !label.isBlank()).orElse("shell");
            index.record(new ShellHistoryEntry(finished.command(), Instant.now().getEpochSecond(), Set.of(shell),
                finished.workingDirectory().orElse(null), finished.exitStatus().isPresent() ? finished.exitStatus().getAsInt() : null));
        });
        index.refresh();
    }

    /** The lower-cased trivial commands, or none when {@code deprioritize_trivial = false}. */
    static Set<String> trivial(PluginContext context) {
        if (!context.config().bool("deprioritize_trivial").orElse(true)) return Set.of();
        List<String> configured = context.config().stringList("trivial_commands");
        var set = new HashSet<String>();
        for (String command : configured.isEmpty() ? DEFAULT_TRIVIAL : configured) {
            String word = command.strip().toLowerCase(Locale.ROOT);
            if (!word.isEmpty()) set.add(word);
        }
        return set;
    }

    @Override public void stop() { if (index != null) index.close(); }
}
