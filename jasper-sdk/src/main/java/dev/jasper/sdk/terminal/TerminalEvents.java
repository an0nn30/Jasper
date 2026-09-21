package dev.jasper.sdk.terminal;

import dev.jasper.sdk.events.Topic;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What happens in terminals. Subscribing to any of these topics needs {@code terminal.observe}. Payloads
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
     * A tab.
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

    /** A terminal window opened. */
    public static final Topic<WindowEvent> WINDOW_OPENED = Topic.of(PREFIX + "window_opened", WindowEvent.class);
    /** A terminal window closed. */
    public static final Topic<WindowEvent> WINDOW_CLOSED = Topic.of(PREFIX + "window_closed", WindowEvent.class);
    /** The user turned to a terminal window. */
    public static final Topic<WindowEvent> WINDOW_ACTIVATED = Topic.of(PREFIX + "window_activated", WindowEvent.class);
    /** A tab opened. */
    public static final Topic<TabEvent> TAB_OPENED = Topic.of(PREFIX + "tab_opened", TabEvent.class);
    /** A tab closed. */
    public static final Topic<TabEvent> TAB_CLOSED = Topic.of(PREFIX + "tab_closed", TabEvent.class);
    /** A window's selected tab changed. */
    public static final Topic<TabEvent> TAB_SELECTED = Topic.of(PREFIX + "tab_selected", TabEvent.class);
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
    /** A pane's session started or ended. */
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
