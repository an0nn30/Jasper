package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Remote's answer to the Editor's Open in Editor…: a focused SSH pane, or an active Editor tab showing a remote file,
 * opens the remote file picker there instead of the local chooser. Constructed only when the Editor is installed; UI
 * thread only.
 */
public final class RemoteOpening {
    /**
     * Where the picker starts.
     *
     * @param identity the connection
     * @param directory the folder; blank for the home folder
     */
    public record Target(ConnectionIdentity identity, String directory) {
        /** Rejects a null identity; a null directory is blank. */
        public Target { Objects.requireNonNull(identity, "identity"); directory = directory == null ? "" : directory; }
    }

    private final Editor editor;
    private final RemoteEditing editing;
    private final Function<PaneHandle, Optional<Target>> panes;
    private final BiConsumer<WindowHandle, Target> show;

    /**
     * @param editor the Editor's service
     * @param editing rebuilds connections from remote keys
     * @param panes the connection and folder of a connected SSH pane
     * @param show shows the picker for a window
     */
    public RemoteOpening(Editor editor, RemoteEditing editing, Function<PaneHandle, Optional<Target>> panes, BiConsumer<WindowHandle, Target> show) {
        this.editor = Objects.requireNonNull(editor); this.editing = Objects.requireNonNull(editing);
        this.panes = Objects.requireNonNull(panes); this.show = Objects.requireNonNull(show);
    }

    /**
     * Registers with the Editor. Throws {@link NoSuchMethodError} with an Editor older than 0.5, which opens only local files.
     *
     * @return removes the registration
     */
    public Subscription register() { return editor.opener(this::handle); }

    boolean handle(OpenRequest request) {
        Optional<Target> target = request.pane().flatMap(panes);
        if (target.isEmpty()) target = request.documentKey().flatMap(this::fromKey);
        if (target.isEmpty()) return false;
        show.accept(request.window(), target.get());
        return true;
    }

    private Optional<Target> fromKey(String key) {
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        if (parsed.isEmpty()) return Optional.empty();
        String folder;
        try { folder = FilePaths.parent(parsed.get().path()); }
        catch (IOException root) { folder = ""; }
        String directory = folder;
        return editing.identity(parsed.get()).map(identity -> new Target(identity, directory));
    }
}
