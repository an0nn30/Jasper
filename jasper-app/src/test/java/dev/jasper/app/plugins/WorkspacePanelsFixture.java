package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;

/**
 * The staged Vault, Remote and File Server jars in one real plugin host, for an actual workspace window to connect to:
 * the window mounts their panels and rail buttons through its own contributions path. The Vault is created and locked
 * with a synthetic password; Remote has an isolated empty ~/.ssh, no agent, no timers and no network; File Server has a
 * synthetic home and never listens. Host rows are synthetic presentation data written through Remote's real store.
 * Test only; call from the test thread.
 */
public final class WorkspacePanelsFixture implements AutoCloseable {
    public static final String SESSIONS = StagedRemoteHost.PANEL, SFTP = StagedRemoteHost.SFTP_PANEL,
        TUNNELS = StagedRemoteHost.TUNNELS_PANEL, FILE_SERVERS = "dev.jasper.fileserver.panel";
    public static final UUID SELECTED = new UUID(0x4000L, 0x8000000000000001L);
    private static final PluginInfo FILE_SERVER = new PluginInfo("dev.jasper.fileserver", "File Server", "0.4.0",
        Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));
    private final WorkspaceAppearanceFixture appearance;
    private final StagedVaultHost vault;
    private final PluginClassLoader remoteLoader, serverLoader;
    private final Plugin remote;
    private final Object store;
    private final Class<?> hostType;
    private final List<Object> hosts = new ArrayList<>();

    public WorkspacePanelsFixture(Path home, ResolvedTheme theme) throws Exception {
        appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(theme));
        vault = new StagedVaultHost(Files.createDirectories(home.resolve("plugins")), theme.chrome().dark(), appearance);
        vault.createVault("synthetic-workspace-password");
        onEdt(() -> vault.surface("Credential Vault").requestClose());
        vault.invoke("dev.jasper.vault.lock");
        var staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        var ssh = Files.createDirectories(home.resolve("synthetic-ssh"));
        remoteLoader = new PluginClassLoader("dev.jasper.remote", ManagedVaultLoadingTest.jars(staged.resolve("dev.jasper.remote")),
            getClass().getClassLoader(), Map.of("dev.jasper.vault.api", vault.loader));
        var remoteConstructor = remoteLoader.loadClass("dev.jasper.remote.RemotePlugin")
            .getDeclaredConstructor(Executor.class, Function.class, BiFunction.class, Path.class);
        remoteConstructor.setAccessible(true);
        remote = (Plugin) remoteConstructor.newInstance((Executor) SwingUtilities::invokeLater,
            (Function<Object, Optional<Object>>) context -> Optional.empty(),
            (BiFunction<Duration, Runnable, Runnable>) (delay, task) -> () -> { }, ssh);
        onEdt(() -> vault.host.start(new HostedPlugin(StagedRemoteHost.REMOTE, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"),
            Set.of("dev.jasper.remote.api"), remoteLoader, () -> remote)));
        serverLoader = new PluginClassLoader(FILE_SERVER.id(), ManagedVaultLoadingTest.jars(staged.resolve(FILE_SERVER.id())),
            getClass().getClassLoader(), Map.of());
        var pluginClass = serverLoader.loadClass("dev.jasper.fileserver.FileServerPlugin");
        var environmentClass = serverLoader.loadClass("dev.jasper.fileserver.FileServerPlugin$Environment");
        var environment = environmentClass.getDeclaredConstructor(Executor.class, Supplier.class, IntPredicate.class,
            Consumer.class, Consumer.class, Path.class, String.class, java.time.ZoneId.class, BiFunction.class);
        environment.setAccessible(true);
        Object settings = environment.newInstance((Executor) SwingUtilities::invokeLater, (Supplier<List<?>>) List::of,
            (IntPredicate) port -> true, (Consumer<String>) text -> { }, (Consumer<Path>) path -> { },
            Files.createDirectories(home.resolve("synthetic-share")), "Mac OS X", ZoneOffset.UTC,
            (BiFunction<Duration, Runnable, Runnable>) (delay, task) -> () -> { });
        var serverConstructor = pluginClass.getDeclaredConstructor(environmentClass);
        serverConstructor.setAccessible(true);
        Plugin server = (Plugin) serverConstructor.newInstance(settings);
        onEdt(() -> vault.host.start(new HostedPlugin(FILE_SERVER, Set.of(), Set.of("dev.jasper.vault"), Set.of(), serverLoader, () -> server)));
        StagedVaultHost.settle();
        store = field(remote, "store");
        hostType = remoteLoader.loadClass("dev.jasper.remote.hosts.RemoteHost");
        Class<?> auth = remoteLoader.loadClass("dev.jasper.remote.hosts.Auth");
        Object credential = remoteLoader.loadClass("dev.jasper.remote.hosts.Auth$Vault").getConstructor(UUID.class)
            .newInstance(UUID.fromString("00000000-0000-4000-8000-000000000099"));
        // The screenshot's example identities are synthetic presentation data; nothing connects to them.
        String[][] values = {{"amp", "100.110.65.45"}, {"amp-amp", "100.110.65.45"}, {"dustin-vm", "100.83.72.100"},
            {"minecraft-local", "192.168.1.124"}, {"pve", "100.73.226.91"}, {"pve-root", "100.73.226.91"}};
        for (int i = 0; i < values.length; i++) hosts.add(hostType.getConstructor(UUID.class, String.class, String.class, int.class,
                String.class, auth, String.class, boolean.class, Optional.class, Instant.class, Instant.class)
            .newInstance(new UUID(0x4000L, 0x8000000000000001L + i), values[i][0], values[i][1], 22, "ubuntu", credential, "", false,
                Optional.empty(), Instant.EPOCH, Instant.EPOCH));
    }

    public Contributions contributions() { return vault.contributions; }
    public TerminalRegistry registry() { return vault.terminals.registry; }

    /** Writes the synthetic hosts through Remote's real store and waits for its completions. */
    public void populate() throws Exception {
        for (Object host : hosts) ((CompletableFuture<?>) store.getClass().getMethod("put", hostType).invoke(store, host)).get(20, TimeUnit.SECONDS);
        StagedVaultHost.settle();
    }

    /** Selects a synthetic host in the Sessions panel Remote built for {@code window}; false while it has none. */
    public boolean select(UUID window, UUID host) {
        Object panel = onEdtValue(() -> ((Map<?, ?>) field(remote, "panels")).get(window));
        if (panel == null) return false;
        onEdt(() -> {
            try { panel.getClass().getMethod("select", UUID.class).invoke(panel, host); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        return true;
    }

    /** Lets queued plugin work and UI completions run. */
    public static void settle() throws InterruptedException { StagedVaultHost.settle(); }

    public void theme(ResolvedTheme value) { onEdt(() -> appearance.setPair(value)); }

    private static Object field(Object object, String name) {
        try { var found = object.getClass().getDeclaredField(name); found.setAccessible(true); return found.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Stops every plugin in the shared host (the Vault host owns that), then releases the class loaders. */
    @Override public void close() throws Exception {
        try { vault.close(); }
        finally { try { remoteLoader.close(); } finally { serverLoader.close(); } }
    }
}
