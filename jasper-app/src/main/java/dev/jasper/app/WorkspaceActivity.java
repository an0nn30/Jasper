package dev.jasper.app;

import java.util.Objects;
import java.util.OptionalInt;
import java.time.Duration;
import java.util.function.LongSupplier;

/** EDT-delivered facts from one workspace; application features supply their own policy. */
public final class WorkspaceActivity {
    private WorkspaceActivity() {}
    /** Closed family of local events, not an external extension API. */
    public sealed interface Event permits Started, Finished, TitleChanged, PaneState {}
    /** Captured focus facts at completion time. */
    public record Origin(boolean anyWindowActive, boolean ownWindowActive,
                         boolean ownTabSelected, boolean ownPaneFocused) {}
    /** A running command; elapsed and activate are fast, EDT-only callbacks. */
    public record Started(Object id, String command, LongSupplier elapsedNanos,
                          Runnable activate, boolean watched) implements Event {
        public Started { Objects.requireNonNull(id); Objects.requireNonNull(command);
            Objects.requireNonNull(elapsedNanos); Objects.requireNonNull(activate); }
    }
    /** Immutable completion facts, separate from its optional later UI activation. */
    public record Finished(Object id, String command, OptionalInt exitStatus,
                           Duration duration, Origin origin, Runnable activate) implements Event {
        public Finished { Objects.requireNonNull(id); Objects.requireNonNull(command); Objects.requireNonNull(exitStatus);
            Objects.requireNonNull(duration); Objects.requireNonNull(origin); Objects.requireNonNull(activate); }
    }
    /** Program wording for an opaque pane identity. */
    public record TitleChanged(Object id, String title) implements Event {
        public TitleChanged { Objects.requireNonNull(id); Objects.requireNonNull(title); }
    }
    /** Producer lifetime and attention transitions. */
    public enum State { OPENED, FOCUSED, BLURRED, CLOSED }
    /** No Swing object is retained by a pane identity. */
    public record PaneState(Object id, State state) implements Event {
        public PaneState { Objects.requireNonNull(id); Objects.requireNonNull(state); }
    }
}
