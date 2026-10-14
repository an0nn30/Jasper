package dev.jasper.app.application;

import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.vmoptions.LowMemoryWatch;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The low-memory warning (spec section 5.4): the heap pool's threshold signals and an {@code OutOfMemoryError} raise
 * "Jasper is running low on memory" in the Memory &amp; VM options group, at most once until the user changes the
 * heap. {@link #signal()} and {@link #outOfMemory()} run on any thread; the rest on the EDT.
 */
final class MemoryWarnings implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(MemoryWarnings.class.getName());
    /** What Adjust Memory Settings… adds to the current maximum, in MiB. */
    static final long ADJUST_STEP_MIB = 512;
    private static final int CAUSE_DEPTH = 16;

    private final LowMemoryWatch watch;
    private final Function<Runnable, Runnable> watchHeap;
    private final NotificationCenter center;
    private final LongSupplier runningHeapMiB;
    private final LongSupplier savedHeapMiB;
    private final Consumer<String> openSettings;
    private Runnable stop;
    private boolean closed;

    /**
     * @param watch the throttling rules
     * @param watchHeap starts calling its argument on each heap threshold signal; returns the way to stop
     * @param center posts the warning
     * @param runningHeapMiB this JVM's heap limit in MiB
     * @param savedHeapMiB the heap limit saved in {@code jasper.vmoptions}, which a restart applies, in MiB
     * @param openSettings opens Settings at a page and fragment, such as {@code system#vm.heap=2560}
     */
    MemoryWarnings(LowMemoryWatch watch, Function<Runnable, Runnable> watchHeap, NotificationCenter center,
                   LongSupplier runningHeapMiB, LongSupplier savedHeapMiB, Consumer<String> openSettings) {
        this.watch = Objects.requireNonNull(watch, "watch");
        this.watchHeap = Objects.requireNonNull(watchHeap, "watchHeap");
        this.center = Objects.requireNonNull(center, "center");
        this.runningHeapMiB = Objects.requireNonNull(runningHeapMiB, "runningHeapMiB");
        this.savedHeapMiB = Objects.requireNonNull(savedHeapMiB, "savedHeapMiB");
        this.openSettings = Objects.requireNonNull(openSettings, "openSettings");
    }

    /** Once, after start-up settles: starts watching the heap; nothing after {@link #close()}. EDT. */
    void start() {
        if (closed) return;
        if (stop == null) stop = watchHeap.apply(this::signal);
    }

    /** Stops watching the heap and releases its threshold; later calls and a later {@link #start()} do nothing. EDT. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        Runnable release = stop;
        stop = null;
        if (release != null) release.run();
    }

    /** A heap threshold signal. Any thread. */
    void signal() {
        if (watch.signal()) raise();
    }

    /** An {@code OutOfMemoryError} reached the uncaught-exception handler. Any thread; never throws. */
    void outOfMemory() {
        try {
            if (watch.outOfMemory()) raise();
        } catch (RuntimeException | Error failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not warn that Jasper ran out of memory", failure);
        }
    }

    /** The user saved a new heap limit: the warning may show again. EDT. */
    void heapChanged() { watch.heapChanged(); }

    /** Whether {@code failure}, or one of its causes, is an {@code OutOfMemoryError}. */
    static boolean isOutOfMemory(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (cause instanceof OutOfMemoryError) return true;
        }
        return false;
    }

    private void raise() { center.submit(VmOptionsNotices.lowMemory(this::adjust)); }

    /**
     * Adjust Memory Settings…: the heap field, pre-filled with the current maximum plus 512 MiB; when the saved heap is
     * already higher (saved but not yet applied by a restart), only the field, so Apply cannot lower it. EDT.
     */
    private void adjust() {
        long proposal = runningHeapMiB.getAsLong() + ADJUST_STEP_MIB;
        openSettings.accept(proposal > savedHeapMiB.getAsLong() ? "system#vm.heap=" + proposal : "system#vm.heap");
    }
}
