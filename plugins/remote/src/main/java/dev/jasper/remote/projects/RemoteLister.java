package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.RemoteListing;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.SftpEndpoint;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;

/** Lists a remote project: Git, else {@code find}, else an SFTP walk. Blocking: call it off the UI thread. */
final class RemoteLister {
    private static final int MISSING = 3;

    private RemoteLister() { }

    static RemoteListing list(SessionLease lease, String root, ListOptions options) throws IOException {
        try (lease) {
            long deadline = System.nanoTime() + options.timeout().toNanos();
            Set<String> skip = Set.copyOf(options.excludes());
            Optional<RemoteListing> git = stream(lease, Scripts.git(root), root, RemoteListing.Strategy.GIT, skip, options, deadline);
            if (git.isPresent()) return git.get();
            Optional<RemoteListing> find = stream(lease, Scripts.find(root, options.excludes()), root, RemoteListing.Strategy.FIND, skip, options, deadline);
            if (find.isPresent()) return find.get();
            return walk(lease, root, skip, options, deadline);
        }
    }

    private static Optional<RemoteListing> stream(SessionLease lease, byte[] script, String root, RemoteListing.Strategy strategy,
                                                  Set<String> skip, ListOptions options, long deadline) throws IOException {
        var files = new ArrayList<String>();
        boolean[] truncated = {false};
        Exec.Outcome outcome = Exec.run(lease.session(), script, Scripts.LIST, field -> {
            String path = strategy == RemoteListing.Strategy.FIND && field.startsWith("./") ? field.substring(2) : field;
            if (path.isEmpty() || path.equals(".") || excluded(path, skip)) return true;
            if (files.size() >= options.maxFiles()) {
                truncated[0] = true;
                return false;
            }
            files.add(path);
            return true;
        }, remaining(deadline), options.cancelled());
        if (outcome.status() == MISSING && !outcome.started()) throw new NoSuchFileException(root);
        if (outcome.stopped()) return Optional.of(listing(files, strategy, true));
        // Status -1 with the marker seen means the session dropped mid-run: the listing is incomplete, so no
        // strategy accepts it, not even find with files (find exits 1 on unreadable folders, but never -1).
        boolean found = strategy == RemoteListing.Strategy.FIND && outcome.status() != -1 && !files.isEmpty();
        boolean accepted = outcome.started() && (outcome.status() == 0 || found);
        return accepted ? Optional.of(listing(files, strategy, truncated[0])) : Optional.empty();
    }

    private static RemoteListing walk(SessionLease lease, String root, Set<String> skip, ListOptions options, long deadline) throws IOException {
        try (var endpoint = new SftpEndpoint(lease, remaining(deadline))) {
            String start;
            try { start = endpoint.resolveDirectory(root); }
            catch (SftpException failure) {
                if (missing(failure)) throw new NoSuchFileException(root);
                throw failure;
            }
            var files = new ArrayList<String>();
            var pending = new ArrayDeque<String>();
            pending.add("");
            boolean truncated = false;
            walk:
            while (!pending.isEmpty()) {
                if (options.cancelled().getAsBoolean()) throw new CancellationException();
                if (System.nanoTime() > deadline) throw new IOException("Listing " + root + " timed out");
                String relative = pending.removeFirst();
                var entries = new ArrayList<FileEntry>();
                endpoint.list(join(start, relative), entries::add);
                for (FileEntry entry : entries) {
                    String path = relative.isEmpty() ? entry.name() : relative + "/" + entry.name();
                    if (entry.kind() == FileEntry.Kind.DIRECTORY) {
                        if (!skip.contains(entry.name())) pending.add(path);
                    } else if (entry.kind() == FileEntry.Kind.FILE) {
                        if (files.size() >= options.maxFiles()) {
                            truncated = true;
                            break walk;
                        }
                        files.add(path);
                    }
                }
            }
            return listing(files, RemoteListing.Strategy.SFTP, truncated);
        }
    }

    /** Whether SFTP said the path does not exist; any other failure (a dropped session, no permission) is not that. */
    private static boolean missing(SftpException failure) {
        return failure.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE || failure.getStatus() == SftpConstants.SSH_FX_NO_SUCH_PATH;
    }

    private static RemoteListing listing(List<String> files, RemoteListing.Strategy strategy, boolean truncated) {
        Collections.sort(files);
        return new RemoteListing(files, strategy, truncated);
    }

    private static boolean excluded(String path, Set<String> skip) {
        int start = 0;
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', start)) {
            if (skip.contains(path.substring(start, slash))) return true;
            start = slash + 1;
        }
        return false;
    }

    private static String join(String start, String relative) {
        if (relative.isEmpty()) return start;
        return start.endsWith("/") ? start + relative : start + "/" + relative;
    }

    /** The time left before the shared deadline; a timeout is a failure, not a cancellation. */
    private static Duration remaining(long deadline) throws IOException {
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new IOException("Listing timed out");
        return Duration.ofNanos(left);
    }
}
