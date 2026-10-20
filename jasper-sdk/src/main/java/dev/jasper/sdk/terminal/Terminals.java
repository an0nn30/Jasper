package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** The windows, tabs and panes of this Jasper process. EDT only. */
public interface Terminals {
    /**
     * The window the user used last, even while Jasper is in the background.
     *
     * @return the window, or empty when none has been active or the last one closed
     */
    Optional<WindowHandle> activeWindow();

    /**
     * The pane of the active window's active tab (the focused pane's, in its shown tab), when that tab is a terminal tab.
     *
     * @return the pane, or empty while a content tab is active or there is no active window ({@link #activeWindow()})
     */
    Optional<PaneHandle> activePane();

    /**
     * Every open Jasper window, in opening order.
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
     * Opens a terminal in a new tab of the window, at the end of its tab strip, and shows it. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.
     * A request with a command ({@link LocalSpec#command()}) runs that program instead of the shell, with the shell's
     * environment, and its pane closes when the program exits with status 0.
     *
     * @param window where
     * @param request what to run
     * @return the new tab's pane, or empty when the window is gone or the profile is unknown
     */
    Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request);

    /**
     * Opens a terminal in a new pane beside the target pane, in the target's tab, and selects it. A local request needs
     * {@code terminal.open}; one naming a profile the host does not list opens nothing.
     * A request with a command ({@link LocalSpec#command()}) runs that program instead of the shell, with the shell's
     * environment, and its pane closes when the program exits with status 0.
     *
     * @param target the pane the new pane goes beside
     * @param direction which side of the target pane the new pane takes
     * @param request what to run
     * @return the new tab's pane, or empty when the target is gone, its session has ended or the profile is unknown
     */
    Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request);

    /**
     * The shells Jasper detected for local panes, in the order its settings list them: on Windows PowerShell,
     * PowerShell 7, Command Prompt and each WSL distribution, elsewhere the login shell, and the user's custom
     * command when one is configured. Needs {@code terminal.open}. UI thread only.
     *
     * @return the profiles; empty when the host lists none, or has not finished detecting them
     * @since 0.9.1
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
     * @since 0.9.1
     */
    default Subscription onProfilesChanged(Runnable listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        return () -> { };
    }

    /**
     * Adds variables to each new local shell, and to each program a {@link LocalSpec#command()} runs. The contributor runs
     * on the UI thread for every launch, must not block or do I/O, and receives Jasper's own environment, read-only; it
     * returns the variables to add, and null adds nothing. A shell's environment is Jasper's own environment, Jasper's
     * own variables, then the contributions, then the user's {@code [terminal.env]}, then {@code TERM} and
     * {@code COLORTERM}. A name must match {@code [A-Za-z_][A-Za-z0-9_]*}; {@code TERM}, {@code COLORTERM},
     * {@code TERM_PROGRAM}, {@code TERM_PROGRAM_VERSION} and names starting {@code JASPER_} are reserved. An invalid or
     * reserved name, and a value holding NUL, are dropped and logged once. When two plugins set one name, the first by
     * plugin id wins and the other is logged. A contributor that throws adds nothing. WSL profiles and provided sessions
     * get no contributions. Needs {@code terminal.environment}. UI thread only. The registration ends with the plugin.
     *
     * @param contributor given Jasper's own environment, returns the variables to add
     * @return the registration
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code terminal.environment}
     * @since 0.17.1
     */
    default Subscription environment(Function<Map<String, String>, Map<String, String>> contributor) {
        java.util.Objects.requireNonNull(contributor, "contributor");
        return () -> { };
    }
}
