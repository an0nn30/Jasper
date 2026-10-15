package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.settingsui.SchemaPage;
import dev.jasper.app.settingsui.VmOptionsHooks;
import dev.jasper.app.vmoptions.AotCacheRecord;
import dev.jasper.app.vmoptions.LauncherImage;
import dev.jasper.app.vmoptions.VmOptionsCheck;
import dev.jasper.app.vmoptions.VmOptionsFile;
import dev.jasper.app.vmoptions.VmOptionsLaunch;
import dev.jasper.app.vmoptions.VmOptionsSyntax;
import dev.jasper.ui.model.Nodes;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Custom VM options in the running app (spec sections 4.3, 5.1 and 5.2): the System Settings section's hooks, the
 * Help and palette actions' handlers, and the notices after start-up, including a start without the start-up cache
 * (start-up recovery spec 6.4). File I/O and the check run on {@code worker};
 * state lives on the EDT, where {@code ui} delivers results. Nothing here runs on the start-up path until
 * {@link #startupSettled()} or the section asks.
 */
final class VmOptionsFeature {
    private static final System.Logger LOG = System.getLogger(VmOptionsFeature.class.getName());
    private static final List<String> KEYWORDS = List.of("memory", "heap", "xmx", "jvm", "java", "vm options");
    private static final String LAUNCH_FAILED = "launch-failed";

    private final VmOptionsLaunch launch;
    private final Supplier<Optional<LauncherImage>> images;
    private final Function<LauncherImage, VmOptionsCheck> checks;
    private final Executor worker;
    private final Consumer<Runnable> ui;
    private final VmOptionsNotices notices;
    private final BooleanSupplier restart;
    private final Consumer<Path> openInEditor;
    private final Consumer<Path> reveal;
    private final LongSupplier runningHeapMiB;
    private final Supplier<OptionalLong> physicalMemoryMiB;
    private final Duration watchInterval;
    private final List<Runnable> listeners = new ArrayList<>();
    private Runnable heapSaved = () -> { };
    private VmOptionsFile.Contents contents = VmOptionsFile.Contents.ABSENT;
    private VmOptionsFile.Contents atStart;
    private VmOptionsCheck.Findings findings = VmOptionsCheck.Findings.NONE;
    private long bundledMiB = LauncherImage.DEFAULT_MAX_HEAP_MIB;
    private String stamp = "";
    private int generation;
    private boolean started;
    private javax.swing.Timer watch;

    VmOptionsFeature(VmOptionsLaunch launch, Supplier<Optional<LauncherImage>> images, Function<LauncherImage, VmOptionsCheck> checks,
                     Executor worker, Consumer<Runnable> ui, NotificationCenter center, BooleanSupplier restart,
                     Consumer<Path> openInEditor, Consumer<Path> reveal, LongSupplier runningHeapMiB,
                     Supplier<OptionalLong> physicalMemoryMiB, Duration watchInterval) {
        this.launch = Objects.requireNonNull(launch, "launch");
        this.images = Objects.requireNonNull(images, "images");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.restart = Objects.requireNonNull(restart, "restart");
        this.openInEditor = Objects.requireNonNull(openInEditor, "openInEditor");
        this.reveal = Objects.requireNonNull(reveal, "reveal");
        this.runningHeapMiB = Objects.requireNonNull(runningHeapMiB, "runningHeapMiB");
        this.physicalMemoryMiB = Objects.requireNonNull(physicalMemoryMiB, "physicalMemoryMiB");
        this.watchInterval = Objects.requireNonNull(watchInterval, "watchInterval");
        notices = new VmOptionsNotices(center, this::editCustomOptions, this::restartNow);
    }

    /** The System Settings section's hooks. EDT. */
    VmOptionsHooks hooks() {
        var heap = new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", KEYWORDS, new Nodes.Numeric(512, 1_048_576, 256, 0),
            this::savedHeapMiB, () -> (int) Math.min(Integer.MAX_VALUE, bundledMiB), mib -> mib < 512 ? "Use 512 MiB or more." : "",
            this::heapComment, this::saveHeap, () -> launch.launcherManaged() && !contents.parsed().corrupted());
        return new VmOptionsHooks(heap,
            () -> launch.file().map(Path::toString).orElse("Custom VM options apply to the installed Jasper app."),
            this::editCustomOptions, this::revealFile, this::editable, this::stateNotice, this::findingsNotice, this::restartNotice,
            this::restartNow, this::subscribe);
    }

    /** Whether jasper.vmoptions applies to this JVM (Jasper's launcher started it). */
    boolean editable() { return launch.launcherManaged(); }

    /** Runs {@code action} after each successful heap save (the low-memory warning may show again). EDT. */
    void onHeapSaved(Runnable action) { heapSaved = Objects.requireNonNull(action, "action"); }

    /** Opens the file in the user's editor, creating it with the header first. EDT. */
    void editCustomOptions() {
        Optional<Path> target = launch.file();
        if (target.isEmpty()) return;
        Path file = target.get();
        worker.execute(() -> {
            try {
                VmOptionsFile.ensureExists(file);
                openInEditor.accept(file);
            } catch (IOException | RuntimeException failure) {
                ui.accept(() -> notices.failed("Could not open " + file.getFileName(), String.valueOf(failure.getMessage())));
            }
            ui.accept(this::reload);
        });
    }

    /**
     * Once, after start-up settles: reads the file and announces a fallback start or a corrupted file; after a start
     * without the start-up cache (start-up recovery spec 6.4) checks the cache, remembers it when it fails and says so.
     * When the launcher skipped both after failed launches, each notice appears only when its own check fails, and the
     * options' notice also when their check could not run. EDT.
     */
    void startupSettled() {
        if (started) return;
        started = true;
        boolean managed = launch.launcherManaged();
        Optional<String> cacheSkipped = launch.aotCacheSkipped();
        if (!managed && cacheSkipped.isEmpty()) return;
        Optional<Path> file = launch.file();
        boolean fallback = launch.skipped().filter(LAUNCH_FAILED::equals).isPresent();
        boolean cacheFailed = cacheSkipped.filter(LAUNCH_FAILED::equals).isPresent();
        boolean corrupted = launch.corrupted();
        worker.execute(() -> {
            VmOptionsFile.Contents read = file.map(VmOptionsFeature::read).orElse(VmOptionsFile.Contents.ABSENT);
            Optional<LauncherImage> located = images.get();
            Optional<VmOptionsCheck.Findings> found = fallback || cacheFailed ? check(located, read, cacheFailed) : Optional.empty();
            Optional<String> message = found.flatMap(VmOptionsCheck.Findings::fatal).map(VmOptionsCheck.Fatal::message);
            boolean announceFallback = fallback && (!cacheFailed || found.isEmpty() || message.isPresent());
            if (fallback && cacheFailed && found.isEmpty()) {
                LOG.log(System.Logger.Level.INFO, "Jasper started without the custom VM options after failed launches, and they could not be checked");
            } else if (fallback && !announceFallback) {
                LOG.log(System.Logger.Level.INFO, "Jasper started without the custom VM options after failed launches, but their check finds no failure now");
            }
            Optional<String> cacheMessage = cacheFailed ? remember(located, read.parsed(), found) : Optional.empty();
            if (!cacheFailed) cacheSkipped.ifPresent(VmOptionsFeature::logSkippedCache);
            ui.accept(() -> {
                if (managed && atStart == null) {
                    atStart = read;
                    contents = read;
                    located.ifPresent(image -> bundledMiB = image.bundledMaxHeapMiB());
                }
                if (announceFallback) notices.fallbackStart(message);
                cacheMessage.ifPresent(notices::cacheSkipped);
                if (corrupted) file.ifPresent(notices::corrupted);
            });
        });
    }

    /**
     * After a start that skipped the cache after a failed launch (start-up recovery spec 6.4). A failing cache check
     * writes the record the launcher matches, with the options' digest when the failure is theirs, and returns Java's
     * message to announce; a record already holding the same values was announced before, so it is not written or
     * announced again. A passing check, or one that could not run, writes nothing, so the next start tries the cache.
     * Worker.
     */
    private Optional<String> remember(Optional<LauncherImage> located, VmOptionsSyntax.Parsed parsed, Optional<VmOptionsCheck.Findings> checked) {
        Optional<VmOptionsCheck.Findings> ran = checked.filter(VmOptionsCheck.Findings::cacheChecked);
        if (ran.isEmpty() || located.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "The start-up cache was skipped after a failed launch and could not be checked; the next start tries it again");
            return Optional.empty();
        }
        VmOptionsCheck.Findings found = ran.get();
        if (found.cache().isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "The start-up cache was skipped after a failed launch, but its check finds no failure now; the next start tries it again");
            return Optional.empty();
        }
        String message = found.cache().get().message();
        Optional<Path> target = launch.aotCacheRecord();
        if (target.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "The start-up cache was skipped after a failed launch and fails its check, but the launcher named no file to remember it in");
            return Optional.of(message);
        }
        List<String> options = found.cacheUsedOptions() ? VmOptionsSyntax.merge(located.get().javaOptions(), parsed.lines()) : located.get().javaOptions();
        Optional<String> digest = found.cacheUsedOptions() ? VmOptionsLaunch.appliedDigest(parsed) : Optional.empty();
        try {
            AotCacheRecord record = AotCacheRecord.capture(options, digest);
            if (AotCacheRecord.read(target.get()).filter(record::equals).isPresent()) {
                LOG.log(System.Logger.Level.INFO, "The start-up cache was skipped after a failed launch and fails its check again; it is already remembered, so Jasper does not say so again");
                return Optional.empty();
            }
            record.write(target.get());
            LOG.log(System.Logger.Level.WARNING, "The start-up cache was skipped after a failed launch and fails its check; Jasper skips it until a new build replaces it");
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not remember the start-up cache that fails its check after a failed launch; the next start tries it again", failure);
        }
        return Optional.of(message);
    }

    /** One line for a start without the cache that needs no check: remembered, or skipped by hand. */
    private static void logSkippedCache(String reason) {
        LOG.log(System.Logger.Level.INFO, "remembered".equals(reason)
            ? "Jasper started without its start-up cache, which failed its check after an earlier failed launch"
            : "Jasper started without its start-up cache because JASPER_AOT_CACHE_SKIP is set");
    }

    /** The Settings dialog closed: repeats a pending restart as a notification. EDT. */
    void settingsClosed() { if (restartNeeded()) notices.restartPending(); }

    /** Restarts Jasper, or says it cannot. EDT. */
    void restartNow() { if (!restart.getAsBoolean()) notices.restartUnavailable(); }

    /** Reads the file again, then checks it; publishes each result on the EDT. EDT. */
    void reload() {
        if (!launch.launcherManaged()) {
            notifyListeners();
            return;
        }
        int ticket = ++generation;
        Path file = launch.file().orElseThrow();
        worker.execute(() -> {
            VmOptionsFile.Contents read = read(file);
            Optional<LauncherImage> located = images.get();
            String readStamp = stamp(file);
            ui.accept(() -> {
                if (ticket != generation) return;
                contents = read;
                stamp = readStamp;
                if (atStart == null) atStart = read;
                located.ifPresent(image -> bundledMiB = image.bundledMaxHeapMiB());
                notifyListeners();
            });
            VmOptionsCheck.Findings found = check(located, read, false).orElse(VmOptionsCheck.Findings.NONE);
            ui.accept(() -> {
                if (ticket != generation) return;
                findings = found;
                notifyListeners();
            });
        });
    }

    /** One tick of the file watch: reloads when the file's size or modification time changed. EDT. */
    void pollFile() {
        if (!launch.launcherManaged()) return;
        Path file = launch.file().orElseThrow();
        worker.execute(() -> {
            String now = stamp(file);
            ui.accept(() -> { if (!now.equals(stamp)) reload(); });
        });
    }

    private Subscription subscribe(Runnable listener) {
        listeners.add(listener);
        if (listeners.size() == 1) {
            reload();
            if (!watchInterval.isZero() && !watchInterval.isNegative()) {
                watch = new javax.swing.Timer((int) Math.min(Integer.MAX_VALUE, watchInterval.toMillis()), event -> pollFile());
                watch.start();
            }
        }
        return new Subscription(() -> {
            listeners.remove(listener);
            if (listeners.isEmpty() && watch != null) {
                watch.stop();
                watch = null;
            }
        });
    }

    private void notifyListeners() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }

    /** The heap limit in the file (else the bundled default), in MiB: what the next start applies. EDT. */
    int savedHeapMiB() {
        return (int) Math.min(Integer.MAX_VALUE, VmOptionsSyntax.lastMaxHeapMiB(contents.parsed().lines()).orElse(bundledMiB));
    }

    private String heapComment(int mib) {
        if (!launch.launcherManaged()) return "Custom VM options apply to the installed Jasper app.";
        if (contents.parsed().corrupted()) return "Fix jasper.vmoptions first: it contains a NUL character or is not valid UTF-8.";
        String current = "Current: " + runningHeapMiB.getAsLong() + " MiB.";
        OptionalLong physical = physicalMemoryMiB.get();
        return physical.isPresent() && mib > physical.getAsLong()
            ? current + " That is more than this computer's " + physical.getAsLong() + " MiB of memory."
            : current;
    }

    /**
     * Writes the heap limit. The file is read back before the future completes, so the row shows the saved value at once
     * (Settings refreshes it from {@code saved()} as the future completes); a corrupted file is never rewritten.
     */
    private CompletableFuture<Void> saveHeap(int mib) {
        var done = new CompletableFuture<Void>();
        Optional<Path> target = launch.file();
        if (target.isEmpty()) {
            done.completeExceptionally(new IllegalStateException("Custom VM options apply to the installed Jasper app."));
            return done;
        }
        Path file = target.get();
        long bundled = bundledMiB;
        worker.execute(() -> {
            VmOptionsFile.Contents saved;
            String savedStamp;
            try {
                VmOptionsFile.Contents current = VmOptionsFile.read(file);
                if (current.parsed().corrupted()) throw new IOException("it contains a NUL character or is not valid UTF-8.");
                VmOptionsFile.write(file, VmOptionsFile.withMaxHeap(current.text(), mib, bundled));
                saved = VmOptionsFile.read(file);
                savedStamp = stamp(file);
            } catch (IOException | RuntimeException failure) {
                String description = describe(failure);
                done.completeExceptionally(new IOException("Could not save " + file.getFileName() + ": " + description, failure));
                // Spec section 7: the row shows the error and a notification names the path.
                ui.accept(() -> notices.failed("Could not save " + file.getFileName(), file + ": " + description));
                ui.accept(this::reload);
                return;
            }
            ui.accept(() -> {
                contents = saved;
                stamp = savedStamp;
                notifyListeners();
                done.complete(null);
                heapSaved.run();
                reload();
            });
        });
        return done;
    }

    /** Why a write failed, in words: the file system's own message is often only a path (often the temporary file's). */
    private static String describe(Exception failure) {
        if (failure instanceof AccessDeniedException) return "permission denied.";
        if (failure instanceof FileSystemException problem) {
            if ((failure instanceof FileAlreadyExistsException || failure instanceof NotDirectoryException) && problem.getFile() != null) {
                return problem.getFile() + " is in the way.";
            }
            if (problem.getReason() != null) return problem.getReason();
        }
        return String.valueOf(failure.getMessage());
    }

    private void revealFile() {
        Optional<Path> target = launch.file();
        if (target.isEmpty()) return;
        Path file = target.get();
        worker.execute(() -> {
            try {
                reveal.accept(Files.exists(file) ? file : file.toAbsolutePath().getParent());
            } catch (RuntimeException failure) {
                ui.accept(() -> notices.failed("Could not reveal " + file.getFileName(), String.valueOf(failure.getMessage())));
            }
        });
    }

    private boolean restartNeeded() {
        return launch.launcherManaged() && atStart != null && !contents.parsed().corrupted()
            && launch.restartNeeded(contents.parsed(), atStart.parsed());
    }

    private Optional<SchemaPage.Notice> stateNotice() {
        if (!launch.launcherManaged()) {
            return Optional.of(new SchemaPage.Notice("Applies to the installed app", "Custom VM options apply to the installed Jasper app."));
        }
        if (launch.skipped().filter("launch-failed"::equals).isPresent()) {
            return Optional.of(new SchemaPage.Notice("Started without your options",
                "Jasper started without your custom VM options this time because the JVM rejected them."));
        }
        if (launch.skipped().isPresent()) {
            return Optional.of(new SchemaPage.Notice("Started without your options",
                "JASPER_VM_OPTIONS_SKIP is set, so Jasper started without your custom VM options."));
        }
        if (contents.parsed().corrupted()) {
            return Optional.of(new SchemaPage.Notice("Options ignored",
                "jasper.vmoptions contains a NUL character or is not valid UTF-8; fix it to apply your options."));
        }
        if (launch.corrupted()) {
            return Optional.of(new SchemaPage.Notice("Options ignored at start-up",
                "jasper.vmoptions could not be read when Jasper started, so it started with the bundled options."));
        }
        return Optional.empty();
    }

    private Optional<SchemaPage.Notice> findingsNotice() {
        // Spec section 5.1's wording; "Not an option, ignored" reports the lines the launcher skips (spec 3.2).
        var sentences = new ArrayList<String>();
        for (String line : contents.parsed().ignored()) sentences.add("Not an option, ignored: " + line + ".");
        for (String line : findings.unrecognized()) sentences.add("Unrecognized option, ignored: " + line + ".");
        findings.fatal().ifPresent(fatal -> sentences.add("Jasper can't start with these options: " + fatal.message() + ". "
            + (fatal.recoverable() ? "It will start with the bundled options instead."
                : "Fix the file before restarting; setting JASPER_VM_OPTIONS_SKIP=1 starts Jasper without it.")));
        // Start-up recovery spec 6.3: only a failing cache check that ran with the options blames them.
        if (findings.cache().isPresent() && findings.cacheUsedOptions()) sentences.add("These options turn off Jasper's start-up cache; it will start more slowly.");
        if (sentences.isEmpty()) return Optional.empty();
        return Optional.of(new SchemaPage.Notice(findings.fatal().isPresent() ? "Jasper won't start with these options" : "Check your options",
            String.join(" ", sentences)));
    }

    private Optional<SchemaPage.Notice> restartNotice() {
        return restartNeeded() ? Optional.of(new SchemaPage.Notice("Restart required", "Restart Jasper to apply your changes.")) : Optional.empty();
    }

    /**
     * The check's findings, with the cache checked even without custom options when {@code withCache}; empty when the
     * check could not run (no launcher image, a failure, a run over its time limit). That never blocks saving, and a
     * failure is logged (spec section 7).
     */
    private Optional<VmOptionsCheck.Findings> check(Optional<LauncherImage> located, VmOptionsFile.Contents read, boolean withCache) {
        if (located.isEmpty()) return Optional.empty();
        try {
            VmOptionsCheck check = checks.apply(located.get());
            return withCache ? check.checkWithCache(read.parsed()) : check.check(read.parsed());
        } catch (IOException | RuntimeException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Could not check the custom VM options", unavailable);
            return Optional.empty();
        }
    }

    private static VmOptionsFile.Contents read(Path file) {
        try {
            return VmOptionsFile.read(file);
        } catch (IOException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "Could not read " + file, unreadable);
            return VmOptionsFile.Contents.ABSENT;
        }
    }

    private static String stamp(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file) : "absent";
        } catch (IOException unreadable) {
            return "unreadable";
        }
    }
}
