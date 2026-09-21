package dev.jasper.app.plugins;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * What the Plugins manager can do, synchronously: each operation is one state transaction followed by a
 * fresh look at the disk. Never the EDT. Nothing here loads, unloads or deletes a plugin's jars.
 */
final class PluginAdmin {
    private final PluginRuntime.Options options;
    private final Version sdk;
    private final Supplier<PluginCatalog.Launch> launch;
    private final ToIntFunction<String> errors;
    private final PluginStateStore store;

    PluginAdmin(PluginRuntime.Options options, Version sdk, Supplier<PluginCatalog.Launch> launch,
                ToIntFunction<String> errors, Duration lockWait) {
        this.options = options; this.sdk = sdk; this.launch = launch; this.errors = errors;
        this.store = new PluginStateStore(options.stateFile(), options.lockFile(), lockWait);
    }

    private PluginCatalog.Disk disk() throws IOException {
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);
        Map<String, PluginCandidate> pending = new TreeMap<>();
        for (PluginCandidate staged : PluginDiscovery.scan(options.userDirectory().resolve(PluginMaintenance.PENDING),
                PluginCandidate.Origin.USER, problems)) pending.put(staged.id(), staged);
        return new PluginCatalog.Disk(candidates, pending, store.read());
    }

    PluginRuntime.Snapshot snapshot() throws IOException {
        return PluginCatalog.compute(launch.get(), disk(), sdk, errors);
    }

    PluginRuntime.Snapshot setEnabled(String id, boolean enabled) throws IOException {
        PluginCatalog.Disk disk = disk();
        PluginCandidate subject = PluginCatalog.subject(disk, id).orElseThrow(() -> new IOException("No such plugin: " + id));
        if (subject.origin() == PluginCandidate.Origin.USER && !disk.state().containsKey(id))
            throw new IOException("Review " + subject.descriptor().name() + " before enabling or disabling it");
        store.transact(PluginStateStore.enabling(id, enabled));
        return snapshot();
    }

    /** {@code reviewed} is what the user saw; consent is refused when the plugin on disk declares something else. */
    PluginRuntime.Snapshot consent(String id, List<String> reviewed) throws IOException {
        PluginCandidate subject = PluginCatalog.subject(disk(), id).orElseThrow(() -> new IOException("No such plugin: " + id));
        if (subject.origin() != PluginCandidate.Origin.USER) throw new IOException(subject.descriptor().name() + " needs no consent");
        requireReviewed(subject, reviewed);
        store.transact(PluginStateStore.consenting(id, subject.descriptor().capabilities()));
        return snapshot();
    }

    private static void requireReviewed(PluginCandidate subject, List<String> reviewed) throws IOException {
        if (!new TreeSet<>(subject.descriptor().capabilities()).equals(new TreeSet<>(reviewed)))
            throw new IOException(subject.descriptor().name() + " changed on disk since you reviewed it. Review it again.");
    }

    PluginRuntime.Snapshot remove(String id, boolean remove) throws IOException {
        if (remove) {
            PluginCatalog.Disk disk = disk();
            boolean installed = disk.pending().containsKey(id) || disk.candidates().stream()
                .anyMatch(candidate -> candidate.id().equals(id) && candidate.origin() == PluginCandidate.Origin.USER);
            if (!installed) throw new IOException("Only installed plugins can be removed: " + id);
        }
        try {
            store.transact(state -> {
                if (remove) {
                    // A staged version is nothing anyone runs; it goes now, inside the lock that would install it.
                    try { PluginMaintenance.deleteRecursively(options.userDirectory().resolve(PluginMaintenance.PENDING).resolve(id)); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                }
                return PluginStateStore.removing(id, remove).apply(state);
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
        return snapshot();
    }

    PluginRuntime.Inspection inspect(Path zip) throws PluginInstaller.InstallFailure {
        PluginInstaller.Staged staged = PluginInstaller.stage(zip, options.userDirectory(), sdk);
        PluginDescriptor descriptor = staged.candidate().descriptor();
        boolean update = Files.isDirectory(options.userDirectory().resolve(descriptor.id()))
            || Files.isDirectory(options.userDirectory().resolve(PluginMaintenance.PENDING).resolve(descriptor.id()));
        return new PluginRuntime.Inspection(staged.directory(), descriptor.id(), descriptor.name(), descriptor.version().toString(),
            descriptor.description(), descriptor.vendor(), new ArrayList<>(new TreeSet<>(descriptor.capabilities())), update);
    }

    PluginRuntime.Snapshot install(Path staged, List<String> reviewed) throws IOException {
        Path directory = staged.toAbsolutePath().normalize();
        if (!options.userDirectory().toAbsolutePath().normalize().equals(directory.getParent())
                || !directory.getFileName().toString().startsWith(PluginMaintenance.STAGING_PREFIX) || !Files.isDirectory(directory))
            throw new IOException("Nothing is staged at " + staged);
        List<String> problems = new ArrayList<>();
        Optional<PluginCandidate> candidate = PluginDiscovery.single(directory, PluginCandidate.Origin.USER, problems);
        if (candidate.isEmpty()) throw new IOException("The staged plugin is no longer readable: " + String.join("; ", problems));
        requireReviewed(candidate.get(), reviewed);
        PluginInstaller.commit(new PluginInstaller.Staged(directory, candidate.get()), options.userDirectory(), store);
        return snapshot();
    }

    void discard(Path staged) {
        Path directory = staged.toAbsolutePath().normalize();
        if (options.userDirectory().toAbsolutePath().normalize().equals(directory.getParent())
                && directory.getFileName().toString().startsWith(PluginMaintenance.STAGING_PREFIX)) PluginInstaller.discard(directory);
    }

    PluginRuntime.Snapshot discardInstall(String id) throws IOException {
        if (!dev.jasper.sdk.PluginInfo.validId(id)) throw new IOException("Not a plugin id: " + id);
        PluginInstaller.discardPending(id, options.userDirectory(), store);
        return snapshot();
    }
}
