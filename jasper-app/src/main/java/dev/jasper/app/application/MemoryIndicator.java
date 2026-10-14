package dev.jasper.app.application;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.vmoptions.HeapReading;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The status bar's memory indicator (spec section 5.3): a contributed entry, {@code 312 of 2048M}, rendered the same in
 * every window, refreshed by one shared timer while it is shown, a window is open and start-up has settled
 * ({@link #ready()}); a click (the {@link #COLLECT} action) runs garbage collection and refreshes. The entry stays
 * hidden until a reading succeeds, and changes only when its text, tooltip or visibility changes. Updates are instant.
 * EDT only.
 */
final class MemoryIndicator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(MemoryIndicator.class.getName());
    static final String STATUS_ID = "app.memory.indicator";
    static final String COLLECT = "app.memory.gc";
    static final Duration INTERVAL = Duration.ofSeconds(3);

    private final ActionEntry collectAction;
    private final StatusEntry entry;
    private final Supplier<HeapReading> readings;
    private final Runnable collect;
    private final BiFunction<Duration, Runnable, Runnable> every;
    private boolean shown;
    private boolean ready;
    private boolean hasReading;
    private boolean failureLogged;
    private int windows;
    private Runnable stop;
    private boolean closed;

    /**
     * @param contributions where the entry and its action live
     * @param readings reads the heap
     * @param collect runs garbage collection
     * @param every starts a repeating task at an interval and returns the way to stop it
     */
    MemoryIndicator(Contributions contributions, Supplier<HeapReading> readings, Runnable collect,
                    BiFunction<Duration, Runnable, Runnable> every) {
        this.readings = Objects.requireNonNull(readings, "readings");
        this.collect = Objects.requireNonNull(collect, "collect");
        this.every = Objects.requireNonNull(every, "every");
        collectAction = contributions.addAction(COLLECT, "Run Garbage Collection", null,
            List.of("memory", "heap", "gc", "garbage collection", "free memory"), Optional.empty(), invocation -> collectNow());
        entry = contributions.addStatus(STATUS_ID, false, Integer.MAX_VALUE);
        entry.setActionId(COLLECT);
        entry.setVisible(false);
    }

    /** Shows or hides the indicator: {@code window.memory_indicator} while the status bar shows. */
    void show(boolean value) {
        if (closed) return;
        shown = value;
        update();
    }

    /** How many windows are open; without one the timer stops. */
    void windows(int count) {
        if (closed) return;
        windows = count;
        update();
    }

    /** Start-up has settled (spec section 6.5): from now on the indicator may read the heap. Idempotent. */
    void ready() {
        if (closed || ready) return;
        ready = true;
        update();
    }

    /** Runs garbage collection; the heap is read again only while the indicator runs. */
    void collectNow() {
        if (closed) return;
        collect.run();
        if (active()) refresh();
    }

    /** Stops the timer and removes the entry and the click action; later calls do nothing. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        stopTimer();
        entry.close();
        collectAction.close();
    }

    /** Swing's repeating timer, as a plain function: start a task at an interval, get back the way to stop it. */
    static Runnable swingTimer(Duration interval, Runnable task) {
        javax.swing.Timer timer = new javax.swing.Timer((int) Math.max(1, Math.min(Integer.MAX_VALUE, interval.toMillis())),
            event -> task.run());
        timer.start();
        return timer::stop;
    }

    private boolean active() { return shown && windows > 0 && ready; }

    private void update() {
        if (active()) {
            if (stop == null) {
                refresh();
                stop = every.apply(INTERVAL, this::refresh);
            }
        } else {
            stopTimer();
        }
        showEntry();
    }

    /** Visible only while it runs and has a reading to show. */
    private void showEntry() {
        boolean visible = active() && hasReading;
        if (entry.visible() != visible) entry.setVisible(visible);
    }

    private void stopTimer() {
        if (stop == null) return;
        stop.run();
        stop = null;
    }

    /** Reads the heap; a failed read is logged once and leaves the entry as it was. */
    private void refresh() {
        if (closed) return;
        try {
            HeapReading reading = readings.get();
            String label = reading.label();
            String tooltip = reading.tooltip();
            if (!label.equals(entry.text())) entry.setText(label);
            if (!tooltip.equals(entry.tooltip())) entry.setTooltip(tooltip);
            hasReading = true;
        } catch (RuntimeException failure) {
            if (!failureLogged) {
                failureLogged = true;
                LOG.log(System.Logger.Level.WARNING, "Could not read the heap for the memory indicator", failure);
            }
        }
        showEntry();
    }
}
