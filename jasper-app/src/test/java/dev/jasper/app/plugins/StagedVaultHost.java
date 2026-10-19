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
    final Plugin plugin;
    final List<String> problems = new CopyOnWriteArrayList<>();
    /** The folders the host's file chooser answers, in order; it cancels when none is queued. */
    final java.util.Deque<Path> chosenFolders = new java.util.concurrent.ConcurrentLinkedDeque<>();
    final UUID window;
    /** The staged Enterprise plugin's loader in Enterprise mode (plan 10b), else null. */
    final PluginClassLoader enterpriseLoader;
    /** The main window's workspace: the Vault's manager opens there as a tab (workspace tabs spec 1 decision 2). */
    final JComponent workspace;

    StagedVaultHost(Path data, boolean dark) throws Exception { this(data, dark, null); }

    StagedVaultHost(Path data, boolean dark, WorkspaceAppearanceFixture appearance) throws Exception { this(data, dark, appearance, false); }

    /**
     * {@code enterprise}: the staged Enterprise plugin starts first in the same host and the Vault may see its API, so it
     * composes its classic layout (plan 10b). The caller installs the Enterprise kit and theme first
     * ({@code EnterpriseLook.controller}).
     */
    StagedVaultHost(Path data, boolean dark, WorkspaceAppearanceFixture appearance, boolean enterprise) throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(UnboundVault.ID);
        UnboundVault.settings(data);
        contributions = onEdtValue(Contributions::new);
        auxiliary = onEdtValue(() -> AppContractTest.headlessWindows((choice, cancellation) -> {
            Path next = chosenFolders.poll();
            return next == null ? List.of() : List.of(dev.jasper.app.files.Location.local(next));
        }));
        terminals = onEdtValue(TerminalFixture::new);
        workspace = onEdtValue(() -> new javax.swing.JPanel(new java.awt.BorderLayout()));
        host = AppContractTest.host(data, Duration.ofMillis(200), contributions, new AtomicBoolean(dark), auxiliary, terminals.registry,
            new NotificationsFixture(problems), new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>(),
            PluginRuntime.ShellProfiles.NONE, new CopyOnWriteArrayList<>(), appearance, GlassSource.NONE);
        enterpriseLoader = enterprise ? StagedEnterprise.start(host) : null;
        loader = new PluginClassLoader(UnboundVault.ID, ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(),
            enterprise ? Map.of("dev.jasper.enterprise.api", enterpriseLoader) : Map.of());
        plugin = (Plugin) loader.loadClass("dev.jasper.vault.VaultPlugin").getConstructor().newInstance();
        var opened = new AtomicReference<UUID>();
        onEdt(() -> {
            host.start(new HostedPlugin(VAULT, Set.of(), enterprise ? Set.of("dev.jasper.enterprise") : Set.of(), Set.of("dev.jasper.vault.api"),
                loader, () -> plugin));
            opened.set(terminals.addMainWindow()); terminals.activateWindow(opened.get());
            contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(opened.get(),
                tab -> workspace.add(tab.component()), tab -> workspace.remove(tab.component()), tab -> tab.active(true), () -> { }));
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

    /** The open manager, the Vault's Credential Vault tab. UI thread. */
    private Optional<dev.jasper.app.contributions.Tab> managerTab() {
        return contributions.tabs().hosted().stream().filter(tab -> tab.title().equals("Credential Vault") && !tab.closed()).findFirst();
    }

    /** The manager tab's component, its kit view, while it is open; else null. */
    JComponent managerOrNull() { return onEdtValue(() -> managerTab().map(dev.jasper.app.contributions.Tab::component).orElse(null)); }

    JComponent manager() {
        var found = managerOrNull();
        assertThat(found).as("open manager tab").isNotNull();
        return found;
    }

    JComponent awaitManager() throws InterruptedException {
        await(() -> managerOrNull() != null, "the manager tab");
        return manager();
    }

    /** Closes the manager tab at once, as stopping the plugin does. */
    void closeManager() { onEdt(() -> managerTab().ifPresent(dev.jasper.app.contributions.Tab::close)); }

    /** Closes the manager tab as Close Tab does, through its plugin's guard. */
    void requestCloseManager() { onEdt(() -> managerTab().ifPresent(dev.jasper.app.contributions.Tab::requestClose)); }

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
        awaitManager();
    }

    /** Real synthetic domain writes and a reviewed managed-key import, never descriptor-only sample rows. */
    void seedReferenceItems(Path keys) throws Exception { seedReferenceItems(keys, ignored -> {}); }
    void seedReferenceItems(Path keys, java.util.function.Consumer<AuxiliarySurface> readyImport) throws Exception {
        Class<?> algorithm = loader.loadClass("dev.jasper.vault.keygen.KeyAlgorithm");
        Class<?> generator = loader.loadClass("dev.jasper.vault.keygen.KeyGenerator");
        Object gen = generator.getConstructor(Path.class).newInstance(keys);
        var generate = generator.getMethod("generate", algorithm, String.class, String.class, char[].class);
        Object diskKey = generate.invoke(gen, algorithm.getField("ED25519").get(null), "id_ed25519", "synthetic@fixture", new char[0]);
        Object storedKey = generate.invoke(gen, algorithm.getField("RSA_4096").get(null), "work_rsa", "synthetic-deploy", new char[0]);
        var managerField = plugin.getClass().getDeclaredField("manager"); managerField.setAccessible(true);
        Object manager = onEdtValue(() -> { try { return managerField.get(plugin); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } });
        Class<?> account = loader.loadClass("dev.jasper.vault.model.Account"), auth = loader.loadClass("dev.jasper.vault.model.Auth");
        Class<?> password = loader.loadClass("dev.jasper.vault.model.Auth$Password"), keyAuth = loader.loadClass("dev.jasper.vault.model.Auth$Key");
        var epoch = java.time.Instant.parse("2026-10-02T12:00:00Z");
        UUID selected = UUID.randomUUID();
        Object login = account.getConstructor(UUID.class,String.class,String.class,auth,java.time.Instant.class,java.time.Instant.class)
            .newInstance(selected,"amp","ubuntu",password.getConstructor(char[].class).newInstance((Object)"synthetic-password".toCharArray()),epoch,epoch);
        Object other = account.getConstructor(UUID.class,String.class,String.class,auth,java.time.Instant.class,java.time.Instant.class)
            .newInstance(UUID.randomUUID(),"Proxmox","root",keyAuth.getConstructor(Path.class,char[].class).newInstance(diskKey.getClass().getMethod("privatePath").invoke(diskKey),null),epoch,epoch);
        invokeSave(manager,"saveAccount",account,login); invokeSave(manager,"saveAccount",account,other);
        invokeSave(manager,"importKey",diskKey.getClass(),diskKey);
        Class<?> note = loader.loadClass("dev.jasper.vault.model.Note");
        invokeSave(manager,"saveNote",note,note.getConstructor(UUID.class,String.class,char[].class,java.time.Instant.class)
            .newInstance(UUID.randomUUID(),"Router recovery codes","synthetic recovery codes".toCharArray(),epoch));
        Class<?> apiType = loader.loadClass("dev.jasper.vault.api.VaultApi");
        var consumer = new AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
        var api = new AtomicReference<Object>();
        start(new PluginInfo("dev.jasper.fixture","Fixture", "0.1.0",Set.of()), Set.of(UnboundVault.ID), context -> { consumer.set(context); api.set(context.services().require(apiType)); });
        Class<?> source = loader.loadClass("dev.jasper.vault.api.SshKeySource");
        Object request = source.getConstructor(UUID.class,String.class,Path.class).newInstance(UUID.randomUUID(),"work_rsa",storedKey.getClass().getMethod("privatePath").invoke(storedKey));
        var pending = onEdtValue(() -> {
            try { return (CompletableFuture<?>)apiType.getMethod("importSshKeys",dev.jasper.sdk.terminal.WindowHandle.class,List.class,List.class)
                .invoke(api.get(),consumer.get().terminals().window(window).orElseThrow(),List.of(request),List.of()); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        var review = awaitSurface("Import SSH keys");
        await(() -> node(review,"commit").state().enabled(),"synthetic managed key ready");
        readyImport.accept(review);
        onEdt(() -> click(review,"commit")); pending.get(20, java.util.concurrent.TimeUnit.SECONDS);
        await(() -> review.closed(),"managed import closed");
        var managerTabField = plugin.getClass().getDeclaredField("managerTab"); managerTabField.setAccessible(true);
        onEdt(() -> {
            try {
                Object managerTab = managerTabField.get(plugin);
                managerTab.getClass().getMethod("show",dev.jasper.sdk.terminal.WindowHandle.class,Optional.class)
                    .invoke(managerTab,consumer.get().terminals().window(window).orElseThrow(),Optional.of(selected));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            var rows = (dev.jasper.ui.model.Nodes.ItemList)node(manager(),"rows");
            assertThat(rows.items()).hasSize(5);
            assertThat(rows.selectedIds()).containsExactly(selected.toString());
        });
    }

    private static void invokeSave(Object manager, String method, Class<?> type, Object value) throws Exception {
        var result = onEdtValue(() -> {
            try { return (CompletableFuture<?>)manager.getClass().getMethod(method,type).invoke(manager,value); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        result.get(20,java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Saves synthetic logins ({name, username, password}) through the Vault's own manager, as an editor's Save does (plan 10b). */
    List<UUID> seedLogins(String[][] logins) throws Exception {
        var managerField = plugin.getClass().getDeclaredField("manager"); managerField.setAccessible(true);
        Object manager = onEdtValue(() -> { try { return managerField.get(plugin); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } });
        Class<?> account = loader.loadClass("dev.jasper.vault.model.Account"), auth = loader.loadClass("dev.jasper.vault.model.Auth");
        Class<?> password = loader.loadClass("dev.jasper.vault.model.Auth$Password");
        var modified = java.time.Instant.parse("2026-09-24T12:00:00Z");
        var ids = new java.util.ArrayList<UUID>();
        for (String[] login : logins) {
            UUID id = UUID.randomUUID(); ids.add(id);
            Object value = account.getConstructor(UUID.class, String.class, String.class, auth, java.time.Instant.class, java.time.Instant.class)
                .newInstance(id, login[0], login[1], password.getConstructor(char[].class).newInstance((Object) login[2].toCharArray()), modified, modified);
            invokeSave(manager, "saveAccount", account, value);
        }
        settle();
        return List.copyOf(ids);
    }

    /** Delivers {@code event} to the open manager's view, as its kit would from a click or a key (plan 10b). */
    void dispatchToManager(dev.jasper.sdk.ui.UiEvent event) throws InterruptedException {
        onEdt(() -> {
            try {
                Object managerTab = field(plugin, "managerTab"), panel = field(managerTab, "panel");
                var view = panel.getClass().getDeclaredMethod("view"); view.setAccessible(true);
                ((HostedKitView) view.invoke(panel)).dispatch(event);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        settle();
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var declared = owner.getClass().getDeclaredField(name); declared.setAccessible(true); return declared.get(owner);
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

    /** As the surface forms above, inside a tab's component, such as the manager's. */
    static Component require(Container root, String name) {
        var found = named(root, name);
        assertThat(found).as("component %s", name).isNotNull();
        return found;
    }

    static void click(Container root, String name) { ((AbstractButton) require(root, name)).doClick(0); }

    static void type(Container root, String name, String text) { ((JTextComponent) require(root, name)).setText(text); }
    /** Spec 13: a File Server folder is read-only, so tests choose it: queue it, then press Choose…. EDT. */
    static void chooseFolder(StagedVaultHost host, AuxiliarySurface surface, Path folder) {
        host.chosenFolders.add(folder); click(surface, "server.folder.choose");
    }

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
        // A tab's component is its kit view.
        if (root instanceof dev.jasper.ui.swing.KitView view) return view;
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

    /** A node of the live descriptor of the kit view in {@code root}, such as the manager tab's. */
    static dev.jasper.ui.model.Nodes.Node node(Container root, String id) {
        return find(kitView(root).spec().root(), id);
    }

    static dev.jasper.ui.model.Nodes.Node find(dev.jasper.ui.model.Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<dev.jasper.ui.model.Nodes.Node> children = switch (node) {
            case dev.jasper.ui.model.Nodes.Form n -> {
                var all = new java.util.ArrayList<dev.jasper.ui.model.Nodes.Node>(n.sections()); n.hero().ifPresent(all::add); n.preview().ifPresent(all::add); all.addAll(n.actions()); yield all;
            }
            case dev.jasper.ui.model.Nodes.Stack n -> n.children();
            case dev.jasper.ui.model.Nodes.Split n -> n.children();
            case dev.jasper.ui.model.Nodes.Panel n -> {
                var all = new java.util.ArrayList<dev.jasper.ui.model.Nodes.Node>(); all.add(n.header()); n.searchOrNavigation().ifPresent(all::add); all.add(n.body()); n.footer().ifPresent(all::add); yield all;
            }
            case dev.jasper.ui.model.Nodes.Card n -> n.children();
            case dev.jasper.ui.model.Nodes.Section n -> List.of(n.body());
            case dev.jasper.ui.model.Nodes.SettingRow n -> List.of(n.control());
            case dev.jasper.ui.model.Nodes.FormRow n -> { var rowChildren = new java.util.ArrayList<dev.jasper.ui.model.Nodes.Node>(); rowChildren.add(n.control());n.trailing().ifPresent(rowChildren::add);yield rowChildren; }
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
            try { loader.close(); } finally { if (enterpriseLoader != null) enterpriseLoader.close(); }
        }
    }
}
