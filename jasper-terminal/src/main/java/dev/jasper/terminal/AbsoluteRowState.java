package dev.jasper.terminal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Row identity and prompt marks. Mutations use the owning buffer lock; reads are published snapshots. */
final class AbsoluteRowState {
    private volatile long discarded;
    private final AtomicLong epoch = new AtomicLong();
    private final List<Long> prompts = new CopyOnWriteArrayList<>();
    long discarded() { return discarded; }
    void discard(int count) { discarded += count; prompts.removeIf(row -> row < discarded); }
    long epoch() { return epoch.get(); }
    void invalidate() { epoch.incrementAndGet(); }
    List<Long> prompts() {
        long oldest = discarded;
        return prompts.stream().filter(row -> row >= oldest).toList();
    }
    boolean recordPrompt(long row) {
        if (!prompts.isEmpty() && prompts.getLast() == row) return false;
        prompts.add(row);
        return true;
    }
    void clearPrompts() { prompts.clear(); }
}
