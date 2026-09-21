package dev.jasper.sdk.terminal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The windows, tabs and panes of this Jasper process. EDT only. */
public interface Terminals {
    /**
     * The window the user used last, even while Jasper is in the background.
     *
     * @return the window, or empty when none has been active or the last one closed
     */
    Optional<WindowHandle> activeWindow();

    /**
     * The focused pane of the active window's selected tab.
     *
     * @return the pane, or empty
     */
    Optional<PaneHandle> activePane();

    /**
     * Every open terminal window, in opening order.
     *
     * @return the windows
     */
    List<WindowHandle> windows();

    /**
     * An open pane by id, as events name it.
     *
     * @param id the pane id
     * @return the pane, or empty when it is not open
     */
    Optional<PaneHandle> pane(UUID id);

    /**
     * An open tab by id.
     *
     * @param id the tab id
     * @return the tab, or empty when it is not open
     */
    Optional<TabHandle> tab(UUID id);

    /**
     * An open window by id.
     *
     * @param id the window id
     * @return the window, or empty when it is not open
     */
    Optional<WindowHandle> window(UUID id);

    /**
     * Opens and selects a new tab. A local request needs {@code terminal.open}.
     *
     * @param window where
     * @param request what to run
     * @return the new tab's pane, or empty when the window is gone
     */
    Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request);

    /**
     * Splits a pane. A local request needs {@code terminal.open}.
     *
     * @param target the pane to split
     * @param direction where the new pane goes
     * @param request what to run
     * @return the new pane, or empty when the target is gone or its session has ended
     */
    Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request);
}
