package dev.jasper.remote.chooser;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Remote's saved hosts as places in Jasper's file chooser. A root is a saved host, by id; connecting resolves the host's
 * logins (which may ask for a password through the Vault) and opens an SFTP endpoint. An SSH pane is located on its
 * host, at the shell's folder when known and the login's home otherwise. The route and logins last used for each host
 * are remembered, so the Editor can open the chosen files on them. UI thread.
 */
public final class RemoteHosts implements FileSource {
    /** The source id; the Editor's resolver is registered under it. */
    public static final String ID = "dev.jasper.remote.hosts";

    /**
     * Where an SSH pane is.
     *
     * @param directory the shell's folder, or empty text when unknown
     */
    public record Located(ConnectionIdentity identity, String directory) { }

    private final Supplier<List<RemoteHost>> hosts;
    private final Predicate<UUID> connected;
    private final Function<Runnable, Subscription> hostsChanged;
    private final Function<Runnable, Subscription> connectionsChanged;
    private final Function<UUID, Optional<Located>> panes;
    private final BiFunction<UUID, WindowHandle, CompletableFuture<ConnectionIdentity>> resolve;
    private final BiFunction<ConnectionIdentity, WindowHandle, CompletableFuture<FileEndpoint>> endpoints;
    private final Supplier<Optional<WindowHandle>> activeWindow;
    private final Map<String, ConnectionIdentity> identities = new ConcurrentHashMap<>();

    /**
     * @param hosts the saved hosts
     * @param connected whether a host has an open connection
     * @param hostsChanged subscribes to changes in the saved hosts
     * @param connectionsChanged subscribes to connections opening and closing
     * @param panes where an SSH pane is, by pane id
     * @param resolve resolves a host's route and logins, completing on the UI thread
     * @param endpoints opens an SFTP endpoint on a resolved route, completing on the UI thread
     * @param activeWindow the Jasper window used last, for sign-in prompts when the chooser has no window owner
     */
    public RemoteHosts(Supplier<List<RemoteHost>> hosts, Predicate<UUID> connected, Function<Runnable, Subscription> hostsChanged,
                       Function<Runnable, Subscription> connectionsChanged, Function<UUID, Optional<Located>> panes,
                       BiFunction<UUID, WindowHandle, CompletableFuture<ConnectionIdentity>> resolve,
                       BiFunction<ConnectionIdentity, WindowHandle, CompletableFuture<FileEndpoint>> endpoints,
                       Supplier<Optional<WindowHandle>> activeWindow) {
        this.hosts = Objects.requireNonNull(hosts); this.connected = Objects.requireNonNull(connected);
        this.hostsChanged = Objects.requireNonNull(hostsChanged); this.connectionsChanged = Objects.requireNonNull(connectionsChanged);
        this.panes = Objects.requireNonNull(panes); this.resolve = Objects.requireNonNull(resolve);
        this.endpoints = Objects.requireNonNull(endpoints); this.activeWindow = Objects.requireNonNull(activeWindow);
    }

    @Override public FileSourceSpec spec() { return new FileSourceSpec(ID, "Remote"); }

    @Override public List<FileRoot> roots() {
        return hosts.get().stream().map(host -> new FileRoot(host.id().toString(), host.name().isBlank() ? host.hostname() : host.name(),
            host.label(), connected.test(host.id()))).toList();
    }

    @Override public Subscription onChanged(Runnable listener) {
        Subscription saved = hostsChanged.apply(listener);
        Subscription connections = connectionsChanged.apply(listener);
        return () -> { saved.close(); connections.close(); };
    }

    @Override public Optional<FileLocation.Sourced> locate(PaneHandle pane) {
        return panes.apply(pane.id()).map(found -> {
            String root = found.identity().host().id().toString();
            identities.put(root, found.identity());
            String folder = found.directory().startsWith("/") ? found.directory() : FileLocation.Sourced.HOME;
            return new FileLocation.Sourced(ID, root, folder);
        });
    }

    @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
        UUID id;
        try { id = UUID.fromString(rootId); }
        catch (IllegalArgumentException unknown) { return CompletableFuture.failedFuture(new IOException("Unknown host: " + rootId)); }
        WindowHandle window = owner instanceof WindowHandle handle && handle.isOpen() ? handle : activeWindow.get().orElse(null);
        if (window == null) return CompletableFuture.failedFuture(new IOException("Open a Jasper window to connect"));
        var result = new CompletableFuture<FileBrowser>();
        var step = new AtomicReference<CompletableFuture<?>>();
        ConnectionIdentity known = identities.get(rootId);
        CompletableFuture<ConnectionIdentity> identity = known != null ? CompletableFuture.completedFuture(known) : resolve.apply(id, window);
        step.set(identity);
        identity.thenCompose(found -> {
            identities.put(rootId, found);
            CompletableFuture<FileEndpoint> opening = endpoints.apply(found, window);
            step.set(opening);
            return opening;
        }).whenComplete((endpoint, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else if (!result.complete(new RemoteBrowser(endpoint))) closeQuietly(endpoint);
        });
        result.whenComplete((ignored, failure) -> { if (result.isCancelled()) step.get().cancel(true); });
        return result;
    }

    /** @return the route and logins last used for a host, for opening its files in the Editor */
    public Optional<ConnectionIdentity> identity(String rootId) { return Optional.ofNullable(identities.get(rootId)); }

    private static void closeQuietly(FileEndpoint endpoint) {
        try { endpoint.close(); } catch (IOException | RuntimeException ignored) { }
    }
}
