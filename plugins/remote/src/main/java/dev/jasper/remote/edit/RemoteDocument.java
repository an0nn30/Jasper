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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** One file on one connection identity, edited in the Editor. Every operation queues on {@link EditChannels}. */
public final class RemoteDocument implements EditorDocument {
    private final ConnectionIdentity identity;
    private final String path;
    private final EditChannels channels;
    private final Supplier<Optional<WindowHandle>> owner;

    /** The start of every remote document's key. */
    public static final String KEY_PREFIX = "remote:";

    /**
     * One hop of a parsed key.
     *
     * @param hostId the saved host's id
     * @param login the login recorded for it; empty when it was never resolved
     */
    public record KeyHop(UUID hostId, String login) { }

    /**
     * A key parsed back.
     *
     * @param hops the route, target first
     * @param path the absolute path
     */
    public record ParsedKey(List<KeyHop> hops, String path) {
        /** Copies the hops. */
        public ParsedKey { hops = List.copyOf(hops); }
    }

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
        return KEY_PREFIX + identity.hops().stream().map(hop -> hop.host().id() + "/" + hop.username()).collect(Collectors.joining(",")) + ":" + path;
    }

    /**
     * The inverse of {@link #key(ConnectionIdentity, String)}. The hop list ends at the first {@code :}; logins
     * containing {@code ,}, {@code /} or {@code :} cannot be parsed back.
     *
     * @param key a recorded key
     * @return the route and path, or empty when the key is not a well-formed remote key
     */
    public static Optional<ParsedKey> parseKey(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) return Optional.empty();
        String rest = key.substring(KEY_PREFIX.length());
        int colon = rest.indexOf(':');
        if (colon < 0) return Optional.empty();
        String path = rest.substring(colon + 1);
        if (!path.startsWith("/")) return Optional.empty();
        var hops = new ArrayList<KeyHop>();
        for (String hop : rest.substring(0, colon).split(",", -1)) {
            int slash = hop.indexOf('/');
            if (slash < 0) return Optional.empty();
            String login = hop.substring(slash + 1);
            if (login.indexOf('/') >= 0) return Optional.empty();
            try { hops.add(new KeyHop(UUID.fromString(hop.substring(0, slash)), login)); }
            catch (IllegalArgumentException malformed) { return Optional.empty(); }
        }
        return Optional.of(new ParsedKey(hops, path));
    }

    @Override public Optional<EditorDocument> resolveRelative(String relative) {
        // Keep validation local: Remote also loads with Editors predating RelativePaths and this hook.
        if (relative == null || relative.isEmpty() || relative.startsWith("/") || relative.indexOf('\\') >= 0
                || relative.indexOf('?') >= 0 || relative.indexOf('#') >= 0
                || Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:").matcher(relative).lookingAt())
            throw new IllegalArgumentException("A decoded relative file path is required");
        for (int offset = 0; offset < relative.length();) {
            int codePoint = relative.codePointAt(offset);
            if (Character.isISOControl(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF)
                throw new IllegalArgumentException("A relative file path cannot contain controls or unpaired surrogates");
            offset += Character.charCount(codePoint);
        }
        try {
            return Optional.of(new RemoteDocument(identity, FilePaths.parent(path) + "/" + relative, channels, owner));
        } catch (IOException invalid) {
            throw new IllegalArgumentException("An absolute source path is required", invalid);
        }
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

    /**
     * A failed write that left its hidden sibling (an SFTP write failure aborts the endpoint, so SafeWrite cannot
     * remove it) queues a best-effort removal before the failure is reported. The removal never prompts: it fails
     * when the connection is gone, and its outcome is ignored. Cancelling the result withdraws or aborts the write.
     */
    @Override public CompletableFuture<Revision> write(byte[] content) {
        CompletableFuture<Revision> written = channels.submit(identity, owner, endpoint -> revision(SafeWrite.write(endpoint, path, content)));
        var result = new CompletableFuture<Revision>();
        // Nothing else sees written, so this runs where EditChannels completes it: on the UI thread, as submit requires.
        written.whenComplete((revision, failure) -> {
            if (failure == null) {
                result.complete(revision);
                return;
            }
            SafeWrite.leftover(failure).ifPresent(sibling -> channels.submit(identity, Optional::empty, endpoint -> {
                endpoint.remove(sibling, false);
                return null;
            }));
            result.completeExceptionally(failure);
        });
        result.whenComplete((revision, failure) -> { if (result.isCancelled()) written.cancel(true); });
        return result;
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
