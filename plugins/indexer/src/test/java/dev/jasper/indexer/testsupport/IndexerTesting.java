package dev.jasper.indexer.testsupport;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Shared fixtures for the indexer's tests. */
public final class IndexerTesting {
    /** The plugin as the bundled manifest describes it. */
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.indexer", "Project Index", "0.1.0",
        Set.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN, Capabilities.PALETTE_CONTRIBUTE));

    private IndexerTesting() { }

    /**
     * A running local shell.
     *
     * @param directory its working directory, or null for none
     * @return the pane's info
     */
    public static PaneInfo pane(Path directory) {
        return new PaneInfo("zsh", Optional.ofNullable(directory), Optional.empty(), 80, 24, true, SessionKind.LOCAL,
            Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh");
    }
}
