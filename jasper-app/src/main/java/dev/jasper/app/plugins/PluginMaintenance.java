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

        /** Never the EDT. Never throws. {@code legacyDataRootOrNull} is the pre-2026-09-22 {@code plugin-data/} directory, migrated once. */
        static void apply(Path userDirectory, PluginStateStore store, Path legacyDataRootOrNull, Version sdk) {
            try {
                sweep(userDirectory);
                migrateLayout(userDirectory, legacyDataRootOrNull);
                consumeZips(userDirectory, store, sdk);
                if (!needed(userDirectory, store.read())) return;
                store.transact(state -> { removals(userDirectory, state); installs(userDirectory); return state; });
            } catch (IOException | RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Pending plugin changes could not be applied; they will be retried at the next launch", failure);
            }
        }

        /** Kept for callers that predate drop-in zips and the layout migration. */
        static void apply(Path userDirectory, PluginStateStore store) { apply(userDirectory, store, null, Version.parse(dev.jasper.sdk.JasperSdk.VERSION)); }

        /**
         * Jars that sit directly in {@code plugins/<id>/} move into {@code jars/}; {@code plugin-data/<id>/} moves to
         * {@code plugins/<id>/data/}. A move that fails (an open jar) is retried at the next launch.
         */
        static void migrateLayout(Path userDirectory, Path legacyDataRootOrNull) {
            if (Files.isDirectory(userDirectory)) {
                List<Path> folders;
                try (var children = Files.list(userDirectory)) {
                    folders = children.filter(Files::isDirectory).filter(path -> !path.getFileName().toString().startsWith(".")).toList();
                } catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Plugins cannot be listed", failure); return; }
                for (Path folder : folders) {
                    List<Path> jars;
                    try (var children = Files.list(folder)) {
                        jars = children.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path)).toList();
                    } catch (IOException failure) { continue; }
                    if (jars.isEmpty()) continue;
                    try {
                        Path target = Files.createDirectories(folder.resolve(PluginDiscovery.JARS));
                        for (Path jar : jars) move(jar, target.resolve(jar.getFileName()));
                        LOG.log(System.Logger.Level.INFO, "Moved the jars of " + folder.getFileName() + " into jars/");
                    } catch (IOException failure) {
                        LOG.log(System.Logger.Level.WARNING, "Could not move the jars of " + folder.getFileName() + " into jars/; retried at the next launch", failure);
                    }
                }
            }
            if (legacyDataRootOrNull == null || !Files.isDirectory(legacyDataRootOrNull)) return;
            List<Path> dataFolders;
            try (var children = Files.list(legacyDataRootOrNull)) { dataFolders = children.filter(Files::isDirectory).toList(); }
            catch (IOException failure) { return; }
            for (Path data : dataFolders) {
                String id = data.getFileName().toString();
                if (!PluginInfo.validId(id)) continue;
                Path target = userDirectory.resolve(id).resolve("data");
                try {
                    if (Files.exists(target)) { LOG.log(System.Logger.Level.WARNING, "Both " + data + " and " + target + " exist; leaving both"); continue; }
                    Files.createDirectories(target.getParent());
                    move(data, target);
                    LOG.log(System.Logger.Level.INFO, "Moved " + data + " to " + target);
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not move " + data + " to " + target, failure);
                }
            }
            try (var remaining = Files.list(legacyDataRootOrNull)) {
                if (remaining.findAny().isEmpty()) Files.delete(legacyDataRootOrNull);
            } catch (IOException ignored) { /* left for a later launch */ }
        }

        /** Every {@code plugins/*.zip} is staged like a manager install, without consent; an unusable one is renamed {@code .rejected}. */
        static void consumeZips(Path userDirectory, PluginStateStore store, Version sdk) {
            if (!Files.isDirectory(userDirectory)) return;
            List<Path> zips;
            try (var children = Files.list(userDirectory)) {
                zips = children.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".zip")
                    && !path.getFileName().toString().startsWith(".")).sorted().toList();
            } catch (IOException failure) { return; }
            for (Path zip : zips) {
                try {
                    PluginInstaller.Staged staged = PluginInstaller.stage(zip, userDirectory, sdk);
                    PluginInstaller.commit(staged, userDirectory, store, false);
                    Files.delete(zip);
                    LOG.log(System.Logger.Level.INFO, "Staged " + zip.getFileName() + " for install as " + staged.candidate().id());
                } catch (PluginInstaller.InstallFailure | IOException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Not a usable plugin zip: " + zip.getFileName() + ": " + failure.getMessage());
                    try { move(zip, zip.resolveSibling(zip.getFileName() + ".rejected")); }
                    catch (IOException unmovable) { LOG.log(System.Logger.Level.WARNING, "Could not set aside " + zip, unmovable); }
                }
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
                                Path jars = userDirectory.resolve(name).resolve(PluginDiscovery.JARS);
                                retire(jars);
                                Files.createDirectories(jars.getParent());
                                move(directory, jars);
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
