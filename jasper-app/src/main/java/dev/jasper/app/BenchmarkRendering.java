package dev.jasper.app;

import dev.jasper.terminal.view.TerminalView;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Counts visible-terminal dirty paint passes, not physical monitor frames. Installed only by benchmarks. */
final class BenchmarkRendering extends RepaintManager {
    private final List<Double> intervals = new ArrayList<>();
    private final List<Double> delays = new ArrayList<>();
    private final AtomicBoolean probePending = new AtomicBoolean();
    private boolean terminalDirty;
    private long previousPaint;
    private long passes;

    @Override public synchronized void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
        if (component instanceof TerminalView && component.isShowing() && width > 0 && height > 0) terminalDirty = true;
        super.addDirtyRegion(component, x, y, width, height);
    }
    @Override public void paintDirtyRegions() {
        boolean dirty;
        synchronized (this) { dirty = terminalDirty; terminalDirty = false; }
        super.paintDirtyRegions();
        if (dirty) {
            long now = System.nanoTime();
            synchronized (this) {
                passes++;
                if (previousPaint != 0 && intervals.size() < 100000) intervals.add((now - previousPaint) / 1e6);
                previousPaint = now;
            }
        }
    }
    void probe() {
        if (!probePending.compareAndSet(false, true)) return;
        long queued = System.nanoTime();
        SwingUtilities.invokeLater(() -> {
            synchronized (this) { if (delays.size() < 100000) delays.add((System.nanoTime() - queued) / 1e6); }
            probePending.set(false);
        });
    }
    synchronized Map<String, Object> snapshot() {
        return Map.of("visibleTerminalDirtyPaintPasses", passes,
            "paintPassIntervalMillis", BenchmarkReport.summary(List.copyOf(intervals)),
            "edtSchedulingDelayMillis", BenchmarkReport.summary(List.copyOf(delays)));
    }
    synchronized void reset() { passes = 0; previousPaint = 0; intervals.clear(); delays.clear(); }
}
