package dev.jasper.remote.edit;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** One file on one connection identity, edited in the Editor. Every operation queues on {@link EditChannels}. */
public final class RemoteDocument implements EditorDocument {
    private final ConnectionIdentity identity;
    private final String path;
    private final EditChannels channels;
    private final Supplier<Optional<WindowHandle>> owner;

    /**
     * @param identity the connection the file was browsed on
     * @param path the absolute path as browsed
     * @param channels where its operations queue
     * @param owner the window for sign-in prompts when reconnecting
     */
    public RemoteDocument(ConnectionIdentity identity, String path, EditChannels channels, Supplier<Optional<WindowHandle>> owner) {
        this.identity = Objects.requireNonNull(identity);
        try { this.path = FilePaths.absolute(path); }
        catch (IOException relative) { throw new IllegalArgumentException("An absolute path is required: " + path, relative); }
        this.channels = Objects.requireNonNull(channels);
        this.owner = Objects.requireNonNull(owner);
    }

    /**
     * @param identity a connection identity
     * @param path an absolute, normalized path
     * @return {@code remote:}, each hop's saved-host id and login, and the path; cosmetic host edits keep it
     */
    public static String key(ConnectionIdentity identity, String path) {
        return "remote:" + identity.hops().stream().map(hop -> hop.host().id() + "/" + hop.username()).collect(Collectors.joining(",")) + ":" + path;
    }

    @Override public String key() { return key(identity, path); }
    @Override public String name() { return path.equals("/") ? path : path.substring(path.lastIndexOf('/') + 1); }
    @Override public String location() { return identity.username() + "@" + identity.host().name() + ":" + path; }
    @Override public Optional<String> host() { return Optional.of(identity.host().name()); }

    @Override public CompletableFuture<Content> read(long limitBytes) {
        return channels.submit(identity, owner, endpoint -> read(endpoint, path, limitBytes));
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {
        return channels.submit(identity, owner, endpoint -> SafeWrite.resolve(endpoint, path).entry().map(RemoteDocument::revision));
    }

    @Override public CompletableFuture<Revision> write(byte[] content) {
        return channels.submit(identity, owner, endpoint -> revision(SafeWrite.write(endpoint, path, content)));
    }

    /** Resolves links, checks the size before transferring anything, then reads at most the limit. */
    static Content read(FileEndpoint endpoint, String path, long limitBytes) throws IOException {
        var resolved = SafeWrite.resolve(endpoint, path);
        FileEntry file = resolved.entry().orElseThrow(() -> new NoSuchFileException(path));
        if (file.kind() == FileEntry.Kind.DIRECTORY) throw new FileSystemException(path, null, "Is a folder");
        if (file.kind() != FileEntry.Kind.FILE) throw new FileSystemException(path, null, "Not a regular file");
        if (file.size() > limitBytes) throw new TooLargeException(file.size(), limitBytes);
        byte[] bytes;
        try (InputStream in = endpoint.read(resolved.path(), 0)) {
            // One byte past the limit shows that the file grew beyond it after the size check.
            bytes = in.readNBytes((int) Math.min(limitBytes, Integer.MAX_VALUE - 9L) + 1);
        }
        if (bytes.length > limitBytes) throw new TooLargeException(bytes.length, limitBytes);
        return new Content(bytes, new Revision(file.modifiedMillis(), bytes.length));
    }

    private static Revision revision(FileEntry file) { return new Revision(file.modifiedMillis(), file.size()); }
}
