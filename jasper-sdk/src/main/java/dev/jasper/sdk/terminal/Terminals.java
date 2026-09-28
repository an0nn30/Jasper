package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Subscription;
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
     * Opens and selects a new tab. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.
     *
     * @param window where
     * @param request what to run
     * @return the new tab's pane, or empty when the window is gone or the profile is unknown
     */
    Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request);

    /**
     * Splits a pane. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.
     *
     * @param target the pane to split
     * @param direction where the new pane goes
     * @param request what to run
     * @return the new pane, or empty when the target is gone, its session has ended or the profile is unknown
     */
    Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request);

    /**
     * The shells Jasper detected for local panes, in the order its settings list them: on Windows PowerShell,
     * PowerShell 7, Command Prompt and each WSL distribution, elsewhere the login shell, and the user's custom
     * command when one is configured. Needs {@code terminal.open}. UI thread only.
     *
     * @return the profiles; empty when the host lists none, or has not finished detecting them
     * @since 0.8.6
     */
    default List<ShellProfileInfo> profiles() { return List.of(); }

    /**
     * Calls the listener on the UI thread after a shell detection completes: the first one, which may finish after
     * the plugin starts, and each later one (for example after the user asks Jasper to detect shells again) whose
     * result differs, such as a WSL distribution or PowerShell 7 appearing or disappearing. Edits to the Custom shell
     * settings may not be reported; read {@link #profiles()} again when the current list matters. Needs
     * {@code terminal.open}. The registration ends with the plugin.
     *
     * @param listener called with nothing; read {@link #profiles()} again
     * @return the registration
     * @since 0.8.6
     */
    default Subscription onProfilesChanged(Runnable listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        return () -> { };
    }
}
