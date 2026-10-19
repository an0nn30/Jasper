package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.ui.swing.KitView;
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
 * The actual staged Remote New Tunnel (or Edit / Save as Tunnel) dialog content, opened through the plugin's own action
 * or editor entry beside an actual locked synthetic Vault. Hosts and tunnels are fixture data in the plugin's stores; no
 * tunnel starts and nothing connects. Capture is the mounted kit view at the dialog's form width.
 */
public final class TunnelSurfaceFixture implements AutoCloseable {
    private final StagedVaultHost vault;
    private final WorkspaceAppearanceFixture appearance;
    private final PluginClassLoader loader;
    private final Plugin plugin;
    private AuxiliarySurface surface;
    private int width = com.formdev.flatlaf.util.UIScale.scale(540), minimum = com.formdev.flatlaf.util.UIScale.scale(558), height;

    public TunnelSurfaceFixture(Path home, ResolvedTheme theme) throws Exception {
        appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(theme));
        vault = new StagedVaultHost(home, theme.chrome().dark(), appearance);
        vault.createVault("synthetic-tunnel-password");
        vault.requestCloseManager();
        vault.invoke("dev.jasper.vault.lock");
        var ssh = Files.createDirectories(home.resolve("synthetic-ssh"));
        var staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.remote");
        loader = new PluginClassLoader("dev.jasper.remote", ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of("dev.jasper.vault.api", vault.loader));
        var constructor = loader.loadClass("dev.jasper.remote.RemotePlugin").getDeclaredConstructor(java.util.concurrent.Executor.class, Function.class, BiFunction.class, Path.class);
        constructor.setAccessible(true);
        plugin = (Plugin) constructor.newInstance((java.util.concurrent.Executor) SwingUtilities::invokeLater,
            (Function<Object, Optional<Object>>) c -> Optional.empty(), (BiFunction<java.time.Duration, Runnable, Runnable>) (delay, task) -> () -> { }, ssh);
        onEdt(() -> vault.host.start(new HostedPlugin(StagedRemoteHost.REMOTE, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), Set.of("dev.jasper.remote.api"), loader, () -> plugin)));
        StagedVaultHost.settle();
    }

    /** Synthetic saved hosts {name, hostname, user, group} through the plugin's own store; their ids in order. */
    public List<UUID> hosts(String[][] values) throws Exception {
        Object store = PanelSurfaceFixture.field(plugin, "store");
        Class<?> hostType = loader.loadClass("dev.jasper.remote.hosts.RemoteHost");
        Class<?> auth = loader.loadClass("dev.jasper.remote.hosts.Auth");
        Object agent = auth.getField("AGENT").get(null);
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < values.length; i++) {
            UUID id = new UUID(0x4000L, 0x8000000000000201L + i); ids.add(id);
            Object host = hostType.getConstructor(UUID.class, String.class, String.class, int.class, String.class, auth, String.class, boolean.class, Optional.class, Instant.class, Instant.class)
                .newInstance(id, values[i][0], values[i][1], 22, values[i][2], agent, values[i][3], false, Optional.empty(), Instant.EPOCH, Instant.EPOCH);
            ((CompletableFuture<?>) store.getClass().getMethod("put", hostType).invoke(store, host)).join();
        }
        StagedVaultHost.settle();
        return ids;
    }

    /** A saved tunnel in the plugin's own tunnel store. */
    public Object tunnel(String name, UUID host, String kind, String bind, int bindPort, String target, int targetPort) throws Exception {
        Object store = PanelSurfaceFixture.field(plugin, "tunnelStore");
        Class<?> type = loader.loadClass("dev.jasper.remote.tunnels.Tunnel");
        @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) loader.loadClass("dev.jasper.remote.tunnels.TunnelKind"), kind);
        Object tunnel = type.getMethod("create", String.class, UUID.class, value.getClass(), String.class, int.class, String.class, int.class)
            .invoke(null, name, host, value, bind, bindPort, target, targetPort);
        ((CompletableFuture<?>) store.getClass().getMethod("put", type).invoke(store, tunnel)).join();
        StagedVaultHost.settle();
        return tunnel;
    }

    /** Opens New Tunnel through the plugin's registered action, as the palette or menu does. */
    public void openNew() throws InterruptedException {
        vault.invoke("dev.jasper.remote.tunnels.new");
        surface = vault.awaitSurface("New Tunnel");
        layout();
    }

    /** Opens the plugin's own editor entry for {@code tunnel} (Edit, or Save as Tunnel for a quick forward). */
    public void openEdit(Object tunnel, String title) throws Exception {
        onEdt(() -> {
            try {
                Object tunnelsUi = PanelSurfaceFixture.field(plugin, "tunnelsUi");
                var context = (dev.jasper.sdk.plugin.PluginContext) PanelSurfaceFixture.field(plugin, "context");
                var edit = tunnelsUi.getClass().getDeclaredMethod("edit", dev.jasper.sdk.terminal.WindowHandle.class, Optional.class, Optional.class);
                edit.setAccessible(true);
                edit.invoke(tunnelsUi, context.terminals().window(vault.window).orElseThrow(), Optional.of(tunnel), Optional.empty());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        surface = vault.awaitSurface(title);
        layout();
    }

    /** A quick forward's unsaved tunnel value (blank name). */
    public Object quick(UUID host, int port) throws Exception {
        Class<?> type = loader.loadClass("dev.jasper.remote.tunnels.Tunnel");
        @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) loader.loadClass("dev.jasper.remote.tunnels.TunnelKind"), "LOCAL");
        return type.getMethod("create", String.class, UUID.class, value.getClass(), String.class, int.class, String.class, int.class)
            .invoke(null, "", host, value, "localhost", port, "localhost", port);
    }

    /** Makes the tunnel store's next write fail: tunnels.toml becomes a directory (the fixture's own disposable home). */
    public void failWrites() throws Exception {
        Object store = PanelSurfaceFixture.field(plugin, "tunnelStore");
        Path file = (Path) store.getClass().getMethod("file").invoke(store);
        Files.deleteIfExists(file); Files.createDirectories(file);
    }

    public KitView view() { return onEdtValue(() -> StagedVaultHost.kitView(surface)); }
    public AuxiliarySurface surface() { return surface; }
    public Component component(String id) { return onEdtValue(() -> StagedVaultHost.require(surface, id)); }
    public void type(String id, String text) { onEdt(() -> StagedVaultHost.type(surface, id + ".input", text)); layout(); }
    public void click(String id) { onEdt(() -> StagedVaultHost.click(surface, id)); layout(); }
    public void theme(ResolvedTheme theme) { onEdt(() -> appearance.setPair(theme)); layout(); }
    public void width(int value) { width = value; layout(); }
    public void minimum(int value) { minimum = value; layout(); }
    public Dimension preferred() { return onEdtValue(() -> view().getPreferredSize()); }
    public Rectangle bounds(String id) {
        return onEdtValue(() -> { var c = StagedVaultHost.require(surface, id); return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), view()); });
    }
    public void layout() {
        onEdt(() -> { var view = view(); height = Math.max(minimum, view.getPreferredSize().height); view.setSize(width, height); SessionsSurfaceFixture.layout(view); });
    }
    public BufferedImage capture(int scale) {
        layout();
        return onEdtValue(() -> {
            var image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics(); try { graphics.scale(scale, scale); view().printAll(graphics); } finally { graphics.dispose(); } return image;
        });
    }
    @Override public void close() throws Exception { try { vault.close(); } finally { loader.close(); } }
}
