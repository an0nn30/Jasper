package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Remote's side of the Editor: opens remote files as {@link RemoteDocument}s and resolves links for the SFTP
 * panel. Constructed only when the Editor is installed; its method signatures name no Editor type, so callers
 * load without it. UI thread only.
 */
public final class RemoteEditing {
    /** The most files Open in Editor opens at once. */
    public static final int MAX_OPEN = 20;

    private final Editor editor;
    private final EditChannels channels;
    private final Supplier<Optional<WindowHandle>> activeWindow;
    private final NotificationGroup notifications;
    private final Function<UUID, Optional<RemoteHost>> hosts;
    private final Supplier<CompletableFuture<Void>> hostsLoaded;
    private final Executor ui;
    private final Predicate<ConnectionIdentity> silent;

    /**
     * @param editor the Editor's service
     * @param channels where documents queue their operations
     * @param activeWindow the Jasper window used last, for sign-in prompts once the origin window has closed
     * @param notifications where a file the Editor cannot open is posted
     * @param hosts finds a saved host by id, in memory, for reopening recent files
     * @param hostsLoaded completes once the saved hosts have been read at start: before that a recorded key reopens a
     *     document that finds them when it is first used (workspace tabs spec 13)
     * @param ui the UI thread
     * @param silent whether signing in to a route asks nothing, so a restored document's first read may sign in
     */
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, NotificationGroup notifications,
                         Function<UUID, Optional<RemoteHost>> hosts, Supplier<CompletableFuture<Void>> hostsLoaded, Executor ui,
                         Predicate<ConnectionIdentity> silent) {
        this.editor = Objects.requireNonNull(editor); this.channels = Objects.requireNonNull(channels);
        this.activeWindow = Objects.requireNonNull(activeWindow); this.notifications = Objects.requireNonNull(notifications);
        this.hosts = Objects.requireNonNull(hosts); this.hostsLoaded = Objects.requireNonNull(hostsLoaded);
        this.ui = Objects.requireNonNull(ui); this.silent = Objects.requireNonNull(silent);
    }

    /**
     * Makes remote recent files reopenable in the Editor. Throws {@link NoSuchMethodError} with an Editor older than
     * 0.4, which has no recent files.
     *
     * @return removes the registration
     */
    public Subscription registerReopener() { return editor.reopener(RemoteDocument.KEY_PREFIX, this::reopen); }

    /**
     * Opens files picked in Jasper's file chooser from Remote's hosts, and tells the Editor where a remote document is.
     * Throws {@link NoSuchMethodError} with an Editor older than 0.6.
     *
     * @param sourceId Remote's file-source id
     * @param identities the route and logins to open a host's files on, by root id
     * @return removes the registration
     */
    public Subscription registerLocations(String sourceId, Function<String, Optional<ConnectionIdentity>> identities) {
        return editor.locationDocuments(sourceId, new LocationResolver() {
            @Override public EditorDocument document(FileLocation.Sourced location, WindowOwner owner) {
                ConnectionIdentity identity = identities.apply(location.rootId())
                    .orElseThrow(() -> new IllegalStateException("That host is no longer saved"));
                return new RemoteDocument(identity, location.path(), channels, owner instanceof WindowHandle window ? owner(window) : activeWindow);
            }
            @Override public Optional<FileLocation.Sourced> location(String documentKey) {
                return RemoteDocument.parseKey(documentKey)
                    .map(parsed -> new FileLocation.Sourced(sourceId, parsed.hops().getFirst().hostId().toString(), parsed.path()));
            }
        });
    }

    /**
     * @param key a recorded {@link RemoteDocument#key}
     * @return empty when the key is malformed, or a host on its route is no longer saved once the saved hosts have loaded;
     *     otherwise builds the document on the recorded route and logins, with the saved hosts as they are now, for the
     *     window reopening it. Before the saved hosts have loaded (Jasper restoring its windows at start), or while they
     *     could not be read, the document finds them when it is first used, or says why not.
     */
    Optional<Function<WindowOwner, EditorDocument>> reopen(String key) {
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        if (parsed.isEmpty()) return Optional.empty();
        String path = parsed.get().path();
        Optional<ConnectionIdentity> identity = identity(parsed.get());
        if (identity.isPresent()) return Optional.of(owner -> new RemoteDocument(identity.get(), path, channels, owner(owner), silent));
        // A host is gone only once the saved hosts have been read; while they cannot be, the document says why when read.
        CompletableFuture<Void> loaded = hostsLoaded.get();
        if (loaded.isDone() && !loaded.isCompletedExceptionally()) return Optional.empty();
        return Optional.of(owner -> new SavedRemoteDocument(key, parsed.get(), this::identity,
            found -> new RemoteDocument(found, path, channels, owner(owner), silent), hostsLoaded, ui));
    }

    /** The window that owns a reopened document's sign-in prompts: the one reopening it, else the one used last. */
    private Supplier<Optional<WindowHandle>> owner(WindowOwner owner) {
        return owner instanceof WindowHandle window ? owner(window) : activeWindow;
    }

    /**
     * @param parsed a parsed key
     * @return its route with the saved hosts as they are now and the recorded logins; empty when a host is no longer saved
     */
    Optional<ConnectionIdentity> identity(RemoteDocument.ParsedKey parsed) {
        var route = new ArrayList<ConnectionIdentity.Hop>();
        for (RemoteDocument.KeyHop hop : parsed.hops()) {
            Optional<RemoteHost> host = hosts.apply(hop.hostId());
            if (host.isEmpty()) return Optional.empty();
            route.add(new ConnectionIdentity.Hop(host.get(), hop.login()));
        }
        return Optional.of(new ConnectionIdentity(route));
    }

    /**
     * Opens a remote file in an editor tab, or selects its tab.
     *
     * @param from the Jasper window it was opened from, which owns sign-in prompts while it is open
     */
    public void open(ConnectionIdentity identity, String path, WindowHandle from) {
        try {
            editor.open(new RemoteDocument(identity, path, channels, owner(from)), from);
        } catch (RuntimeException failure) {
            // The host does not contain calls between plugins.
            notifications.error("Could not open a file in the Editor", path + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
        }
    }

    /**
     * @return what each path's links lead to, in order; empty where nothing is there
     */
    public CompletableFuture<List<Optional<FileEntry.Kind>>> kinds(ConnectionIdentity identity, List<String> paths, WindowHandle from) {
        var requested = List.copyOf(paths);
        return channels.submit(identity, owner(from), endpoint -> {
            var kinds = new ArrayList<Optional<FileEntry.Kind>>();
            for (String path : requested) kinds.add(SafeWrite.resolve(endpoint, path).entry().map(FileEntry::kind));
            return kinds;
        });
    }

    private Supplier<Optional<WindowHandle>> owner(WindowHandle from) {
        return () -> from.isOpen() ? Optional.of(from) : activeWindow.get();
    }
}
