package dev.jasper.indexer.testsupport;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/** An in-memory Remote: one file tree shared by every host, scripted per test. */
public final class FakeRemote implements RemoteProjects {
    /** Where each SSH pane is. */
    public final Map<UUID, RemoteFolder> panes = new HashMap<>();
    /** Hosts with an open connection. */
    public final Set<UUID> connectedHosts = new HashSet<>();
    /** Saved hosts' names. */
    public final Map<UUID, String> names = new HashMap<>();
    /** Marker entries per folder. */
    public final Map<String, Set<String>> markers = new HashMap<>();
    /** Listings per root. */
    public final Map<String, RemoteListing> listings = new HashMap<>();
    /** What was asked, in order. */
    public final List<String> calls = new CopyOnWriteArrayList<>();

    @Override public Optional<RemoteFolder> locate(UUID paneId) { return Optional.ofNullable(panes.get(paneId)); }
    @Override public boolean connected(UUID hostId) { return connectedHosts.contains(hostId); }
    @Override public Optional<String> hostName(UUID hostId) { return Optional.ofNullable(names.get(hostId)); }
    @Override public String fileSourceId() { return "dev.jasper.remote.hosts"; }

    @Override public Optional<RemoteFolder> folder(FileLocation.Sourced location) {
        if (!location.sourceId().equals(fileSourceId()) || !location.path().startsWith("/")) return Optional.empty();
        UUID id = UUID.fromString(location.rootId());
        return hostName(id).map(name -> new RemoteFolder(id, name, location.path()));
    }

    @Override public CompletionStage<Map<String, Set<String>>> exists(UUID hostId, List<String> folders, Set<String> wanted) {
        calls.add("exists " + folders);
        if (!connected(hostId)) return CompletableFuture.failedFuture(new NotConnectedException(names.getOrDefault(hostId, "?")));
        var found = new LinkedHashMap<String, Set<String>>();
        for (String folder : folders) if (markers.containsKey(folder)) found.put(folder, markers.get(folder));
        return CompletableFuture.completedFuture(found);
    }

    @Override public CompletionStage<RemoteListing> list(UUID hostId, String root, ListOptions options, Optional<WindowHandle> signIn) {
        calls.add("list " + root + (signIn.isPresent() ? " signed-in" : ""));
        if (!connected(hostId) && signIn.isEmpty()) return CompletableFuture.failedFuture(new NotConnectedException(names.getOrDefault(hostId, "?")));
        connectedHosts.add(hostId);
        RemoteListing listing = listings.get(root);
        return listing == null ? CompletableFuture.failedFuture(new NoSuchFileException(root)) : CompletableFuture.completedFuture(listing);
    }

    @Override public void open(UUID hostId, String path, WindowHandle window) { calls.add("open " + path); }
    @Override public void openTerminal(UUID hostId, String folder, WindowHandle window) { calls.add("terminal " + folder); }
}
