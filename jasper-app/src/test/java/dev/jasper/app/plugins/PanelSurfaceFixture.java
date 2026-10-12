package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.contributions.PanelHeaderState;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.PanelHostHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import javax.swing.*;
import static dev.jasper.app.plugins.AppContractTest.*;

/**
 * Actual staged Remote (Tunnels, SFTP) or File Server panel owners mounted through their real factories and the app's
 * host adapters, beside an actual locked synthetic Vault. Hosts, tunnels and servers are fixture data put through the
 * plugins' own stores; nothing connects, listens or starts. Header placement actions are the dock's More and Collapse.
 */
public final class PanelSurfaceFixture implements AutoCloseable {
    public static final String TUNNELS = "dev.jasper.remote.tunnels.panel", SFTP = "dev.jasper.remote.sftp.panel", FILE_SERVERS = "dev.jasper.fileserver.panel";
    private final StagedVaultHost vault;
    private final WorkspaceAppearanceFixture appearance;
    private final PluginClassLoader loader;
    private final Plugin plugin;
    private final PanelSite site;
    private final KitView view;
    private final String root;
    private final Path home;
    private PanelHeaderState header = PanelHeaderState.EMPTY;

    public PanelSurfaceFixture(Path home, ResolvedTheme theme, String panel) throws Exception {
        this.home = home;
        appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(theme));
        vault = new StagedVaultHost(home, theme.chrome().dark(), appearance);
        vault.createVault("synthetic-panels-password");
        onEdt(() -> vault.surface("Credential Vault").requestClose());
        vault.invoke("dev.jasper.vault.lock");
        boolean files = panel.equals(FILE_SERVERS);
        String id = files ? "dev.jasper.fileserver" : "dev.jasper.remote";
        var staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(id);
        loader = new PluginClassLoader(id, ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of("dev.jasper.vault.api", vault.loader));
        if (files) {
            var pluginClass = loader.loadClass("dev.jasper.fileserver.FileServerPlugin");
            var environmentClass = loader.loadClass("dev.jasper.fileserver.FileServerPlugin$Environment");
            var environment = environmentClass.getDeclaredConstructor(java.util.concurrent.Executor.class, Supplier.class, IntPredicate.class,
                Consumer.class, Consumer.class, Path.class, String.class, java.time.ZoneId.class, BiFunction.class);
            environment.setAccessible(true);
            Object settings = environment.newInstance((java.util.concurrent.Executor) SwingUtilities::invokeLater, (Supplier<List<?>>) List::of,
                (IntPredicate) port -> true, (Consumer<String>) text -> { }, (Consumer<Path>) path -> { }, home, "Mac OS X", java.time.ZoneOffset.UTC,
                (BiFunction<java.time.Duration, Runnable, Runnable>) (delay, task) -> () -> { });
            var constructor = pluginClass.getDeclaredConstructor(environmentClass); constructor.setAccessible(true);
            plugin = (Plugin) constructor.newInstance(settings);
            onEdt(() -> vault.host.start(new HostedPlugin(FileServerRenderEvidenceTest.INFO, Set.of(), Set.of("dev.jasper.vault"), Set.of(), loader, () -> plugin)));
        } else {
            var ssh = Files.createDirectories(home.resolve("synthetic-ssh"));
            var constructor = loader.loadClass("dev.jasper.remote.RemotePlugin").getDeclaredConstructor(java.util.concurrent.Executor.class, Function.class, BiFunction.class, Path.class);
            constructor.setAccessible(true);
            plugin = (Plugin) constructor.newInstance((java.util.concurrent.Executor) SwingUtilities::invokeLater,
                (Function<Object, Optional<Object>>) c -> Optional.empty(), (BiFunction<java.time.Duration, Runnable, Runnable>) (delay, task) -> () -> { }, ssh);
            onEdt(() -> vault.host.start(new HostedPlugin(StagedRemoteHost.REMOTE, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), Set.of("dev.jasper.remote.api"), loader, () -> plugin)));
        }
        StagedVaultHost.settle();
        root = panel.equals(TUNNELS) ? "tunnels" : panel.equals(SFTP) ? "sftp" : "fileserver.panel";
        var holder = new KitView[1];
        site = onEdtValue(() -> new PanelSite(vault.window, () -> { }, () -> { }, () -> true, state -> { header = state; if (holder[0] != null) hostHeader(holder[0]); }));
        view = onEdtValue(() -> (KitView) vault.contributions.panels().stream().filter(p -> p.id().equals(panel)).findFirst().orElseThrow().factory().apply(site));
        holder[0] = view;
        onEdt(() -> {
            zone(theme);
            hostHeader(view);
            view.setPanelHostActions(root, List.of(
                new Nodes.Button("host.options", ControlState.READY, "", "more", Nodes.ButtonKind.GHOST, "host.options", "Options"),
                new Nodes.Button("host.hide", ControlState.READY, "", "collapse", Nodes.ButtonKind.GHOST, "host.hide", "Hide")), e -> { });
            site.notifyVisibility(true);
        });
        StagedVaultHost.settle();
    }

    private void hostHeader(KitView target) {
        target.setPanelHostHeader(root, new PanelHostHeader(header.detail(), header.active(), header.actions().stream()
            .map(action -> new PanelHostHeader.Action(action.label(), action.icon(), action.invoke(), action.icon() instanceof dev.jasper.app.platform.PresentationIcon named
                ? new PanelHostHeader.Artwork(named.name(), named.pathData()) : null)).toList()));
    }

    public KitView view() { return view; }
    public String root() { return root; }

    /** Puts synthetic Remote hosts through the plugin's own store; returns their ids in order. */
    public List<UUID> hosts(String[][] values) throws Exception {
        Object store = field(plugin, "store");
        Class<?> hostType = loader.loadClass("dev.jasper.remote.hosts.RemoteHost");
        Class<?> auth = loader.loadClass("dev.jasper.remote.hosts.Auth");
        Object agent = auth.getField("AGENT").get(null);
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < values.length; i++) {
            UUID id = new UUID(0x4000L, 0x8000000000000101L + i); ids.add(id);
            Object host = hostType.getConstructor(UUID.class, String.class, String.class, int.class, String.class, auth, String.class, boolean.class, Optional.class, Instant.class, Instant.class)
                .newInstance(id, values[i][0], values[i][1], 22, values[i][2], agent, values[i].length > 3 ? values[i][3] : "", false, Optional.empty(), Instant.EPOCH, Instant.EPOCH);
            ((CompletableFuture<?>) store.getClass().getMethod("put", hostType).invoke(store, host)).join();
        }
        StagedVaultHost.settle();
        return ids;
    }

    /** Puts a saved tunnel through the plugin's own tunnel store; returns its id. */
    public UUID tunnel(String name, UUID host, String kind, int bindPort, String target, int targetPort) throws Exception {
        Object store = field(plugin, "tunnelStore");
        Class<?> type = loader.loadClass("dev.jasper.remote.tunnels.Tunnel");
        @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) loader.loadClass("dev.jasper.remote.tunnels.TunnelKind"), kind);
        Object tunnel = type.getMethod("create", String.class, UUID.class, value.getClass(), String.class, int.class, String.class, int.class)
            .invoke(null, name, host, value, "localhost", bindPort, target, targetPort);
        ((CompletableFuture<?>) store.getClass().getMethod("put", type).invoke(store, tunnel)).join();
        StagedVaultHost.settle();
        return (UUID) type.getMethod("id").invoke(tunnel);
    }

    /**
     * Hands the live Tunnels panel confirmed states as the manager would publish them (no tunnel runs): {@code states}
     * maps a tunnel id to "up", "starting:<stage>" or "failed:<reason>"; others are stopped.
     */
    public void tunnelStates(Map<UUID, String> states) {
        onEdt(() -> {
            try {
                Object tunnelsUi = field(plugin, "tunnelsUi");
                Object panel = ((Map<?, ?>) field(tunnelsUi, "panels")).get(vault.window);
                Object store = field(tunnelsUi, "store");
                var tunnels = (List<?>) store.getClass().getMethod("tunnels").invoke(store);
                Object stopped = loader.loadClass("dev.jasper.remote.tunnels.TunnelState").getField("STOPPED").get(null);
                Object counters = loader.loadClass("dev.jasper.remote.tunnels.Counters").getConstructor().newInstance();
                var mapped = new HashMap<Object, Object>();
                for (var entry : states.entrySet()) {
                    String state = entry.getValue();
                    Object value = state.equals("up")
                        ? loader.loadClass("dev.jasper.remote.tunnels.TunnelState$Up").getConstructor(Instant.class, int.class, counters.getClass()).newInstance(Instant.now(), 8080, counters)
                        : state.startsWith("starting:") ? loader.loadClass("dev.jasper.remote.tunnels.TunnelState$Starting").getConstructor(String.class).newInstance(state.substring(9))
                        : loader.loadClass("dev.jasper.remote.tunnels.TunnelState$Failed").getConstructor(String.class).newInstance(state.substring(7));
                    mapped.put(entry.getKey(), value);
                }
                Object hostsStore = field(plugin, "store");
                Function<Object, Object> stateOf = id -> mapped.getOrDefault(id, stopped);
                Function<Object, Optional<String>> hostNames = id -> {
                    try { return ((Optional<?>) hostsStore.getClass().getMethod("host", UUID.class).invoke(hostsStore, id)).map(h -> {
                        try { return (String) h.getClass().getMethod("name").invoke(h); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }); }
                    catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                };
                panel.getClass().getMethod("setTunnels", List.class, List.class, Optional.class, Function.class, Function.class)
                    .invoke(panel, tunnels, List.of(), Optional.empty(), stateOf, hostNames);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    /** The live SFTP explorer of the fixture window. */
    private Object sftpPanel() throws ReflectiveOperationException {
        Object sftpUi = field(plugin, "sftpUi");
        var panel = sftpUi.getClass().getDeclaredMethod("panel", UUID.class); panel.setAccessible(true);
        return panel.invoke(sftpUi, vault.window);
    }

    /** Calls one public method of the window's live SFTP explorer, as its controller does. */
    public void sftp(String method, Class<?>[] types, Object... args) {
        onEdt(() -> {
            try { var panel = sftpPanel(); panel.getClass().getMethod(method, types).invoke(panel, args); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    /** A Remote {@code FileEntry}: {@code kind} is FILE, DIRECTORY or LINK. */
    public Object fileEntry(String name, String kind, long size) {
        try {
            Class<?> entry = loader.loadClass("dev.jasper.remote.sftp.FileEntry");
            @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) loader.loadClass("dev.jasper.remote.sftp.FileEntry$Kind"), kind);
            return entry.getConstructor(String.class, value.getClass(), long.class, long.class, int.class, String.class)
                .newInstance(name, value, size, 1_000L, kind.equals("DIRECTORY") ? 0755 : 0644, "");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Puts a saved File Server definition through the plugin's own store (stopped; nothing listens). */
    public UUID fileServer(String name, String protocol) throws Exception {
        Object store = field(plugin, "store");
        Class<?> type = loader.loadClass("dev.jasper.fileserver.definition.ServerDefinition");
        @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) loader.loadClass("dev.jasper.fileserver.definition.Protocol"), protocol);
        Path folder = Files.createDirectories(home.resolve("synthetic-share-" + name.replaceAll("[^A-Za-z0-9]", "")));
        Object definition = type.getMethod("create", String.class, value.getClass(), Path.class).invoke(null, name, value, folder);
        ((CompletableFuture<?>) store.getClass().getMethod("put", type).invoke(store, definition)).join();
        StagedVaultHost.settle();
        return (UUID) type.getMethod("id").invoke(definition);
    }

    public void theme(ResolvedTheme value) { onEdt(() -> { zone(value); appearance.setPair(value); }); }
    private void zone(ResolvedTheme value) { view.putClientProperty("Jasper.zone", "workspace"); view.putClientProperty("Jasper.workspaceTheme", value.workspace()); }
    public Component component(String id) { return onEdtValue(() -> { var found = StagedVaultHost.named(view, id); if (found == null) throw new AssertionError("Missing " + id); return found; }); }
    public void size(int width, int height) { onEdt(() -> { view.setSize(width, height); for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(view); }); }
    public BufferedImage capture(int scale) {
        return onEdtValue(() -> {
            var image = new BufferedImage(view.getWidth() * scale, view.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics(); try { graphics.scale(scale, scale); view.printAll(graphics); } finally { graphics.dispose(); } return image;
        });
    }
    static Object field(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void close() throws Exception { try { onEdt(site::notifyClosed); } finally { try { vault.close(); } finally { loader.close(); } } }
}
