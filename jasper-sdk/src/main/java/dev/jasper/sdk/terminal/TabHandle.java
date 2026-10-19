package dev.jasper.sdk.terminal;

import java.util.Optional;
import java.util.UUID;

/**
 * One tab of a Jasper window: a terminal tab, whose {@link #terminal()} is its one pane, or a content tab, such as an
 * editor, a tool view or a plugin's view. Handles keep an id and no Swing object; two handles for the same tab are equal.
 * EDT only, except {@link #id()} and {@link #kind()}.
 */
public interface TabHandle {
    /**
     * What a tab holds. A tab keeps its kind for its whole life.
     *
     * @since 0.19.0
     */
    enum Kind {
        /** A terminal: one pane, so one session. */
        TERMINAL,
        /** Anything else: an editor, a tool view or a plugin's view. */
        CONTENT
    }

    /**
     * The tab's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The window the tab was last seen in.
     *
     * @return its handle
     */
    WindowHandle window();

    /**
     * What the tab holds. Needs no capability.
     *
     * @return its kind
     * @since 0.19.0
     */
    Kind kind();

    /**
     * A terminal tab's pane. Needs no capability.
     *
     * @return the pane, or empty for a content tab and once the tab is closed
     * @since 0.19.0
     */
    Optional<PaneHandle> terminal();

    /**
     * The tab's title as its header shows it. Needs {@code terminal.observe}.
     *
     * @return the title; the last known one once the tab is closed
     */
    String title();

    /** Selects the tab in its window. Ignored once the tab is closed. */
    void select();

    /**
     * Whether the tab still exists.
     *
     * @return true while it is open
     */
    boolean isOpen();
}
