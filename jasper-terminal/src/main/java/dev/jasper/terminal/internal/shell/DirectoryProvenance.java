package dev.jasper.terminal.internal.shell;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides whether an OSC 7 {@code file://host/path} report names a directory on this machine. It is local only
 * when the session is a local process and the host is empty, {@code localhost}, or exactly one of the machine's
 * known names, compared without regard to case. There is no partial or first-label matching, and a session
 * that is not a local process never has a local directory: ambiguity resolves away from treating a path as
 * local, because consumers open files and start shells there.
 *
 * <p>A local report's path goes through the session's local path mapper, which may place it elsewhere on this
 * machine (a WSL path becomes a Windows one) or return empty, and then the report is ignored. A mapper that
 * throws or returns null counts as empty.
 *
 * <p>This is a best-effort classification, not a guarantee. The report is unauthenticated text from whatever
 * runs in the pane, and a remote machine that reports this machine's name, {@code localhost} or no host at all
 * is indistinguishable from local.
 */
public final class DirectoryProvenance {
    /** A classified report. */
    public sealed interface Report {
        /** A directory on this machine. */
        record Local(Path directory) implements Report { }
        /** A directory somewhere else. */
        record Remote(RemoteLocation location) implements Report { }
    }

    private final boolean localSession;
    private final Set<String> localNames;
    private final Function<String, Optional<Path>> localPaths;

    public DirectoryProvenance(boolean localSession, Set<String> localHostNames) {
        this(localSession, localHostNames, DirectoryProvenance::fileUriPath);
    }

    public DirectoryProvenance(boolean localSession, Set<String> localHostNames, Function<String, Optional<Path>> localPaths) {
        this.localSession = localSession;
        this.localNames = localHostNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        this.localPaths = Objects.requireNonNull(localPaths, "localPaths");
    }

    /** The reported path as a {@code file} URI path on this machine; empty when it is not one. */
    public static Optional<Path> fileUriPath(String path) {
        try {
            return Optional.of(Path.of(new URI("file", null, path, null)));
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** Empty for anything that is not a {@code file} URI with a path, and for a local report the mapper ignores. */
    public Optional<Report> classify(String uri) {
        try {
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (!"file".equalsIgnoreCase(parsed.getScheme()) || path == null || path.isEmpty()) return Optional.empty();
            // Java parses a name with an underscore as a registry authority, not a host; it is a name all the same.
            String host = parsed.getHost() != null ? parsed.getHost() : parsed.getAuthority() == null ? "" : parsed.getAuthority();
            String key = host.toLowerCase(Locale.ROOT);
            if (localSession && (key.isEmpty() || key.equals("localhost") || localNames.contains(key))) return local(path);
            return Optional.of(new Report.Remote(new RemoteLocation(host, path)));
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * The path a {@code file} URI names when it is on this machine by the same rule, through the local path mapper;
     * empty for a remote one and for anything that is not a {@code file} URI with a path.
     */
    public Optional<Path> localPath(String uri) {
        return classify(uri).flatMap(report -> report instanceof Report.Local local ? Optional.of(local.directory()) : Optional.empty());
    }

    private Optional<Report> local(String path) {
        Optional<Path> mapped;
        try {
            mapped = localPaths.apply(path);
        } catch (RuntimeException failure) {
            // The mapper runs on the reader thread; a broken one must not end the session.
            return Optional.empty();
        }
        return mapped == null ? Optional.empty() : mapped.map(Report.Local::new);
    }
}
