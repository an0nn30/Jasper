package dev.jasper.sdk.terminal;

import dev.jasper.sdk.events.Topic;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What happens in Jasper's windows, their tabs and terminal tabs' panes. Subscribing to any of these topics needs
 * {@code terminal.observe}. Payloads
 * name windows, tabs and panes by id, because a handle belongs to the plugin that obtained it; ask
 * {@link Terminals} for one. Like every event they arrive later, on the event thread, and are not
 * replayed: read the current state first, then listen.
 */
public final class TerminalEvents {
    /** The prefix of every terminal topic id. */
    public static final String PREFIX = "jasper.terminal.";

    /**
     * A window.
     *
     * @param windowId the window
     */
    public record WindowEvent(UUID windowId) {
        /** Rejects null. */
        public WindowEvent { Objects.requireNonNull(windowId, "windowId"); }
    }

    /**
     * A tab, terminal or content.
     *
     * @param windowId its window
     * @param tabId the tab
     */
    public record TabEvent(UUID windowId, UUID tabId) {
        /** Rejects nulls. */
        public TabEvent { Objects.requireNonNull(windowId, "windowId"); Objects.requireNonNull(tabId, "tabId"); }
    }

    /**
     * A pane.
     *
     * @param tabId its tab
     * @param paneId the pane
     */
    public record PaneEvent(UUID tabId, UUID paneId) {
        /** Rejects nulls. */
        public PaneEvent { Objects.requireNonNull(tabId, "tabId"); Objects.requireNonNull(paneId, "paneId"); }
    }

    /**
     * A tab moved to another window, keeping its id, its content and a terminal tab's pane, whose id stays too: the move
     * reports no {@code TAB_CLOSED} or {@code TAB_OPENED} and no {@code PANE_OPENED} or {@code PANE_CLOSED}. A window
     * opened for the move reports {@code WINDOW_OPENED} before it, and a window the move empties {@code WINDOW_CLOSED}
     * after it.
     *
     * @param tabId the tab
     * @param fromWindowId the window it left
     * @param toWindowId the window it is in now
     * @since 0.19.0
     */
    public record TabMoved(UUID tabId, UUID fromWindowId, UUID toWindowId) {
        /** Rejects nulls and a move to the window the tab is in. */
        public TabMoved {
            Objects.requireNonNull(tabId, "tabId"); Objects.requireNonNull(fromWindowId, "fromWindowId");
            Objects.requireNonNull(toWindowId, "toWindowId");
            if (fromWindowId.equals(toWindowId)) throw new IllegalArgumentException("A move goes to another window");
        }
    }

    /**
     * {@link Terminals#activePane()} changed.
     *
     * @param paneId the pane that is active now, or empty
     */
    public record ActivePaneChanged(Optional<UUID> paneId) {
        /** Rejects null. */
        public ActivePaneChanged { Objects.requireNonNull(paneId, "paneId"); }
    }

    /**
     * A pane's title changed.
     *
     * @param paneId the pane
     * @param title the new title
     */
    public record TitleChanged(UUID paneId, String title) {
        /** Rejects nulls. */
        public TitleChanged { Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(title, "title"); }
    }

    /**
     * The shell reported a working directory.
     *
     * @param paneId the pane
     * @param workingDirectory the local directory, when the report was local
     * @param remoteDirectory the remote directory, when it was not
     */
    public record CwdChanged(UUID paneId, Optional<Path> workingDirectory, Optional<RemoteDirectory> remoteDirectory) {
        /** Rejects nulls. */
        public CwdChanged {
            Objects.requireNonNull(paneId, "paneId");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(remoteDirectory, "remoteDirectory");
        }
    }

    /**
     * A command began, in a shell with integration.
     *
     * @param paneId the pane
     * @param command the command line
     */
    public record CommandStarted(UUID paneId, String command) {
        /** Rejects nulls. */
        public CommandStarted { Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(command, "command"); }
    }

    /**
     * A command finished, in a shell with integration.
     *
     * @param paneId the pane
     * @param command the command line
     * @param exitStatus its exit status, when the shell reported one
     * @param duration how long it ran
     * @param workingDirectory the local directory it ran in, when known
     * @param remoteDirectory the remote directory it ran in, when the shell reported one
     */
    public record CommandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration,
                                  Optional<Path> workingDirectory, Optional<RemoteDirectory> remoteDirectory) {
        /** Rejects nulls. */
        public CommandFinished {
            Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(command, "command");
            Objects.requireNonNull(exitStatus, "exitStatus"); Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(workingDirectory, "workingDirectory"); Objects.requireNonNull(remoteDirectory, "remoteDirectory");
        }
    }

    /**
     * A pane's session changed state.
     *
     * @param paneId the pane
     * @param state the new state
     * @param exitStatus the exit status, when it exited with a known one
     */
    public record SessionStateChanged(UUID paneId, SessionState state, OptionalInt exitStatus) {
        /** Rejects nulls. */
        public SessionStateChanged {
            Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(exitStatus, "exitStatus");
        }
    }

    /** A Jasper window, the main window or a child window, opened. */
    public static final Topic<WindowEvent> WINDOW_OPENED = Topic.of(PREFIX + "window_opened", WindowEvent.class);
    /** A Jasper window closed; the main window stays open when its last tab closes. */
    public static final Topic<WindowEvent> WINDOW_CLOSED = Topic.of(PREFIX + "window_closed", WindowEvent.class);
    /** The user turned to a Jasper window. */
    public static final Topic<WindowEvent> WINDOW_ACTIVATED = Topic.of(PREFIX + "window_activated", WindowEvent.class);
    /** A tab opened: a terminal tab or a content tab. */
    public static final Topic<TabEvent> TAB_OPENED = Topic.of(PREFIX + "tab_opened", TabEvent.class);
    /** A tab closed; a tab that moved to another window reports {@link #TAB_MOVED} instead. */
    public static final Topic<TabEvent> TAB_CLOSED = Topic.of(PREFIX + "tab_closed", TabEvent.class);
    /** A window's active tab, the focused pane's item in its shown tab, changed. */
    public static final Topic<TabEvent> TAB_SELECTED = Topic.of(PREFIX + "tab_selected", TabEvent.class);
    /**
     * A tab moved to another window; see {@link TabMoved}.
     *
     * @since 0.19.0
     */
    public static final Topic<TabMoved> TAB_MOVED = Topic.of(PREFIX + "tab_moved", TabMoved.class);
    /** A pane opened. */
    public static final Topic<PaneEvent> PANE_OPENED = Topic.of(PREFIX + "pane_opened", PaneEvent.class);
    /** A pane closed. */
    public static final Topic<PaneEvent> PANE_CLOSED = Topic.of(PREFIX + "pane_closed", PaneEvent.class);
    /** A pane took keyboard focus. */
    public static final Topic<PaneEvent> PANE_FOCUSED = Topic.of(PREFIX + "pane_focused", PaneEvent.class);
    /** The active pane changed, for whatever reason: focus, tab selection, window activation, or a close. */
    public static final Topic<ActivePaneChanged> ACTIVE_PANE_CHANGED = Topic.of(PREFIX + "active_pane_changed", ActivePaneChanged.class);
    /** A pane's title changed. */
    public static final Topic<TitleChanged> TITLE_CHANGED = Topic.of(PREFIX + "title_changed", TitleChanged.class);
    /** A shell reported its working directory. */
    public static final Topic<CwdChanged> CWD_CHANGED = Topic.of(PREFIX + "cwd_changed", CwdChanged.class);
    /** A command began. */
    public static final Topic<CommandStarted> COMMAND_STARTED = Topic.of(PREFIX + "command_started", CommandStarted.class);
    /** A command finished. */
    public static final Topic<CommandFinished> COMMAND_FINISHED = Topic.of(PREFIX + "command_finished", CommandFinished.class);
    /**
     * A pane's session changed state: {@code CONNECTING} when a provided session begins a connection attempt,
     * {@code RUNNING} when a session starts, {@code EXITED} when a running session ends.
     */
    public static final Topic<SessionStateChanged> SESSION_STATE_CHANGED = Topic.of(PREFIX + "session_state_changed", SessionStateChanged.class);
    /** A pane rang the bell. */
    public static final Topic<PaneEvent> BELL = Topic.of(PREFIX + "bell", PaneEvent.class);

    private TerminalEvents() { }

    /**
     * Whether a topic is one of these, and so needs {@code terminal.observe}.
     *
     * @param topic any topic
     * @return true for a terminal topic
     */
    public static boolean owns(Topic<?> topic) { return topic.id().startsWith(PREFIX); }
}
