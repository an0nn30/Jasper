package dev.jasper.sdk.terminal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One tab. Handles keep an id and no Swing object; two handles for the same tab are equal. EDT only. */
public interface TabHandle {
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
     * The tab's panes, in creation order; empty once the tab is closed.
     *
     * @return the panes
     */
    List<PaneHandle> panes();

    /**
     * The pane that has, or would get, keyboard focus in this tab.
     *
     * @return the pane, or empty once the tab is closed
     */
    Optional<PaneHandle> activePane();

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
