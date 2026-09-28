package dev.jasper.app.plugins;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the staged Remote jar through real TOML reload and the app's keybinding resolver. */
class RemoteShortcutConfigTest {
    private static final String HOSTS = "dev.jasper.remote.hosts", CONNECT = "dev.jasper.remote.connect",
        SFTP_TOGGLE = "dev.jasper.remote.sftp.toggle";
    @TempDir Path root;
    private PluginRuntime runtime;
    private Contributions contributions;

    private void start() throws Exception {
        onEdt(() -> {
            contributions = new Contributions();
            var buddy = new BuddyTestSupport();
            runtime = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")),
                root.resolve("plugins"), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(buddy.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED, contributions,
                AppContractTest.headlessWindows(), new TerminalRegistry());
            runtime.start(Map.of(), true);
        });
    }

    private void reload(String toml) throws Exception {
        Files.writeString(root.resolve("plugins/dev.jasper.remote/dev.jasper.remote.toml"), toml);
        onEdt(() -> runtime.configurationChanged(Map.of()));
    }

    private KeyBindings.Resolved resolve(Map<String, String> overrides) {
        return KeyBindings.withOverrides(true, overrides).withExtensions(contributions.actions().stream()
            .map(action -> new KeyBindings.Extension(action.id(), action.defaultBinding())).toList());
    }

    @AfterEach void stop() throws Exception {
        if (runtime != null) {
            var stopped = onEdtValue(runtime::stop);
            CompletableFuture.allOf(stopped.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }

    @Test void defaultsReloadDisableAndAppOverridesPreserveMenuPlacements() throws Exception {
        start();
        onEdt(() -> {
            assertThat(contributions.action(HOSTS).orElseThrow().defaultBinding()).contains("cmd+shift+s");
            assertThat(contributions.action(CONNECT).orElseThrow().defaultBinding()).contains("cmd+shift+h");
        });
        reload("[shortcuts]\ntoggle_panel = 'ctrl+alt+s'\nopen_palette = 'ctrl+alt+h'\n");
        onEdt(() -> {
            var bindings = resolve(Map.of()).bindings();
            assertThat(bindings.strokeFor(HOSTS)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
            assertThat(bindings.strokeFor(CONNECT)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
            assertThat(bindings.idFor(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).isEmpty();
            assertThat(contributions.menus()).filteredOn(menu -> menu.target().equals(MenuTarget.standard(MenuTarget.Slot.VIEW)))
                .as("View > Tools toggles the panel; Remote adds no View item of its own")
                .allSatisfy(menu -> assertThat(menu.entries()).doesNotContain(new MenuEntry.Item(HOSTS)));
            assertThat(contributions.menus()).anySatisfy(menu -> {
                assertThat(menu.target().key()).isEqualTo("dev.jasper.remote.menu");
                assertThat(menu.entries()).contains(new MenuEntry.Item(HOSTS), new MenuEntry.Item(CONNECT));
            });
        });
        var hostsBefore = onEdtValue(() -> contributions.action(HOSTS).orElseThrow());
        reload("keepalive_seconds = 9\n[shortcuts]\ntoggle_panel = 'ctrl+alt+s'\nopen_palette = 'ctrl+alt+h'\n");
        onEdt(() -> assertThat(contributions.action(HOSTS)).containsSame(hostsBefore));
        reload("[shortcuts]\ntoggle_panel = ''\nopen_palette = '  '\n");
        onEdt(() -> {
            assertThat(resolve(Map.of()).bindings().strokeFor(HOSTS)).isEmpty();
            assertThat(resolve(Map.of()).bindings().strokeFor(CONNECT)).isEmpty();
            assertThat(resolve(Map.of(HOSTS, "ctrl+alt+s")).bindings().strokeFor(HOSTS))
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        });
        reload("# Reset to defaults\n");
        onEdt(() -> assertThat(contributions.action(HOSTS).orElseThrow().defaultBinding()).contains("cmd+shift+s"));
    }

    @Test void eachRemotePanelHasOneToggleThatToolsListsWithItsShortcutAcrossReloads() throws Exception {
        start();
        onEdt(() -> {
            assertThat(contributions.action("dev.jasper.remote.panel.toggle")).as("no app toggle beside Remote's").isEmpty();
            assertThat(contributions.action("dev.jasper.remote.sftp.panel.toggle")).isEmpty();
            assertThat(contributions.actions()).filteredOn(action -> action.id().startsWith("dev.jasper.remote.") && action.title().startsWith("Toggle "))
                .extracting(action -> action.id() + "|" + action.title())
                .containsExactlyInAnyOrder(HOSTS + "|Toggle SSH hosts", SFTP_TOGGLE + "|Toggle SFTP");
            assertThat(tools()).contains(new MenuEntry.Item(HOSTS), new MenuEntry.Item(SFTP_TOGGLE));
        });
        reload("[shortcuts]\ntoggle_panel = 'ctrl+alt+s'\n");
        onEdt(() -> {
            assertThat(contributions.action(HOSTS).orElseThrow().defaultBinding()).contains("ctrl+alt+s");
            assertThat(tools()).as("the replaced action keeps its Tools entry").contains(new MenuEntry.Item(HOSTS));
        });
    }

    /** The entries of View > Tools. */
    private java.util.List<MenuEntry> tools() {
        return contributions.menus().stream().filter(menu -> menu.target().equals(MenuTarget.standard(MenuTarget.Slot.VIEW)))
            .flatMap(menu -> menu.entries().stream())
            .filter(entry -> entry instanceof MenuEntry.Submenu submenu && submenu.title().equals("Tools"))
            .flatMap(entry -> ((MenuEntry.Submenu) entry).entries().stream()).toList();
    }

    @Test void badOrConflictingShortcutsDoNotDisplaceBuiltInKeys() throws Exception {
        start();
        reload("[shortcuts]\ntoggle_panel = 'no-such-key'\nopen_palette = 'cmd+t'\n");
        onEdt(() -> {
            var resolved = resolve(Map.of());
            assertThat(resolved.bindings().strokeFor(HOSTS)).isEmpty();
            assertThat(resolved.bindings().strokeFor(CONNECT)).isEmpty();
            assertThat(resolved.bindings().idFor(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.META_DOWN_MASK))).contains("new_tab");
            assertThat(resolved.problems()).extracting(KeyBindings.Problem::actionId).contains(HOSTS, CONNECT);
        });
    }
    @Test void sftpBindingIsBlankByDefaultAndReloadsThroughTheRemoteFile() throws Exception {
        start();
        onEdt(()-> { var initial=resolve(Map.of());assertThat(initial.bindings().strokeFor("dev.jasper.remote.sftp.toggle")).isEmpty(); });
        reload("[shortcuts]\ntoggle_sftp = 'cmd+alt+f'\n");
        onEdt(()-> { var changed=resolve(Map.of());assertThat(changed.bindings().strokeFor("dev.jasper.remote.sftp.toggle")).isPresent(); });
    }

}
