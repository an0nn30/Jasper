package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
    private final Consumer<String> notice;

    /**
     * @param editor the Editor's service
     * @param channels where documents queue their operations
     * @param activeWindow the terminal window used last, for sign-in prompts once the origin window has closed
     * @param notice shows an error notice
     */
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice) {
        this.editor = Objects.requireNonNull(editor); this.channels = Objects.requireNonNull(channels);
        this.activeWindow = Objects.requireNonNull(activeWindow); this.notice = Objects.requireNonNull(notice);
    }

    /**
     * Opens a remote file in an editor tab, or selects its tab.
     *
     * @param from the terminal window it was opened from, which owns sign-in prompts while it is open
     */
    public void open(ConnectionIdentity identity, String path, WindowHandle from) {
        try {
            editor.open(new RemoteDocument(identity, path, channels, owner(from)));
        } catch (RuntimeException failure) {
            // The host does not contain calls between plugins.
            notice.accept("Could not open " + path + " in the Editor: "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
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
