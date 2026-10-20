package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tabs and panes spec 10 and 16, the collision check on each platform: no bundled plugin's default shortcut loses to a
 * tab or pane action's, and Move Pane to New Tab's keys (plan 8b) stay free.
 */
class TabShortcutCollisionTest {
    /** The actions of spec 10's table that Jasper defines itself. */
    private static final EnumSet<ActionId> TABS_AND_PANES = EnumSet.of(ActionId.NEW_TAB, ActionId.SPLIT_RIGHT,
        ActionId.SPLIT_DOWN, ActionId.CLOSE_PANE, ActionId.CLOSE_TAB, ActionId.CLOSE_WINDOW, ActionId.NEXT_TAB,
        ActionId.PREVIOUS_TAB, ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
        ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9,
        ActionId.FOCUS_PANE_LEFT, ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
        ActionId.MAXIMIZE_PANE, ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);

    @TempDir Path root;
    private PluginRuntime runtime;
    private Contributions contributions;

    @AfterEach void stop() throws Exception {
        if (runtime != null) {
            var stopped = onEdtValue(runtime::stop);
            CompletableFuture.allOf(stopped.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }

    @Test void theTabAndPaneDefaultsTakeNoBundledPluginsDefaultOnEitherPlatform() throws Exception {
        UnboundVault.settings(root.resolve("plugins")); // the runtime starts the staged Vault, whose keychain stays unused
        onEdt(() -> {
            contributions = new Contributions();
            var buddy = new BuddyTestSupport();
            runtime = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")),
                root.resolve("plugins"), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(buddy.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED, contributions,
                AppContractTest.headlessWindows(), new TerminalRegistry());
            runtime.start(Map.of(), true);
        });
        onEdt(() -> {
            var extensions = contributions.actions().stream().map(ActionEntry::extension).toList();
            assertThat(extensions).as("the bundled plugins' actions").isNotEmpty();
            for (boolean mac : new boolean[]{true, false}) {
                String platform = mac ? "macOS" : "Linux and Windows";
                var resolved = KeyBindings.defaults(mac).withExtensions(extensions);
                for (ActionId action : TABS_AND_PANES)
                    assertThat(resolved.problems()).as("%s on %s", action.id(), platform)
                        .noneMatch(problem -> problem.kind() == KeyBindings.Problem.Kind.DEFAULT_DROPPED
                            && problem.message().contains("'" + action.id() + "'"));
                var movePane = KeyBindings.parse(mac ? "alt+cmd+t" : "ctrl+alt+shift+t", mac).orElseThrow();
                assertThat(resolved.bindings().idsFor(movePane)).as("Move Pane to New Tab's keys on %s", platform).isEmpty();
            }
        });
    }
}
