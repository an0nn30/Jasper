package dev.jasper.indexer.remote;

import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.detect.ProjectDetector;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.Listing;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.indexer.index.Strategy;
import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.terminal.WindowHandle;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

/** The indexer's view of Remote. UI thread; stages complete on Remote's worker threads. */
public final class RemoteBridge {
    static final Duration LIST_TIMEOUT = Duration.ofMinutes(2);
    private final RemoteProjects remote;

    RemoteBridge(RemoteProjects remote) { this.remote = remote; }

    /**
     * @param services the plugin's services
     * @return the bridge, or empty when Remote is not installed
     */
    public static Optional<RemoteBridge> find(Services services) {
        try { return services.find(RemoteProjects.class).map(RemoteBridge::new); }
        catch (NoClassDefFoundError absent) { return Optional.empty(); }
    }

    /** @param host a saved host @return whether an authenticated connection to it is open */
    public boolean connected(UUID host) { return remote.connected(host); }

    /** @param host a saved host @return its name, or empty when it is no longer saved */
    public Optional<String> hostName(UUID host) { return remote.hostName(host); }

    /** @return Remote's file source id, for the folder chooser */
    public String fileSourceId() { return remote.fileSourceId(); }

    /** @param paneId a pane @return where it is, when it is an SSH pane with a known folder */
    public Optional<RemotePlace> locate(UUID paneId) {
        return remote.locate(paneId).map(found -> new RemotePlace(found.hostId(), found.hostName(), found.path()));
    }

    /** @param location a chooser pick from Remote's source @return the folder, when it is one */
    public Optional<RemotePlace> folder(FileLocation.Sourced location) {
        return remote.folder(location).map(found -> new RemotePlace(found.hostId(), found.hostName(), found.path()));
    }

    /**
     * Finds a remote folder's project root with one remote command.
     *
     * @param place a folder on a connected host
     * @param markers the configured markers
     * @return the root, or empty
     */
    public CompletionStage<Optional<Detection<String>>> detect(RemotePlace place, Markers markers) {
        List<String> ancestors = ProjectDetector.remoteAncestors(place.folder());
        Set<String> names = new TreeSet<>(markers.strong());
        names.addAll(markers.weak());
        return remote.exists(place.host(), ancestors, names)
            .thenApply(found -> ProjectDetector.root(ancestors, folder -> found.getOrDefault(folder, Set.of()), markers));
    }

    /**
     * Lists a remote project. Without a sign-in window a missing connection cancels the job quietly.
     *
     * @param key a remote project
     * @param excludes folder names never listed
     * @param maxFiles the limit
     * @param cancelled polled while listing
     * @param signIn the window to connect in, for a job the user asked for
     * @return the listing
     */
    public CompletionStage<Listing> list(ProjectKey key, List<String> excludes, int maxFiles, BooleanSupplier cancelled,
                                         Optional<WindowHandle> signIn) {
        var options = new ListOptions(excludes, maxFiles, LIST_TIMEOUT, cancelled);
        return remote.list(key.host().orElseThrow(), key.root(), options, signIn).handle((listing, failure) -> {
            if (failure == null) return new Listing(listing.files(), strategy(listing.strategy()), listing.truncated());
            Throwable cause = unwrap(failure);
            if (signIn.isEmpty() && cause instanceof NotConnectedException) throw new CancellationException(cause.getMessage());
            throw new CompletionException(cause);
        });
    }

    /** @param ref a remote file @param window where the user acted */
    public void open(FileRef ref, WindowHandle window) { remote.open(ref.key().host().orElseThrow(), ref.remotePath(), window); }

    /** @param ref a remote file @param window where to open the tab */
    public void openTerminal(FileRef ref, WindowHandle window) {
        String path = ref.remotePath();
        int slash = path.lastIndexOf('/');
        remote.openTerminal(ref.key().host().orElseThrow(), slash <= 0 ? "/" : path.substring(0, slash), window);
    }

    private static Strategy strategy(RemoteListing.Strategy strategy) {
        return switch (strategy) {
            case GIT -> Strategy.GIT;
            case FIND -> Strategy.FIND;
            case SFTP -> Strategy.SFTP;
        };
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }
}
