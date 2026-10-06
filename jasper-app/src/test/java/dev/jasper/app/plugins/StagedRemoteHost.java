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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The staged Remote jar in the application's real plugin host, its Sessions panel mounted in a real window (Plan 2
 * Task 8a, the {@link StagedVaultHost} shape). The plugin uses its test constructor: completions on the EDT, no SSH agent,
 * no timers and an isolated, empty ~/.ssh under the test's directory; no Vault or Editor (both optional), no network and
 * no shell. Call from the test thread, not the EDT.
 */
final class StagedRemoteHost implements AutoCloseable {
    static final PluginInfo REMOTE = new PluginInfo("dev.jasper.remote", "Remote", "0.12.0", Set.of(Capabilities.TERMINAL_OPEN,
        Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE, Capabilities.FILES_SOURCE));
    static final String PANEL = "dev.jasper.remote.panel", SESSIONS = "sessions";

    final Contributions contributions;
    final AuxiliaryWindows auxiliary;
    final TerminalFixture terminals;
    final PluginHost host;
    final PluginClassLoader loader;
    final List<String> problems = new CopyOnWriteArrayList<>();
    final Path ssh;
    final WindowContent window;
    final Plugin plugin;

    StagedRemoteHost(Path data, boolean dark) throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(REMOTE.id());
        ssh = Files.createDirectories(data.resolve("ssh"));
        contributions = onEdtValue(Contributions::new);
        auxiliary = onEdtValue(AppContractTest::headlessWindows);
        terminals = onEdtValue(TerminalFixture::new);
        host = AppContractTest.host(data.resolve("home"), Duration.ofMillis(200), contributions, new AtomicBoolean(dark), auxiliary,
            terminals.registry, problems, new CopyOnWriteArrayList<>());
        loader = new PluginClassLoader(REMOTE.id(), ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of());
        var constructor = loader.loadClass("dev.jasper.remote.RemotePlugin").getDeclaredConstructor(Executor.class, Function.class, BiFunction.class, Path.class);
        constructor.setAccessible(true);
        plugin = (Plugin) constructor.newInstance((Executor) SwingUtilities::invokeLater, (Function<Object, Optional<Object>>) context -> Optional.empty(),
            (BiFunction<Duration, Runnable, Runnable>) (delay, task) -> () -> { }, ssh);
        onEdt(() -> host.start(new HostedPlugin(REMOTE, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), Set.of("dev.jasper.remote.api"),
            loader, () -> plugin)));
        window = onEdtValue(() -> {
            var content = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            // A terminal window of the registry, so Remote's dialogs may name it as their owner.
            content.connectTerminals(terminals.registry, () -> { });
            content.connectContributions(contributions);
            return content;
        });
    }

    /** Opens the Sessions panel in the window and waits until its kit view is mounted. */
    void openSessions() throws InterruptedException {
        onEdt(() -> contributions.requestPanel(new Contributions.PanelRequest(window.id(), PANEL, Contributions.PanelRequest.Op.TOGGLE)));
        StagedVaultHost.await(() -> sessions() != null, "the Sessions panel is mounted in the window");
    }

    /**
     * Drives the live Sessions panel's session counts and credential captions directly (its test accessor), as the
     * plugin does when sessions open or the Vault locks, without a connection or a Vault; UI thread.
     */
    void sessionsState(java.util.function.ToIntFunction<Object> sessions, String credential) {
        try {
            // The plugin's per-window panel map: its method table names optional Vault types, which are absent here.
            var panels = plugin.getClass().getDeclaredField("panels"); panels.setAccessible(true);
            Object panel = ((Map<?, ?>) panels.get(plugin)).get(window.id());
            panel.getClass().getMethod("setCredentialLabels", Function.class).invoke(panel, (Function<Object, String>) host -> credential);
            panel.getClass().getMethod("setHostDetails", Function.class, java.util.function.ToIntFunction.class)
                .invoke(panel, (Function<Object, String>) host -> "Ubuntu 24.04", sessions);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** The plugin's own context (its kit, windows and terminals), for mounting its views as the plugin does. */
    dev.jasper.sdk.plugin.PluginContext context() {
        try {
            var field = plugin.getClass().getDeclaredField("context"); field.setAccessible(true);
            return (dev.jasper.sdk.plugin.PluginContext) field.get(plugin);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /**
     * Asks the plugin's host-key question for a synthetic key, as a connection would, through its package-private entry.
     * A method handle resolves just that method; plain reflection would load every signature, and Vault types are absent.
     */
    CompletableFuture<?> askHostKey(String host, int port, String type, String fingerprint) {
        try {
            Class<?> question = loader.loadClass("dev.jasper.remote.client.HostKeyVerifier$Question");
            Object asked = question.getConstructor(String.class, int.class, String.class, String.class).newInstance(host, port, type, fingerprint);
            var lookup = java.lang.invoke.MethodHandles.privateLookupIn(plugin.getClass(), java.lang.invoke.MethodHandles.lookup());
            var ask = lookup.findVirtual(plugin.getClass(), "askHostKey",
                java.lang.invoke.MethodType.methodType(CompletableFuture.class, dev.jasper.sdk.terminal.WindowHandle.class, question));
            var owner = onEdtValue(() -> context().terminals().window(window.id()).orElseThrow());
            return (CompletableFuture<?>) ask.invoke(plugin, owner, asked);
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }

    /** Waits until the surface's content holds a component with this kit name (a mounted, laid-out view). */
    static void awaitNamed(AuxiliarySurface surface, String name) throws InterruptedException {
        StagedVaultHost.await(() -> StagedVaultHost.named(surface.holder(), name) != null, name + " in " + surface.title());
    }

    /** The Sessions kit view mounted in the window, found by its root Panel's id; UI thread. */
    KitView sessions() { return find(window); }

    /** The window's borrowed kit header of the Sessions panel; UI thread. */
    Container header() { return sessions().panelHeader(SESSIONS); }

    AuxiliarySurface awaitSurface(String title) throws InterruptedException {
        StagedVaultHost.await(() -> auxiliary.open().stream().anyMatch(s -> s.title().equals(title) && !s.closed()), "surface " + title);
        return onEdtValue(() -> auxiliary.open().stream().filter(s -> s.title().equals(title) && !s.closed()).findFirst().orElseThrow());
    }

    /** Adds an SSH-agent host through the header's New session action and the Add Host dialog, as a user would. */
    void addHost(String name, String hostname, String user) throws InterruptedException {
        String before = onEdtValue(() -> String.join("|", labels(header())));
        onEdt(() -> button(header(), "New session").doClick(0));
        var editor = awaitSurface("Add Host");
        onEdt(() -> {
            type(editor, "host.name.input", name); type(editor, "host.hostname.input", hostname); type(editor, "host.username.input", user);
            ((AbstractButton) require(editor, "host.save")).doClick(0);
        });
        StagedVaultHost.await(() -> editor.closed() && !String.join("|", labels(header())).equals(before), "Add Host saved " + name);
    }

    private static KitView find(Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof KitView view && !view.isClosed() && view.spec().root().id().equals(SESSIONS)) return view;
            if (child instanceof Container nested) { var found = find(nested); if (found != null) return found; }
        }
        return null;
    }

    static Component require(AuxiliarySurface surface, String name) {
        var found = StagedVaultHost.named(surface.holder(), name);
        assertThat(found).as("component %s", name).isNotNull();
        return found;
    }

    static void type(AuxiliarySurface surface, String name, String text) { ((JTextComponent) require(surface, name)).setText(text); }

    /** A key through Swing's real binding path: the component's own bindings, then its ancestors'. */
    static void press(Component component, int key, char character) {
        SwingUtilities.processKeyBindings(new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key, character));
    }

    static List<AbstractButton> buttons(Container root) {
        var result = new ArrayList<AbstractButton>();
        for (var child : root.getComponents()) {
            if (child instanceof AbstractButton button && button.isVisible()) result.add(button);
            else if (child instanceof Container nested && child.isVisible()) result.addAll(buttons(nested));
        }
        return result;
    }

    static List<String> names(Container root) { return buttons(root).stream().map(b -> b.getAccessibleContext().getAccessibleName()).toList(); }

    static AbstractButton button(Container root, String name) {
        return buttons(root).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    static List<String> labels(Container root) {
        var result = new ArrayList<String>();
        for (var child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JLabel label && label.getText() != null && !label.getText().isBlank()) result.add(label.getText());
            if (child instanceof Container nested) result.addAll(labels(nested));
        }
        return result;
    }

    @Override public void close() throws Exception {
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        try {
            onEdt(() -> pending.set(host.stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } finally {
            onEdt(auxiliary::close);
            loader.close();
            DesktopTestSupport.closeOwners();
        }
    }
}
