package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The staged Vault jar in the application's real plugin host, with headless windows and a terminal fixture, driven by
 * kit component names. Unbound by construction ({@link UnboundVault}). Reusable shape for Tasks 8–10: stage, start in
 * the real host, drive by kit names, inspect the mounted kit view. Call from the test thread, not the EDT.
 */
final class StagedVaultHost implements AutoCloseable {
    static final PluginInfo VAULT = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.2.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE));
    final Contributions contributions;
    final AuxiliaryWindows auxiliary;
    final TerminalFixture terminals;
    final PluginHost host;
    final PluginClassLoader loader;
    final List<String> problems = new CopyOnWriteArrayList<>();
    final UUID window;

    StagedVaultHost(Path data, boolean dark) throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(UnboundVault.ID);
        UnboundVault.settings(data);
        contributions = onEdtValue(Contributions::new);
        auxiliary = onEdtValue(AppContractTest::headlessWindows);
        terminals = onEdtValue(TerminalFixture::new);
        host = AppContractTest.host(data, Duration.ofMillis(200), contributions, new AtomicBoolean(dark), auxiliary, terminals.registry,
            problems, new CopyOnWriteArrayList<>());
        loader = new PluginClassLoader(UnboundVault.ID, ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of());
        Plugin plugin = (Plugin) loader.loadClass("dev.jasper.vault.VaultPlugin").getConstructor().newInstance();
        var opened = new AtomicReference<UUID>();
        onEdt(() -> {
            host.start(new HostedPlugin(VAULT, Set.of(), Set.of(), Set.of("dev.jasper.vault.api"), loader, () -> plugin));
            opened.set(terminals.addWindow()); terminals.activateWindow(opened.get());
        });
        window = opened.get();
    }

    /** Starts another plugin (on the test class loader) after the Vault, for example an API consumer. */
    void start(PluginInfo info, Set<String> requires, Plugin plugin) {
        onEdt(() -> host.start(new HostedPlugin(info, requires, Set.of(), Set.of(), getClass().getClassLoader(), () -> plugin)));
    }

    void invoke(String action) {
        onEdt(() -> assertThat(contributions.action(action)).as(action).hasValueSatisfying(entry ->
            entry.invoke(new Contributions.Invocation(window, Optional.empty()))));
    }

    AuxiliarySurface surfaceOrNull(String title) {
        return onEdtValue(() -> auxiliary.open().stream().filter(s -> s.title().equals(title) && !s.closed()).findFirst().orElse(null));
    }

    AuxiliarySurface surface(String title) {
        var found = surfaceOrNull(title);
        assertThat(found).as("open surface %s", title).isNotNull();
        return found;
    }

    AuxiliarySurface awaitSurface(String title) throws InterruptedException {
        await(() -> auxiliary.open().stream().anyMatch(s -> s.title().equals(title) && !s.closed()), "surface " + title);
        return surface(title);
    }

    /** Polls a UI-thread condition while the host's background work and UI completions run. */
    static void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!onEdtValue(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out: " + description);
            Thread.sleep(20);
        }
    }

    /** Lets queued background work and UI completions run, for asserting that nothing happened. */
    static void settle() throws InterruptedException { for (int i = 0; i < 10; i++) { Thread.sleep(20); onEdt(() -> { }); } }

    /** Creates an unbound vault through the create dialog; the manager opens. */
    void createVault(String password) throws InterruptedException {
        invoke("dev.jasper.vault.open");
        var create = surface("Create Vault");
        onEdt(() -> {
            UnboundVault.assertUnbound(kitView(create));
            type(create, "password.input", password); type(create, "confirm.input", password);
            UnboundVault.assertUnbound(kitView(create));
            click(create, "submit");
        });
        awaitSurface("Credential Vault");
    }

    static Component named(Container root, String name) {
        if (name.equals(root.getName())) return root;
        for (var child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) { var found = named(nested, name); if (found != null) return found; }
        }
        return null;
    }

    static Component require(AuxiliarySurface surface, String name) {
        var found = named(surface.holder(), name);
        assertThat(found).as("component %s", name).isNotNull();
        return found;
    }

    static void click(AuxiliarySurface surface, String name) { ((AbstractButton) require(surface, name)).doClick(0); }

    static void type(AuxiliarySurface surface, String name, String text) { ((JTextComponent) require(surface, name)).setText(text); }

    /** Presses Enter on {@code component} through Swing's real key-binding path (its own, then its ancestors' bindings). */
    static void enter(Component component) {
        SwingUtilities.processKeyBindings(new java.awt.event.KeyEvent(component, java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, '\n'));
    }

    /** The descendant of a kit control that owns its keyboard bindings (for a row list, its focusable canvas). */
    static Component keyTarget(Component control) {
        var bound = first(control, c -> c instanceof JComponent j && j.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER")) != null);
        if (bound != null) return bound;
        var focusable = first(control, c -> c instanceof JComponent j && j.isFocusable());
        return focusable != null ? focusable : control;
    }

    private static Component first(Component root, java.util.function.Predicate<Component> test) {
        if (test.test(root)) return root;
        if (root instanceof Container container) for (var child : container.getComponents()) { var found = first(child, test); if (found != null) return found; }
        return null;
    }

    static dev.jasper.ui.swing.KitView kitView(Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof dev.jasper.ui.swing.KitView view) return view;
            if (child instanceof Container nested) { var found = kitView(nested); if (found != null) return found; }
        }
        return null;
    }

    static dev.jasper.ui.swing.KitView kitView(AuxiliarySurface surface) {
        var found = kitView(surface.holder());
        assertThat(found).as("kit view in %s", surface.title()).isNotNull();
        return found;
    }

    /** A node of the surface's live descriptor, through the containers Vault views use. */
    static dev.jasper.ui.model.Nodes.Node node(AuxiliarySurface surface, String id) {
        return find(kitView(surface).spec().root(), id);
    }

    static dev.jasper.ui.model.Nodes.Node find(dev.jasper.ui.model.Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<dev.jasper.ui.model.Nodes.Node> children = switch (node) {
            case dev.jasper.ui.model.Nodes.Form n -> {
                var all = new java.util.ArrayList<dev.jasper.ui.model.Nodes.Node>(n.sections()); n.preview().ifPresent(all::add); all.addAll(n.actions()); yield all;
            }
            case dev.jasper.ui.model.Nodes.Stack n -> n.children();
            case dev.jasper.ui.model.Nodes.SettingRow n -> List.of(n.control());
            case dev.jasper.ui.model.Nodes.Empty n -> n.actions().stream().map(b -> (dev.jasper.ui.model.Nodes.Node) b).toList();
            default -> List.of();
        };
        for (var child : children) { var found = find(child, id); if (found != null) return found; }
        return null;
    }

    @Override public void close() throws Exception {
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        try {
            onEdt(() -> pending.set(host.stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
        } finally {
            onEdt(auxiliary::close);
            loader.close();
        }
    }
}
