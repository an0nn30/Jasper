package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.ui.swing.KitView;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 2 Task 8a acceptance (Task 6d): the staged Remote plugin's Sessions panel, a kit Panel, mounted in a real window.
 * The window borrows the panel's kit header, which carries the panel's own title and, through {@code PanelHost.setHeader},
 * the saved-session count and the New session and Import sessions actions before Options and Hide: one title row, no
 * in-panel action row. The Add Host dialog keeps baseline f16ae32f keys through the real kit components: Enter saves
 * nothing (no default button), Escape cancels. No network, no shell, an isolated ~/.ssh, no Vault (Remote's Vault and
 * Editor requirements are optional). Runs on the test thread and hops to the EDT for each step.
 */
class StagedRemoteSessionsTest {
    static final PluginInfo REMOTE = new PluginInfo("dev.jasper.remote", "Remote", "0.12.0", Set.of(Capabilities.TERMINAL_OPEN,
        Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE, Capabilities.FILES_SOURCE));
    static final String PANEL = "dev.jasper.remote.panel";

    @TempDir Path root;

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void theSessionsHeaderKeepsItsCountAndActionsInARealWindowAndTheHostDialogKeepsItsKeys() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(REMOTE.id());
        var contributions = onEdtValue(Contributions::new);
        AuxiliaryWindows auxiliary = onEdtValue(AppContractTest::headlessWindows);
        var terminals = onEdtValue(TerminalFixture::new);
        var problems = new CopyOnWriteArrayList<String>();
        var host = AppContractTest.host(root.resolve("home"), Duration.ofMillis(200), contributions, new AtomicBoolean(true), auxiliary,
            terminals.registry, problems, new CopyOnWriteArrayList<>());
        try (var loader = new PluginClassLoader(REMOTE.id(), ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of())) {
            // The test constructor: completions on the EDT, no SSH agent, no timers and an empty, isolated ~/.ssh.
            var constructor = loader.loadClass("dev.jasper.remote.RemotePlugin")
                .getDeclaredConstructor(Executor.class, Function.class, BiFunction.class, Path.class);
            constructor.setAccessible(true);
            Plugin plugin = (Plugin) constructor.newInstance((Executor) SwingUtilities::invokeLater, (Function<Object, Optional<Object>>) context -> Optional.empty(),
                (BiFunction<Duration, Runnable, Runnable>) (delay, task) -> () -> { }, Files.createDirectories(root.resolve("ssh")));
            onEdt(() -> host.start(new HostedPlugin(REMOTE, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), Set.of("dev.jasper.remote.api"),
                loader, () -> plugin)));
            try {
                WindowContent window = onEdtValue(() -> {
                    var content = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
                    // A terminal window of the registry, so Remote's dialogs may name it as their owner.
                    content.connectTerminals(terminals.registry, () -> { });
                    content.connectContributions(contributions);
                    contributions.requestPanel(new Contributions.PanelRequest(content.id(), PANEL, Contributions.PanelRequest.Op.TOGGLE));
                    return content;
                });
                StagedVaultHost.await(() -> sessions(window) != null, "the Sessions panel is mounted in the window");
                onEdt(() -> {
                    KitView view = sessions(window);
                    var header = view.panelHeader("sessions");
                    assertThat(labels(header)).as("the title, then the count from PanelHost.setHeader").contains("Sessions", "0");
                    assertThat(names(header)).as("contributed actions before the window's own").containsExactly("New session", "Import sessions", "Options", "Hide");
                    assertThat(labels(window).stream().filter("Sessions"::equals).count()).as("one title row").isEqualTo(1);
                    assertThat(names(window).stream().filter(name -> name.equals("New session") || name.equals("Import sessions")).toList())
                        .as("no duplicate in-panel action row").containsExactly("New session", "Import sessions");
                    button(header, "New session").doClick(0);
                });
                var editor = awaitSurface(auxiliary, "Add Host");
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
                    assertThat(labels(sessions(window).panelHeader("sessions"))).contains("0");
                    press(require(editor, "host.name.input"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
                });
                StagedVaultHost.await(editor::closed, "Escape cancels the Add Host dialog");
                StagedVaultHost.settle();
                onEdt(() -> {
                    assertThat(labels(sessions(window).panelHeader("sessions"))).as("cancelling saved nothing").contains("0");
                    button(sessions(window).panelHeader("sessions"), "New session").doClick(0);
                });
                var again = awaitSurface(auxiliary, "Add Host");
                onEdt(() -> {
                    type(again, "host.name.input", "web");
                    type(again, "host.hostname.input", "web.example");
                    type(again, "host.username.input", "deploy");
                    ((AbstractButton) require(again, "host.save")).doClick(0);
                });
                StagedVaultHost.await(() -> again.closed() && labels(sessions(window).panelHeader("sessions")).contains("1"),
                    "Add Host saved the host and the header counts it");
                assertThat(problems).isEmpty();
            } finally {
                var pending = new java.util.concurrent.atomic.AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
                onEdt(() -> pending.set(host.stop()));
                java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
                onEdt(auxiliary::close);
            }
        }
    }

    private static AuxiliarySurface awaitSurface(AuxiliaryWindows auxiliary, String title) throws InterruptedException {
        StagedVaultHost.await(() -> auxiliary.open().stream().anyMatch(s -> s.title().equals(title) && !s.closed()), "surface " + title);
        return onEdtValue(() -> auxiliary.open().stream().filter(s -> s.title().equals(title) && !s.closed()).findFirst().orElseThrow());
    }

    /** The Sessions kit view mounted in the window, found by its root Panel's id. */
    private static KitView sessions(Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof KitView view && !view.isClosed() && view.spec().root().id().equals("sessions")) return view;
            if (child instanceof Container nested) { var found = sessions(nested); if (found != null) return found; }
        }
        return null;
    }

    private static Component require(AuxiliarySurface surface, String name) {
        var found = StagedVaultHost.named(surface.holder(), name);
        assertThat(found).as("component %s", name).isNotNull();
        return found;
    }

    private static void type(AuxiliarySurface surface, String name, String text) { ((JTextComponent) require(surface, name)).setText(text); }

    /** A key through Swing's real binding path: the component's own bindings, then its ancestors'. */
    private static void press(Component component, int key, char character) {
        SwingUtilities.processKeyBindings(new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key, character));
    }

    private static List<AbstractButton> buttons(Container root) {
        var result = new ArrayList<AbstractButton>();
        for (var child : root.getComponents()) {
            if (child instanceof AbstractButton button && button.isVisible()) result.add(button);
            else if (child instanceof Container nested && child.isVisible()) result.addAll(buttons(nested));
        }
        return result;
    }

    private static List<String> names(Container root) { return buttons(root).stream().map(b -> b.getAccessibleContext().getAccessibleName()).toList(); }

    private static AbstractButton button(Container root, String name) {
        return buttons(root).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    private static List<String> labels(Container root) {
        var result = new ArrayList<String>();
        for (var child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JLabel label && label.getText() != null && !label.getText().isBlank()) result.add(label.getText());
            if (child instanceof Container nested) result.addAll(labels(nested));
        }
        return result;
    }
}
