package dev.jasper.terminal.internal.text;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Row identity and prompt marks. Mutations use the owning buffer lock; reads are published snapshots. */
public final class AbsoluteRowState {
    private volatile long discarded;
    private final AtomicLong epoch = new AtomicLong();
    private final List<Long> prompts = new CopyOnWriteArrayList<>();
    public long discarded() { return discarded; }
    public void discard(int count) { discarded += count; prompts.removeIf(row -> row < discarded); }
    public long epoch() { return epoch.get(); }
    public void invalidate() { epoch.incrementAndGet(); }
    public List<Long> prompts() {
        long oldest = discarded;
        return prompts.stream().filter(row -> row >= oldest).toList();
    }
    public boolean recordPrompt(long row) {
        if (!prompts.isEmpty() && prompts.getLast() == row) return false;
        prompts.add(row);
        return true;
    }
    public void clearPrompts() { prompts.clear(); }
}
