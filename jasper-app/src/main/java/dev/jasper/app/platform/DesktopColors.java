package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static dev.jasper.app.platform.DesktopObservation.*;

/** Application-owned Linux desktop service. Optional native capabilities are acquired asynchronously. */
public final class DesktopColors implements AutoCloseable {
    private record Demand(long generation, Set<Source> sources, boolean dark) { }
    private final Path directory;
    private final ScheduledExecutorService timer;
    private final Executor manager;
    private final LongSupplier clock;
    private final Function<List<String>, Process> launcher;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<Consumer<DesktopObservation>> listeners = new ArrayList<>();
    private final List<Runnable> resampleListeners = new ArrayList<>();
    private final Slot portal = new Slot(null);
    private final Map<Source, Slot> toolkits = new EnumMap<>(Source.class);
    private volatile DesktopObservation current = new DesktopObservation(
        new Portal(0, Polarity.UNKNOWN, Optional.empty(), Optional.empty(), "Desktop preferences pending"), Map.of());
    private DesktopObservation delivered = current;
    private Demand demand = new Demand(0, Set.of(), false), requested = demand;
    private boolean demandQueued;
    private ScheduledFuture<?> publication, resample;
    private ExecutorService ownedManager;
    private boolean ownedTimer;

    /** Fixed executable names in distribution order: portal, GTK 3, GTK 4. */
    public static List<String> helperNames() {
        return List.of("jasper-desktop-portal", "jasper-desktop-gtk3", "jasper-desktop-gtk4");
    }

    /** Absolute development override or helpers beside the installed application jar; never searches PATH or cwd. */
    public static Optional<Path> helperDirectory() {
        return helperDirectory(System.getProperty("os.name", ""), System.getProperty("jasper.desktopColors.helpers"), CodeLocation.of(DesktopColors.class));
    }
    static Optional<Path> helperDirectory(String os, String override, Path jar) {
        if (!os.startsWith("Linux")) return Optional.empty();
        try {
            Path directory;
            if (override != null && !override.isBlank()) directory = Path.of(override);
            else if (jar != null && jar.isAbsolute() && jar.getFileName().toString().endsWith(".jar"))
                directory = jar.resolveSibling("desktop-colors");
            else return Optional.empty();
            return directory.isAbsolute() && java.nio.file.Files.isDirectory(directory)
                ? Optional.of(directory.normalize()) : Optional.empty();
        } catch (java.nio.file.InvalidPathException | SecurityException unavailable) { return Optional.empty(); }
    }

    /** Opens helpers from the absolute, packaged helper directory, without waiting for native initialization. */
    public static DesktopColors open(Path helperDirectory) {
        var manager = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("desktop-colors").factory());
        var timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("desktop-colors-timeout").factory());
        var desktop = new DesktopColors(helperDirectory, timer, manager, System::nanoTime, DesktopProcess::spawn);
        desktop.ownedManager = manager; desktop.ownedTimer = true; return desktop;
    }
    DesktopColors(Path helperDirectory, ScheduledExecutorService timer, Executor manager, LongSupplier clock,
                  Function<List<String>, Process> launcher) {
        directory = Objects.requireNonNull(helperDirectory);
        if (!directory.isAbsolute()) throw new IllegalArgumentException("Desktop helper directory must be absolute");
        this.timer = Objects.requireNonNull(timer); this.manager = Objects.requireNonNull(manager);
        this.clock = Objects.requireNonNull(clock); this.launcher = Objects.requireNonNull(launcher);
        for (Source source : Source.values()) toolkits.put(source, new Slot(source));
        manage(() -> { if (!closed.get()) { start(portal); scheduleResample(); } });
    }
    public DesktopObservation current() { return current; }
    /** Registers future coalesced observations. The application dispatches installation onto EDT. */
    public synchronized Runnable subscribe(Consumer<DesktopObservation> listener) {
        Objects.requireNonNull(listener); if (!closed.get()) listeners.add(listener);
        return () -> { synchronized (this) { listeners.remove(listener); } };
    }
    /**
     * Registers future 30-second sampling boundaries on the manager thread, independently of palette equality.
     * The returned removal is idempotent; removal and close suppress delivery of queued boundaries.
     */
    public synchronized Runnable subscribeResample(Runnable listener) {
        Objects.requireNonNull(listener); if (!closed.get()) resampleListeners.add(listener);
        return () -> { synchronized (this) { resampleListeners.remove(listener); } };
    }
    /** Replaces aggregate demand; the caller's generation belongs to its source/appearance request. */
    public void request(long generation, Set<Source> sources, boolean dark) {
        DesktopObservation.generation(generation); Set<Source> fixed = Set.copyOf(sources);
        synchronized (this) {
            if (closed.get() || generation < requested.generation()) return;
            requested = new Demand(generation, fixed, dark);
            if (demandQueued) return;
            demandQueued = true;
        }
        manage(() -> {
            synchronized (this) { demandQueued = false; demand = requested; }
            if (closed.get()) return;
            var palettes = new EnumMap<Source, Palette>(Source.class);
            current.palettes().forEach((source, value) -> {
                if (demand.sources().contains(source) && value.appearance() == polarity()) palettes.put(source, value);
            });
            update(new DesktopObservation(current.portal(), palettes));
            for (Slot slot : toolkits.values()) {
                if (!active(slot)) stop(slot);
                else if (slot.process == null && slot.retry == null) start(slot);
                else if (slot.process != null) slot.process.sample(demand.generation(), demand.dark());
            }
        });
    }
    /** Reinitializes native capabilities and cancels superseded retries, retaining compatible snapshots. */
    public void reload() {
        manage(() -> {
            if (closed.get()) return;
            stop(portal); start(portal);
            for (Slot slot : toolkits.values()) { stop(slot); if (active(slot)) start(slot); }
        });
    }
    private Polarity polarity() { return demand.dark() ? Polarity.DARK : Polarity.LIGHT; }
    private boolean active(Slot slot) { return slot.source == null || demand.sources().contains(slot.source); }
    private void start(Slot slot) {
        if (closed.get() || !active(slot)) return;
        var process = new DesktopProcess(timer, manager, clock, launcher); slot.process = process; slot.owner = -1;
        String name = helperNames().get(slot.source == null ? 0 : slot.source == Source.GTK3 ? 1 : 2);
        process.launch(List.of(directory.resolve(name).toString(), "--protocol=1"),
            message -> observed(slot, process, message), reason -> failed(slot, process, reason));
        if (slot.source != null) process.sample(demand.generation(), demand.dark());
    }
    private void observed(Slot slot, DesktopProcess process, DesktopProtocol.Message message) {
        if (closed.get() || slot.process != process || !active(slot)) return;
        if (slot.source == null && message instanceof DesktopProtocol.PortalMessage value) {
            // Owner ordering applies within this Java process incarnation only.
            if (slot.owner >= 0 && value.value().owner() < slot.owner) return;
            slot.owner = value.value().owner(); slot.attempt = 0;
            update(new DesktopObservation(value.value(), current.palettes()));
        } else if (slot.source != null && message instanceof DesktopProtocol.PaletteMessage value) {
            Palette palette = value.value();
            Demand latest; synchronized (this) { latest = requested; }
            if (palette.generation() != latest.generation() || !latest.sources().contains(slot.source)) return;
            if (palette.source() != slot.source || !palette.colors().containsKey(Pair.WINDOW)
                || palette.appearance() != (latest.dark() ? Polarity.DARK : Polarity.LIGHT)) {
                process.close();
                failed(slot, process, palette.reason().isBlank() ? "Desktop toolkit did not provide a compatible window pair" : palette.reason(), palette);
                return;
            }
            slot.attempt = 0;
            var palettes = new EnumMap<Source, Palette>(Source.class); palettes.putAll(current.palettes());
            palettes.put(slot.source, palette); update(new DesktopObservation(current.portal(), palettes));
        }
    }
    private void failed(Slot slot, DesktopProcess process, String reason) {
        failed(slot, process, reason, null);
    }
    private void failed(Slot slot, DesktopProcess process, String reason, Palette capability) {
        if (closed.get() || slot.process != process || !active(slot)) return;
        slot.process = null;
        if (slot.source == null) {
            update(new DesktopObservation(new Portal(Math.max(0, slot.owner), Polarity.UNKNOWN, Optional.empty(), Optional.empty(), reason), current.palettes()));
        } else {
            var palettes = new EnumMap<Source, Palette>(Source.class); palettes.putAll(current.palettes());
            Palette prior = palettes.get(slot.source);
            boolean compatible = prior != null && prior.appearance() == polarity();
            Palette metadata = capability != null ? capability : prior;
            palettes.put(slot.source, new Palette(demand.generation(), slot.source, polarity(), compatible ? prior.colors() : Map.of(),
                metadata == null ? "" : metadata.implementation(), metadata == null ? "" : metadata.version(), reason));
            update(new DesktopObservation(current.portal(), palettes));
        }
        long retryToken = ++slot.retryToken;
        slot.retry = timer.schedule(() -> manage(() -> {
            if (slot.retryToken != retryToken) return;
            slot.retry = null;
            if (!closed.get() && active(slot) && slot.process == null) start(slot);
        }), DesktopProcess.retrySeconds(slot.attempt++), TimeUnit.SECONDS);
    }
    private void stop(Slot slot) {
        DesktopProcess process = slot.process; slot.process = null;
        cancel(slot.retry); slot.retry = null; slot.retryToken++; slot.attempt = 0; slot.owner = -1;
        if (process != null) process.close();
    }
    private void scheduleResample() {
        resample = timer.schedule(() -> manage(() -> {
            if (closed.get()) return;
            for (Slot slot : toolkits.values()) if (active(slot) && slot.process != null)
                slot.process.sample(demand.generation(), demand.dark());
            List<Runnable> targets;
            synchronized (this) { targets = List.copyOf(resampleListeners); }
            for (Runnable target : targets) {
                synchronized (this) { if (closed.get()) return; if (!resampleListeners.contains(target)) continue; }
                try { target.run(); }
                catch (RuntimeException failure) {
                    System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Desktop colors resample listener failed", failure);
                }
            }
            scheduleResample();
        }), 30, TimeUnit.SECONDS);
    }
    private void update(DesktopObservation next) {
        if (closed.get() || current.equals(next)) return;
        current = next;
        if (publication == null) publication = timer.schedule(() -> manage(this::publish), 100, TimeUnit.MILLISECONDS);
    }
    private void publish() {
        publication = null;
        if (closed.get() || delivered.equals(current)) return;
        DesktopObservation next = current; delivered = next;
        List<Consumer<DesktopObservation>> targets;
        synchronized (this) { targets = List.copyOf(listeners); }
        for (Consumer<DesktopObservation> target : targets) {
            synchronized (this) { if (closed.get()) return; if (!listeners.contains(target)) continue; }
            try { target.accept(next); }
            catch (RuntimeException failure) { System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Desktop colors listener failed", failure); }
        }
    }
    private void manage(Runnable action) {
        try { manager.execute(action); }
        catch (RejectedExecutionException ignored) { /* Owned executor has retired after close. */ }
    }
    private static void cancel(ScheduledFuture<?> task) { if (task != null) task.cancel(false); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        synchronized (this) { listeners.clear(); resampleListeners.clear(); }
        manage(() -> {
            cancel(publication); cancel(resample); stop(portal); toolkits.values().forEach(this::stop);
            // Process retirement is queued on the same manager before executor shutdown.
            manage(() -> {
                if (ownedManager != null) ownedManager.shutdown();
                if (ownedTimer) timer.shutdown();
            });
        });
    }
    private static final class Slot {
        final Source source; DesktopProcess process; ScheduledFuture<?> retry; int attempt; long owner = -1, retryToken;
        Slot(Source source) { this.source = source; }
    }
}
