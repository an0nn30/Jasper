package dev.jasper.terminal.internal.shell;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether an OSC 7 {@code file://host/path} report names a directory on this machine. It is local only
 * when the session is a local process and the host is empty, {@code localhost}, or exactly one of the machine's
 * known names, compared without regard to case. There is no partial or first-label matching, and a session
 * that is not a local process never has a local directory: ambiguity resolves away from treating a path as
 * local, because consumers open files and start shells there.
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

    public DirectoryProvenance(boolean localSession, Set<String> localHostNames) {
        this.localSession = localSession;
        this.localNames = localHostNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    /** Empty for anything that is not a {@code file} URI with a path. */
    public Optional<Report> classify(String uri) {
        try {
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (!"file".equalsIgnoreCase(parsed.getScheme()) || path == null || path.isEmpty()) return Optional.empty();
            // Java parses a name with an underscore as a registry authority, not a host; it is a name all the same.
            String host = parsed.getHost() != null ? parsed.getHost() : parsed.getAuthority() == null ? "" : parsed.getAuthority();
            String key = host.toLowerCase(Locale.ROOT);
            if (localSession && (key.isEmpty() || key.equals("localhost") || localNames.contains(key)))
                return Optional.of(new Report.Local(Path.of(new URI("file", null, path, null))));
            return Optional.of(new Report.Remote(new RemoteLocation(host, path)));
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
