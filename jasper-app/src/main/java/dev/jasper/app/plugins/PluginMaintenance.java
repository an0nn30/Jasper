package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Launch-time housekeeping of the user plugin directory, before anything is loaded: pending removals and
 * pending installs are carried out inside the state lock, so two launching processes cannot interleave,
 * and a running process never replaces jars it has open. What cannot be done stays pending.
 */
final class PluginMaintenance {
    static final String PENDING = ".pending";
    static final String STAGING_PREFIX = ".staging-";
    static final String TRASH_PREFIX = ".trash-";
    private static final Duration ABANDONED = Duration.ofDays(1);
    private static final System.Logger LOG = System.getLogger(PluginMaintenance.class.getName());

    private PluginMaintenance() { }

    /** Never the EDT. Never throws. Writes nothing when nothing is pending. */
    static void apply(Path userDirectory, PluginStateStore store) {
        try {
            sweep(userDirectory);
            if (!needed(userDirectory, store.read())) return;
            store.transact(state -> { removals(userDirectory, state); installs(userDirectory); return state; });
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Pending plugin changes could not be applied; they will be retried at the next launch", failure);
        }
    }

    private static boolean needed(Path userDirectory, Map<String, PluginStateStore.Entry> state) throws IOException {
        if (state.values().stream().anyMatch(PluginStateStore.Entry::remove)) return true;
        Path pending = userDirectory.resolve(PENDING);
        if (!Files.isDirectory(pending)) return false;
        try (var children = Files.list(pending)) { return children.findAny().isPresent(); }
    }

    private static void removals(Path userDirectory, Map<String, PluginStateStore.Entry> state) {
        for (var item : List.copyOf(state.entrySet())) {
            String id = item.getKey();
            // The id names a directory to delete, and the file can be edited by hand.
            if (!item.getValue().remove() || !PluginInfo.validId(id)) continue;
            try {
                deleteRecursively(userDirectory.resolve(PENDING).resolve(id));
                retire(userDirectory.resolve(id));
                state.remove(id);
                LOG.log(System.Logger.Level.INFO, "Removed plugin " + id);
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not remove plugin " + id + "; it stays marked and is not loaded", failure);
            }
        }
    }

    private static void installs(Path userDirectory) {
        Path pending = userDirectory.resolve(PENDING);
        if (!Files.isDirectory(pending)) return;
        List<Path> staged;
        try (var children = Files.list(pending)) { staged = children.filter(Files::isDirectory).sorted().toList(); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Pending installs cannot be listed", failure); return; }
        for (Path directory : staged) {
            String name = directory.getFileName().toString();
            List<String> problems = new ArrayList<>();
            Optional<PluginCandidate> candidate = PluginDiscovery.single(directory, PluginCandidate.Origin.USER, problems);
            try {
                if (candidate.isEmpty() || !candidate.get().id().equals(name)) {
                    LOG.log(System.Logger.Level.WARNING, "Discarding an unusable pending install " + name + " " + problems);
                    deleteRecursively(directory);
                    continue;
                }
                retire(userDirectory.resolve(name));
                move(directory, userDirectory.resolve(name));
                LOG.log(System.Logger.Level.INFO, "Installed plugin " + name + " " + candidate.get().descriptor().version());
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not install plugin " + name + "; it stays pending", failure);
            }
        }
    }

    /** Outside the lock: staging directories are in use by a process showing a consent dialog, so only old ones go. */
    private static void sweep(Path userDirectory) {
        if (!Files.isDirectory(userDirectory)) return;
        List<Path> children;
        try (var listing = Files.list(userDirectory)) { children = listing.toList(); }
        catch (IOException failure) { return; }
        for (Path child : children) {
            String name = child.getFileName().toString();
            try {
                boolean abandoned = name.startsWith(STAGING_PREFIX)
                    && Files.getLastModifiedTime(child).toInstant().isBefore(Instant.now().minus(ABANDONED));
                if (name.startsWith(TRASH_PREFIX) || abandoned) deleteRecursively(child);
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not sweep " + child, failure);
            }
        }
    }

    /**
     * Takes a plugin directory out of service with one rename, then deletes it. Where open jars cannot be
     * renamed the rename fails and nothing is half-deleted; a leftover trash directory is swept later.
     */
    static void retire(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        Path trash = directory.resolveSibling(TRASH_PREFIX + directory.getFileName() + "-" + UUID.randomUUID());
        move(directory, trash);
        try { deleteRecursively(trash); }
        catch (IOException failure) { LOG.log(System.Logger.Level.DEBUG, "Left for the next sweep: " + trash, failure); }
    }

    static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(from, to); }
    }

    /** Does not follow links. A missing path is already deleted. */
    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
