package dev.jasper.app.startup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;

/** One window's timing, from the request to its first output painted. Any thread; first write wins. */
public final class WindowTrace {
    public enum Milestone { SHOWN, FIRST_PAINT, OUTPUT_PAINTED }

    private final StartupTrace owner;
    private final String trigger;
    private final long requestedNanos;
    private final AtomicLongArray marks = new AtomicLongArray(Milestone.values().length);
    private final AtomicBoolean completed = new AtomicBoolean();

    WindowTrace(StartupTrace owner, String trigger, long requestedNanos) {
        this.owner = owner;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.requestedNanos = requestedNanos;
        for (int i = 0; i < marks.length(); i++) marks.set(i, StartupTrace.UNSET);
    }

    /** Records {@code milestone} now unless already recorded; output painted completes the trace. */
    public void mark(Milestone milestone) {
        Objects.requireNonNull(milestone, "milestone");
        if (!marks.compareAndSet(milestone.ordinal(), StartupTrace.UNSET, owner.nanos())) return;
        if (milestone == Milestone.OUTPUT_PAINTED) complete();
    }

    /** What asked for the window: cold, menu, reopen, handoff, … */
    public String trigger() { return trigger; }

    /** Milliseconds from the request to {@code milestone}; empty until it is marked. */
    public OptionalDouble millis(Milestone milestone) {
        long at = marks.get(milestone.ordinal());
        return at == StartupTrace.UNSET ? OptionalDouble.empty() : OptionalDouble.of((at - requestedNanos) / 1_000_000.0);
    }

    /** True once output was painted or the trace gave up. */
    public boolean completed() { return completed.get(); }

    void complete() {
        if (completed.compareAndSet(false, true)) owner.windowCompleted(this);
    }

    String line() {
        StringBuilder out = new StringBuilder("Window (").append(trigger).append("):");
        String separator = " ";
        for (Milestone milestone : Milestone.values()) {
            OptionalDouble at = millis(milestone);
            if (at.isEmpty()) continue;
            out.append(separator).append(StartupTrace.label(milestone.name())).append(' ')
                .append(Math.round(at.getAsDouble())).append(" ms");
            separator = ", ";
        }
        if (separator.equals(" ")) out.append(" nothing shown");
        return out.append(" after the request").toString();
    }

    /** This window's JSON entry: the request and each milestone in milliseconds since the process start. */
    Map<String, Object> document() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("trigger", trigger);
        entry.put("requested", StartupTrace.round(owner.sinceStart(requestedNanos)));
        for (Milestone milestone : Milestone.values()) {
            long at = marks.get(milestone.ordinal());
            if (at != StartupTrace.UNSET) entry.put(milestone.name(), StartupTrace.round(owner.sinceStart(at)));
        }
        return entry;
    }
}
