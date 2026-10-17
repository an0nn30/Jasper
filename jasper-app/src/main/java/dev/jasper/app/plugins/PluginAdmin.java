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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

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
        var state = store.read();
        var at = launch.get();
        var attempts = new dev.jasper.app.presentation.AttemptStore(options.stateFile().toAbsolutePath().getParent()).read();
        var electable = PluginCatalog.electable(candidates, pending, state, sdk, at.safeMode());
        return new PluginCatalog.Disk(candidates, pending, state, Presentations.read(electable, attempts, at.build()));
    }

    PluginRuntime.Snapshot snapshot() throws IOException {
        return PluginCatalog.compute(launch.get(), disk(), sdk, errors, options.userDirectory());
    }

    PluginRuntime.Snapshot setEnabled(String id, boolean enabled) throws IOException {
        PluginCatalog.Disk disk = disk();
        PluginCandidate subject = PluginCatalog.subject(disk, id).orElseThrow(() -> new IOException("No such plugin: " + id));
        PluginStateStore.Entry entry = disk.state().get(id);
        if (subject.origin() == PluginCandidate.Origin.USER && (entry == null || entry.optIn()))
            throw new IOException("Review " + subject.descriptor().name() + " before enabling or disabling it");
        boolean optIn = enabled && subject.origin() == PluginCandidate.Origin.BUNDLED && !subject.descriptor().enabledByDefault();
        if (enabled && subject.descriptor().declaresPresentation()) {
            var others = new TreeMap<String, String>();
            store.transact(turningOthersOff(disk, id, others, ids -> PluginStateStore.enablingPresentation(id, optIn, ids)));
            return snapshot().withTurnedOff(others.values());
        }
        store.transact(PluginStateStore.enabling(id, enabled, optIn));
        return snapshot();
    }

    /** {@code reviewed} is what the user saw; consent is refused when the plugin on disk declares something else. */
    PluginRuntime.Snapshot consent(String id, List<String> reviewed) throws IOException {
        PluginCatalog.Disk disk = disk();
        PluginCandidate subject = PluginCatalog.subject(disk, id).orElseThrow(() -> new IOException("No such plugin: " + id));
        if (subject.origin() != PluginCandidate.Origin.USER) throw new IOException(subject.descriptor().name() + " needs no consent");
        requireReviewed(subject, reviewed);
        if (subject.descriptor().declaresPresentation()) {
            var others = new TreeMap<String, String>();
            store.transact(turningOthersOff(disk, id, others,
                ids -> PluginStateStore.consentingPresentation(id, subject.descriptor().capabilities(), ids)));
            return snapshot().withTurnedOff(others.values());
        }
        store.transact(PluginStateStore.consenting(id, subject.descriptor().capabilities()));
        return snapshot();
    }

    /**
     * An edit that decides, from the locked state, which other providers to turn off and applies {@code edit} with them
     * (spec 4.4); {@code turnedOff} receives their ids and names. The candidates come from the disk scan, which the lock
     * does not change.
     */
    private static UnaryOperator<Map<String, PluginStateStore.Entry>> turningOthersOff(PluginCatalog.Disk disk, String id,
            Map<String, String> turnedOff, Function<Set<String>, UnaryOperator<Map<String, PluginStateStore.Entry>>> edit) {
        return state -> {
            turnedOff.clear();
            turnedOff.putAll(otherProviders(disk, state, id));
            return edit.apply(Set.copyOf(turnedOff.keySet())).apply(state);
        };
    }

    /**
     * The other presentation providers the next start would treat as enabled, by id with their names: effectively enabled
     * and, for a user copy, reviewed.
     */
    private static Map<String, String> otherProviders(PluginCatalog.Disk disk, Map<String, PluginStateStore.Entry> state, String id) {
        var others = new TreeMap<String, String>();
        var all = new ArrayList<>(disk.candidates());
        all.addAll(disk.pending().values());
        for (PluginCandidate candidate : all) {
            if (candidate.id().equals(id) || !candidate.descriptor().declaresPresentation()) continue;
            PluginStateStore.Entry entry = state.get(candidate.id());
            boolean unreviewed = candidate.origin() == PluginCandidate.Origin.USER && (entry == null || entry.optIn());
            if (PluginStateStore.effective(entry, candidate) && !unreviewed) others.putIfAbsent(candidate.id(), candidate.descriptor().name());
        }
        return others;
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
        PluginDescriptor descriptor = candidate.get().descriptor();
        var staging = new PluginInstaller.Staged(directory, candidate.get());
        if (descriptor.declaresPresentation()) {
            var others = new TreeMap<String, String>();
            PluginInstaller.commit(staging, options.userDirectory(), store, turningOthersOff(disk(), descriptor.id(), others,
                ids -> PluginStateStore.consentingPresentation(descriptor.id(), descriptor.capabilities(), ids)));
            return snapshot().withTurnedOff(others.values());
        }
        PluginInstaller.commit(staging, options.userDirectory(), store);
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
