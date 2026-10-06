package dev.jasper.app.plugins;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedRemoteHost.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 2 Task 8a acceptance (Task 6d): the staged Remote plugin's Sessions panel, a kit Panel, mounted in a real window.
 * The window borrows the panel's kit header, which carries the panel's own title and, through {@code PanelHost.setHeader},
 * the saved-session count and the New session and Import sessions actions before Options and Hide: one title row, no
 * in-panel action row. The Add Host dialog keeps baseline f16ae32f keys through the real kit components: Enter saves
 * nothing (no default button), Escape cancels. Runs on the test thread and hops to the EDT for each step.
 */
class StagedRemoteSessionsTest {
    @TempDir Path root;

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void theSessionsHeaderKeepsItsCountAndActionsInARealWindowAndTheHostDialogKeepsItsKeys() throws Exception {
        try (var remote = new StagedRemoteHost(root, true)) {
            remote.openSessions();
            onEdt(() -> {
                var header = remote.header();
                assertThat(labels(header)).as("the title, then the count from PanelHost.setHeader").contains("Sessions", "0");
                assertThat(names(header)).as("contributed actions before the window's own").containsExactly("New session", "Import sessions", "Options", "Hide");
                assertThat(labels(remote.window).stream().filter("Sessions"::equals).count()).as("one title row").isEqualTo(1);
                assertThat(names(remote.window).stream().filter(name -> name.equals("New session") || name.equals("Import sessions")).toList())
                    .as("no duplicate in-panel action row").containsExactly("New session", "Import sessions");
                button(header, "New session").doClick(0);
            });
            var editor = remote.awaitSurface("Add Host");
            onEdt(() -> {
                type(editor, "host.name.input", "web");
                type(editor, "host.hostname.input", "web.example");
                type(editor, "host.username.input", "deploy"); // valid: a save would succeed here
                press(require(editor, "host.name.input"), KeyEvent.VK_ENTER, '\n');
                press(require(editor, "host.hostname.input"), KeyEvent.VK_ENTER, '\n');
                press(require(editor, "host.save"), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.settle();
            onEdt(() -> {
                assertThat(editor.closed()).as("Enter saves nothing: the old dialog had no default button").isFalse();
                assertThat(labels(remote.header())).contains("0");
                press(require(editor, "host.name.input"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            });
            StagedVaultHost.await(editor::closed, "Escape cancels the Add Host dialog");
            StagedVaultHost.settle();
            onEdt(() -> {
                assertThat(labels(remote.header())).as("cancelling saved nothing").contains("0");
                button(remote.header(), "New session").doClick(0);
            });
            var again = remote.awaitSurface("Add Host");
            onEdt(() -> {
                type(again, "host.name.input", "web");
                type(again, "host.hostname.input", "web.example");
                type(again, "host.username.input", "deploy");
                ((AbstractButton) require(again, "host.save")).doClick(0);
            });
            StagedVaultHost.await(() -> again.closed() && labels(remote.header()).contains("1"), "Add Host saved the host and the header counts it");
            assertThat(remote.problems).isEmpty();
        }
    }
}
