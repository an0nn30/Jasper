package dev.jasper.sdk.terminal;

import dev.jasper.sdk.WindowOwner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One terminal window. Handles keep an id and no Swing object; two handles for the same window are equal. EDT only. */
public interface WindowHandle extends WindowOwner {
    /**
     * The window's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The window's terminal tabs, in display order; content tabs, such as Editor documents, are not listed. Empty once
     * the window is closed.
     *
     * @return the tabs
     */
    List<TabHandle> tabs();

    /**
     * The selected terminal tab.
     *
     * @return the tab; empty while a content tab is selected or once the window is closed
     */
    Optional<TabHandle> activeTab();

    /**
     * Whether this window has the user's attention right now.
     *
     * @return true while it is the active window of a foreground Jasper
     */
    boolean isActive();

    /**
     * Whether the window still exists.
     *
     * @return true while it is open
     */
    boolean isOpen();

    /** Raises the window. Ignored once it is closed. */
    void toFront();
}
