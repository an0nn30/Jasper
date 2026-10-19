package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.windows.DialogFit;
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
    static final String PANEL = "dev.jasper.remote.panel", SESSIONS = "sessions", TUNNELS_PANEL = "dev.jasper.remote.tunnels.panel", TUNNELS = "tunnels",
        SFTP_PANEL = "dev.jasper.remote.sftp.panel", SFTP = "sftp";

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
        auxiliary = onEdtValue(() -> new AuxiliaryWindows(dev.jasper.app.persistence.UiState.inMemory(), this::shell));
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
            // A Jasper window of the registry, so Remote's dialogs may name it as their owner.
            content.connectTerminals(terminals.registry, () -> { });
            content.connectContributions(contributions);
            return content;
        });
    }

    /**
     * A headless stand-in for a native dialog the host packs itself: it takes its content's preferred size when shown
     * (as {@code pack()} does) and then follows the production {@link DialogFit} rule, as {@code NativeShells} wires it.
     */
    static final class PackedDialog implements DialogFit.Target {
        final javax.swing.JComponent holder;
        java.awt.Rectangle bounds = new java.awt.Rectangle(100, 100, 0, 0);
        DialogFit fit;
        PackedDialog(javax.swing.JComponent holder) { this.holder = holder; }
        @Override public java.awt.Dimension size() { return bounds.getSize(); }
        @Override public java.awt.Dimension packedSize() { return holder.getPreferredSize(); }
        @Override public java.awt.Rectangle usableBounds() { return new java.awt.Rectangle(0, 0, 1440, 900); }
        @Override public java.awt.Point location() { return bounds.getLocation(); }
        @Override public void setBounds(java.awt.Rectangle value) { bounds = new java.awt.Rectangle(value); }
    }

    final Map<AuxiliarySurface, PackedDialog> dialogs = new java.util.HashMap<>();

    /** Plugin dialogs get a packed stand-in; every other surface touches no native window. */
    private AuxiliarySurface.Shell shell(AuxiliarySurface surface) {
        if (surface.kind() == AuxiliarySurface.Kind.DIALOG && surface.id().isEmpty()) {
            var dialog = new PackedDialog(surface.holder());
            dialog.fit = new DialogFit(surface.holder(), dialog);
            surface.fitWith(dialog.fit);
            dialogs.put(surface, dialog);
            return new AuxiliarySurface.Shell(() -> { dialog.bounds.setSize(dialog.packedSize()); dialog.fit.packed(); }, () -> { }, () -> { },
                title -> { }, () -> dialog.bounds, (title, initial) -> Optional.empty());
        }
        return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> Optional.empty());
    }

    /** The size of a shown plugin dialog's stand-in window; UI thread. */
    java.awt.Dimension dialogSize(AuxiliarySurface surface) { return dialogs.get(surface).size(); }

    /** The user drags the dialog to {@code size}; UI thread. */
    void userResize(AuxiliarySurface surface, java.awt.Dimension size) {
        var dialog = dialogs.get(surface);
        dialog.bounds.setSize(size);
        dialog.fit.resized(size);
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

    /** Has the tunnel store re-read tunnels.toml, as its file watch would (the test constructor runs no timers); UI thread. */
    void pollTunnels() throws InterruptedException {
        onEdt(() -> {
            try {
                var store = field(plugin, "tunnelStore");
                store.getClass().getMethod("poll").invoke(store);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        StagedVaultHost.settle();
    }

    /**
     * Hands the live Tunnels panel the states the manager would confirm for a connection in progress (the first saved
     * tunnel Starting at {@code stage}) and an established one (the second Up), as the plugin does on a state change,
     * without running either; UI thread. The panel's switches and dots then show those confirmed states.
     */
    void tunnelStates(String stage) {
        try {
            Object tunnelsUi = field(plugin, "tunnelsUi");
            Object panel = ((Map<?, ?>) field(tunnelsUi, "panels")).get(window.id());
            Object store = field(tunnelsUi, "store");
            var tunnels = (List<?>) store.getClass().getMethod("tunnels").invoke(store);
            Object starting = loader.loadClass("dev.jasper.remote.tunnels.TunnelState$Starting").getConstructor(String.class).newInstance(stage);
            Object counters = loader.loadClass("dev.jasper.remote.tunnels.Counters").getConstructor().newInstance();
            Object up = loader.loadClass("dev.jasper.remote.tunnels.TunnelState$Up").getConstructor(java.time.Instant.class, int.class, counters.getClass())
                .newInstance(java.time.Instant.now(), 5433, counters);
            var first = tunnels.getFirst().getClass().getMethod("id").invoke(tunnels.getFirst());
            Function<Object, Object> states = id -> id.equals(first) ? starting : up;
            Function<Object, Optional<String>> hosts = id -> Optional.empty();
            panel.getClass().getMethod("setTunnels", List.class, List.class, Optional.class, Function.class, Function.class)
                .invoke(panel, tunnels, List.of(), Optional.empty(), states, hosts);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
        return field.get(owner);
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

    /** Opens the Tunnels panel in the window and waits until its kit view is mounted (Task 8b). */
    void openTunnels() throws InterruptedException {
        onEdt(() -> contributions.requestPanel(new Contributions.PanelRequest(window.id(), TUNNELS_PANEL, Contributions.PanelRequest.Op.TOGGLE)));
        StagedVaultHost.await(() -> tunnels() != null, "the Tunnels panel is mounted in the window");
    }

    /** Opens the SFTP panel in the window and waits until its kit view is mounted (Task 8c). */
    void openSftp() throws InterruptedException {
        onEdt(() -> contributions.requestPanel(new Contributions.PanelRequest(window.id(), SFTP_PANEL, Contributions.PanelRequest.Op.TOGGLE)));
        StagedVaultHost.await(() -> sftp() != null, "the SFTP panel is mounted in the window");
    }

    /** The SFTP kit view mounted in the window; UI thread. */
    KitView sftp() { return find(window, SFTP); }

    /** The window's borrowed kit header of the SFTP panel; UI thread. */
    Container sftpHeader() { return sftp().panelHeader(SFTP); }

    /** The window's live SFTP explorer (the plugin's {@code SftpPanel}); UI thread. */
    Object sftpPanel() {
        try {
            Object sftpUi = field(plugin, "sftpUi");
            var panel = sftpUi.getClass().getDeclaredMethod("panel", java.util.UUID.class); panel.setAccessible(true);
            return panel.invoke(sftpUi, window.id());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** A Remote {@code FileEntry}: {@code kind} is FILE, DIRECTORY or LINK. */
    Object fileEntry(String name, String kind, long size, long modifiedMillis) {
        try {
            Class<?> entry = loader.loadClass("dev.jasper.remote.sftp.FileEntry");
            @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) loader.loadClass("dev.jasper.remote.sftp.FileEntry$Kind"), kind);
            return entry.getConstructor(String.class, value.getClass(), long.class, long.class, int.class, String.class)
                .newInstance(name, value, size, modifiedMillis, kind.equals("DIRECTORY") ? 0755 : 0644, "");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /**
     * Shows a listing in the live SFTP explorer as its controller does after a successful list (no connection): the host
     * name, the folder and its entries; UI thread.
     */
    void sftpListing(String host, String directory, List<Object> entries) {
        try {
            Object panel = sftpPanel();
            panel.getClass().getMethod("showPage", String.class, String.class, List.class, long.class, long.class, boolean.class)
                .invoke(panel, host, directory, entries, 0L, (long) entries.size(), false);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Sends files to {@code activated} on Enter or a double click, as the Editor integration would; UI thread. */
    void sftpEditing(java.util.function.Consumer<Object> activated, Runnable open) {
        try {
            Object panel = sftpPanel();
            Class<?> editing = loader.loadClass("dev.jasper.remote.ui.sftp.SftpPanel$Editing");
            Object value = editing.getConstructor(java.util.function.Consumer.class, Runnable.class).newInstance(activated, open);
            panel.getClass().getMethod("editing", editing).invoke(panel, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Stops the transfer UI's own refresh and waits for one in flight, so only rows handed in by {@link #transferRows} show. */
    void quietTransfers() throws InterruptedException {
        onEdt(() -> {
            try {
                Object timer = field(field(plugin, "transferUi"), "timer");
                if (timer != null) timer.getClass().getMethod("stop").invoke(timer);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        StagedVaultHost.await(() -> {
            try { return !(boolean) field(field(plugin, "transferUi"), "pending"); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }, "no transfer refresh in flight");
        StagedVaultHost.settle();
    }

    /**
     * Hands the window's transfer rows to its SFTP sidebar as the transfer UI does after a refresh, with the queue's own
     * refresh stopped first ({@link #quietTransfers}) so only these rows show; each row is {@code {id, title, status, fraction or -1, finished}}.
     */
    void transferRows(List<Object[]> rows) {
        try {
            Object transferUi = field(plugin, "transferUi");
            Object strip = ((Map<?, ?>) field(transferUi, "strips")).get(window.id());
            Class<?> row = loader.loadClass("dev.jasper.remote.ui.transfers.TransferRows$Row");
            var constructor = row.getConstructor(java.util.UUID.class, String.class, String.class, String.class, String.class, java.util.OptionalDouble.class,
                boolean.class, Optional.class, boolean.class, boolean.class);
            var values = new ArrayList<Object>();
            for (Object[] r : rows) {
                double fraction = (double) r[3];
                values.add(constructor.newInstance(r[0], "↑", r[1], "Local: /fixture/" + r[1] + " → prod: /srv", r[2],
                    fraction < 0 ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(fraction), false, Optional.empty(), r[4], false));
            }
            strip.getClass().getMethod("rows", List.class).invoke(strip, values);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** The plugin's data directory under the test's {@code root} (the contract host resolves it by id), where tunnels.toml lives. */
    static Path data(Path root) { return root.resolve("home").resolve(REMOTE.id()); }

    /** The Sessions kit view mounted in the window, found by its root Panel's id; UI thread. */
    KitView sessions() { return find(window, SESSIONS); }

    /** The Tunnels kit view mounted in the window; UI thread. */
    KitView tunnels() { return find(window, TUNNELS); }

    /** The window's borrowed kit header of the Tunnels panel; UI thread. */
    Container tunnelsHeader() { return tunnels().panelHeader(TUNNELS); }

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

    private static KitView find(Container root, String id) {
        for (var child : root.getComponents()) {
            if (child instanceof KitView view && !view.isClosed() && view.spec().root().id().equals(id)) return view;
            if (child instanceof Container nested) { var found = find(nested, id); if (found != null) return found; }
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
