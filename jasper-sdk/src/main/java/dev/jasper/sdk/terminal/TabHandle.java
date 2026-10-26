package dev.jasper.sdk.terminal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One item of a Jasper window, the one a pane shows, which the SDK calls a tab: a terminal tab,
 * whose {@link #terminal()} is its one pane, or a content tab, such as an editor, a tool view or a plugin's view. A
 * window holds tabs in its tab strip, each holding panes, and {@link WindowHandle#tabs()} lists their items. Handles keep
 * an id and no Swing object; two handles for the same tab are equal.
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
     * The tab's title: a terminal tab's as its header shows it, a content tab's presentation title without the unsaved
     * marker or the detail. Needs {@code terminal.observe}.
     *
     * @return the title; the last known one once the tab is closed
     */
    String title();

    /**
     * The pieces a terminal tab's automatic name is made from that have a value, by name without braces: Jasper's
     * {@code dir}, {@code full}, {@code process} and {@code title}, and each plugin piece set for the tab. Empty for a
     * content tab and once the tab has closed. Needs {@code terminal.observe}. UI thread only.
     *
     * @return the values, each on one line
     * @since 0.22.0
     */
    Map<String, String> namePieces();

    /** Selects the tab in its window. Ignored once the tab is closed. */
    void select();

    /**
     * Whether the tab still exists. A tab Jasper closed with its windows comes back under the same id when they are
     * restored, and a handle for that id is open again; a plugin that keeps handles drops one on
     * {@link TerminalEvents#TAB_CLOSED}.
     *
     * @return true while it is open
     */
    boolean isOpen();
}
