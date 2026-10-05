package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.remote.chooser.RemoteHosts;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Remote's {@link RemoteProjects}: scripts and listings over {@link Connections}, files and terminals through Remote's UI. */
public final class ProjectsService implements RemoteProjects {
    static final Duration EXISTS_TIMEOUT = Duration.ofSeconds(10);
    private final Connections connections;
    private final Function<UUID, Optional<RemoteHost>> hosts;
    private final Function<UUID, Optional<RemoteFolder>> panes;
    private final BiConsumer<RemoteFolder, WindowHandle> open;
    private final BiConsumer<RemoteFolder, WindowHandle> terminal;
    private final Executor background;

    /**
     * @param connections Remote's connections (UI thread)
     * @param hosts the saved hosts by id
     * @param panes where an SSH pane is, when its folder is known
     * @param open opens a remote file in the Editor
     * @param terminal opens an SSH tab in a folder
     * @param background runs the remote commands
     */
    public ProjectsService(Connections connections, Function<UUID, Optional<RemoteHost>> hosts, Function<UUID, Optional<RemoteFolder>> panes,
                           BiConsumer<RemoteFolder, WindowHandle> open, BiConsumer<RemoteFolder, WindowHandle> terminal, Executor background) {
        this.connections = connections; this.hosts = hosts; this.panes = panes; this.open = open; this.terminal = terminal;
        this.background = background;
    }

    @Override public Optional<RemoteFolder> locate(UUID paneId) { return panes.apply(paneId); }

    @Override public boolean connected(UUID hostId) { return connections.authenticated(hostId); }

    @Override public Optional<String> hostName(UUID hostId) { return hosts.apply(hostId).map(RemoteHost::name); }

    @Override public String fileSourceId() { return RemoteHosts.ID; }

    @Override public Optional<RemoteFolder> folder(FileLocation.Sourced location) {
        if (!RemoteHosts.ID.equals(location.sourceId()) || !location.path().startsWith("/")) return Optional.empty();
        UUID id;
        try { id = UUID.fromString(location.rootId()); }
        catch (IllegalArgumentException unknown) { return Optional.empty(); }
        return hosts.apply(id).map(host -> new RemoteFolder(id, host.name(), location.path()));
    }

    @Override public CompletionStage<Map<String, Set<String>>> exists(UUID hostId, List<String> folders, Set<String> names) {
        List<String> asked = List.copyOf(folders);
        Set<String> wanted = Set.copyOf(names);
        String hostName = name(hostId);
        Optional<SessionLease> lease = connections.existingLease(hostId);
        if (lease.isEmpty()) return CompletableFuture.failedFuture(new NotConnectedException(hostName));
        return onBackground(lease.get(), held -> {
            try (held) {
                var fields = new ArrayList<String>();
                Exec.Outcome outcome = Exec.run(held.session(), Scripts.exists(asked, wanted), Scripts.EXISTS, fields::add, EXISTS_TIMEOUT, () -> false);
                if (!outcome.started() || outcome.status() != 0) throw new IOException("Could not look for project markers on " + hostName);
                return Scripts.pairs(fields);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
    }

    @Override public CompletionStage<RemoteListing> list(UUID hostId, String root, ListOptions options, Optional<WindowHandle> signIn) {
        Optional<SessionLease> existing = connections.existingLease(hostId);
        CompletableFuture<SessionLease> leasing;
        if (existing.isPresent()) leasing = CompletableFuture.completedFuture(existing.get());
        else if (signIn.isEmpty()) return CompletableFuture.failedFuture(new NotConnectedException(name(hostId)));
        else {
            WindowHandle window = signIn.get();
            try {
                leasing = connections.resolveIdentity(hostId, window)
                    .thenCompose(identity -> connections.lease(identity, window, status -> { }));
            } catch (RuntimeException unknown) {
                return CompletableFuture.failedFuture(unknown);
            }
        }
        return leasing.thenCompose(lease -> onBackground(lease, held -> {
            try { return RemoteLister.list(held, root, options); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }));
    }

    @Override public void open(UUID hostId, String path, WindowHandle window) {
        hosts.apply(hostId).ifPresent(host -> open.accept(new RemoteFolder(hostId, host.name(), path), window));
    }

    @Override public void openTerminal(UUID hostId, String folder, WindowHandle window) {
        hosts.apply(hostId).ifPresent(host -> terminal.accept(new RemoteFolder(hostId, host.name(), folder), window));
    }

    private String name(UUID hostId) { return hostName(hostId).orElse(hostId.toString()); }

    /** Runs {@code work}, which closes the lease, in the background; closes it here when the executor refuses. */
    private <T> CompletableFuture<T> onBackground(SessionLease lease, Function<SessionLease, T> work) {
        try {
            return CompletableFuture.supplyAsync(() -> work.apply(lease), background);
        } catch (RejectedExecutionException stopped) {
            lease.close();
            return CompletableFuture.failedFuture(stopped);
        }
    }
}
