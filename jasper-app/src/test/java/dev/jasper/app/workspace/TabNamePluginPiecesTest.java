package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.Contributions;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tab names spec 6: a plugin's piece joins a tab's name and follows its value. */
class TabNamePluginPiecesTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void aPluginPieceJoinsTheTabNameAndLeavesWithIt() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        edt(() -> {
            WindowContent owner = content(launcher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            var defaults = ConfigSnapshot.defaults();
            owner.applyConfiguration(defaults.toBuilder().terminal(defaults.terminal().withTabName("{dir} {host}")).build(), false);
            UUID tab = owner.currentTerminal().id().value();
            assertThat(owner.tabs().get(0).title()).isEqualTo("~");
            var host = model.tabNamePieces().add("host", "the SSH host", "test.remote");
            model.tabNamePieces().set("host", tab, "db1.example.com");
            assertThat(owner.tabs().get(0).title()).isEqualTo("~ db1.example.com");
            assertThat(owner.currentTerminal().namePieces()).containsEntry("host", "db1.example.com").containsEntry("dir", "~");
            host.close();
            assertThat(owner.tabs().get(0).title()).isEqualTo("~");
        });
    }
}
