package dev.jasper.app.platform;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.Optional;

/** {@code file:} URLs from outside Jasper (browser plan 4c): which local file one names. Any thread. */
public final class FileUrls {
    private FileUrls() { }

    /**
     * The local file {@code url} names, in each form RFC 8089 gives a {@code file:} URL: with no authority, an empty one
     * or {@code localhost}, in any case, as in {@code file:/x}, {@code file:///x} and {@code file://localhost/x}; its query
     * and fragment are ignored. A URL that names another host is the file this platform reads it as: a share on Windows,
     * nothing on macOS and Linux.
     *
     * @param url any URI
     * @return the file, or empty for another scheme, an opaque URL such as {@code file:x}, one with no path, and one this
     *     platform cannot read as a local file
     */
    public static Optional<Path> localFile(URI url) {
        String path = url.getRawPath();
        if (!"file".equalsIgnoreCase(url.getScheme()) || url.isOpaque() || path == null || path.isEmpty())
            return Optional.empty();
        String authority = url.getRawAuthority();
        boolean local = authority == null || authority.isEmpty() || authority.equalsIgnoreCase("localhost");
        try {
            return Optional.of(Path.of(new URI("file://" + (local ? "" : authority) + path)));
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException notLocal) {
            return Optional.empty();
        }
    }
}
