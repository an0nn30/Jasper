package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeChooserTest {
    @Test void scriptsMultipleFilesAndDirectoryAndDiscardsClosedOwnerResults() {
        try (var host = new FakePluginHost()) {
            var context = host.start(new PluginInfo("dev.x.chooser", "Chooser", "1", Set.of()), Set.of(), Set.of(), c -> {});
            // The owner closes when a move takes its only tab to another window (full windows spec 5.3).
            UUID stays = host.addTerminalWindow();
            UUID child = host.addTerminalWindow(), tab = host.addTerminalTab(child, "zsh");
            host.addTerminalPane(tab, dev.jasper.sdk.terminal.PaneInfo.unknown());
            var owner = context.terminals().window(child).orElseThrow();
            host.queuePathSelection(List.of(Path.of("/a").toAbsolutePath(), Path.of("/b").toAbsolutePath()));
            assertThat(context.windows().chooseFiles(owner, "Files", Optional.empty())).containsExactly(Path.of("/a").toAbsolutePath(),Path.of("/b").toAbsolutePath());
            host.queuePathSelection(List.of(Path.of("/dest").toAbsolutePath()));
            assertThat(context.windows().chooseDirectory(owner, "Directory", Optional.empty())).contains(Path.of("/dest").toAbsolutePath());
            assertThat(context.windows().chooseFiles(owner, "Cancel", Optional.empty())).isEmpty();
            host.queuePathSelection(List.of(Path.of("/notes/new.toml").toAbsolutePath()));
            assertThat(context.windows().chooseSaveFile(owner, "Save As", Optional.of(Path.of("/notes/old.toml").toAbsolutePath()))).contains(Path.of("/notes/new.toml").toAbsolutePath());
            assertThat(context.windows().chooseSaveFile(owner, "Save As", Optional.empty())).as("nothing queued cancels").isEmpty();
            host.queuePathSelection(List.of(Path.of("/a").toAbsolutePath()));
            host.duringPathSelection(() -> host.moveTab(tab, stays));
            assertThat(context.windows().chooseFiles(owner, "Closed", Optional.empty())).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.windows().chooseFiles(owner, "Stale", Optional.empty()));
        }
    }
}
