package dev.jasper.remote.chooser;

import dev.jasper.remote.edit.SafeWrite;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One saved host's files, as Jasper's file chooser browses them, over an SFTP endpoint. Links are followed, so a link to
 * a folder is a folder; a dangling link is {@code OTHER}. The chooser calls it on its worker, one call at a time, and
 * {@link #abort()} from any thread. Closing it closes the endpoint and its lease.
 */
public final class RemoteBrowser implements FileBrowser {
    private final FileEndpoint endpoint;

    public RemoteBrowser(FileEndpoint endpoint) { this.endpoint = Objects.requireNonNull(endpoint); }

    @Override public String home() throws IOException { return endpoint.home(); }

    @Override public void list(String directory, Consumer<FileEntry> visitor) throws IOException {
        List<String> links = new ArrayList<>();
        endpoint.list(directory, entry -> {
            String path = child(directory, entry.name());
            if (entry.kind() == dev.jasper.remote.sftp.FileEntry.Kind.LINK) links.add(path);
            else visitor.accept(entry(path, entry));
        });
        // Links are resolved after the listing, so no other request interleaves with reading the folder.
        for (String link : links) visitor.accept(followed(link));
    }

    @Override public Optional<FileEntry> stat(String path) throws IOException {
        return SafeWrite.resolve(endpoint, path).entry().map(found -> entry(path, found));
    }

    @Override public byte[] read(String path, int maxBytes) throws IOException {
        // Reads follow links here: some endpoints refuse to open a link itself.
        try (InputStream input = endpoint.read(SafeWrite.resolve(endpoint, path).path(), 0)) { return input.readNBytes(maxBytes); }
    }

    @Override public void mkdir(String path) throws IOException { endpoint.mkdir(path); }

    @Override public void abort() { endpoint.abort(); }

    @Override public void close() {
        try { endpoint.close(); } catch (IOException | RuntimeException ignored) { }
    }

    private FileEntry followed(String path) {
        Optional<dev.jasper.remote.sftp.FileEntry> target;
        try { target = SafeWrite.resolve(endpoint, path).entry(); }
        catch (IOException broken) { target = Optional.empty(); }
        return target.map(found -> entry(path, found)).orElseGet(() -> new FileEntry(entryName(path), FileEntry.Kind.OTHER, -1, -1));
    }

    /** A listed name may be a bare name or, from SFTP, the whole path. */
    private static String child(String directory, String listed) {
        if (listed.startsWith("/")) return listed;
        return directory.endsWith("/") ? directory + listed : directory + "/" + listed;
    }

    private static String entryName(String path) {
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String last = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return last.isEmpty() ? "root" : last;
    }

    private static FileEntry entry(String path, dev.jasper.remote.sftp.FileEntry entry) {
        FileEntry.Kind kind = switch (entry.kind()) {
            case DIRECTORY -> FileEntry.Kind.DIRECTORY;
            case FILE -> FileEntry.Kind.FILE;
            case LINK, SPECIAL -> FileEntry.Kind.OTHER;
        };
        return new FileEntry(entryName(path), kind, kind == FileEntry.Kind.DIRECTORY ? -1 : entry.size(), entry.modifiedMillis());
    }
}
