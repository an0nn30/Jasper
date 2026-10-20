package dev.jasper.remote.api;

import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Projects on Remote's saved hosts, for plugins such as Project Index. Published by Remote with
 * {@code Services.publish}. Call every method on the UI thread. A stage completes on a worker thread once its remote
 * work has started; a stage that fails before then (no connection, or a failed sign-in) completes on the UI thread and
 * may already be complete when returned. Methods without a window never connect or ask the user to sign in.
 */
public interface RemoteProjects {
    /**
     * Where an SSH pane Remote opened is: its saved host and the folder its shell last reported.
     *
     * @param paneId a terminal pane's {@link dev.jasper.sdk.terminal.PaneHandle#id() id}
     * @return the saved host and folder of an SSH pane Remote opened, when its folder is known; empty for another
     *     pane and when the host is no longer saved
     */
    Optional<RemoteFolder> locate(UUID paneId);

    /**
     * Whether Remote holds an authenticated connection to a saved host, which {@link #exists} and a {@link #list}
     * without a sign-in window need.
     *
     * @param hostId a saved host
     * @return whether an authenticated connection to it is open, so background work can use it
     */
    boolean connected(UUID hostId);

    /**
     * The name a saved host has now.
     *
     * @param hostId a saved host
     * @return its name, or empty when it is no longer saved
     */
    Optional<String> hostName(UUID hostId);

    /**
     * The id of Remote's file source, {@code dev.jasper.remote.hosts}, whose roots are the saved hosts. Name it in a
     * {@link dev.jasper.sdk.files.FileChoice}'s {@code sources} so Jasper's file chooser offers the hosts, then turn
     * what it returns into a host and folder with {@link #folder}.
     *
     * @return the id of Remote's file source, for {@code FileChoice.sources}
     */
    String fileSourceId();

    /**
     * The saved host and path a location from Remote's file source names.
     *
     * @param location a location Jasper's file chooser returned from Remote's file source
     * @return the host and folder, or empty for another source, an unknown host or an unresolved home folder
     */
    Optional<RemoteFolder> folder(FileLocation.Sourced location);

    /**
     * Which names exist in which folders, in one remote command over an open connection. Names with {@code *} are
     * globs. The filesystem root, the login's home folder and its ancestors report nothing.
     *
     * @param hostId a saved host
     * @param folders absolute folders
     * @param names file or folder names, or globs
     * @return folder to the names found there; fails with {@link NotConnectedException} without a connection, and with
     *     an {@code IOException} when the command fails or runs longer than 10 seconds
     */
    CompletionStage<Map<String, Set<String>>> exists(UUID hostId, List<String> folders, Set<String> names);

    /**
     * Lists a project's files: Git's view of a work tree, else {@code find}, else an SFTP walk.
     *
     * @param hostId a saved host
     * @param root the project's absolute root folder
     * @param options excludes, limit, timeout and cancellation
     * @param signIn the window to connect and ask for sign-in in; empty fails with {@link NotConnectedException}
     *     when no connection is open
     * @return the listing; fails with {@code NoSuchFileException} when the root cannot be entered, with an
     *     {@code IOException} when the options' timeout passes, and with {@code CancellationException} once their
     *     cancelled check answers true
     */
    CompletionStage<RemoteListing> list(UUID hostId, String root, ListOptions options, Optional<WindowHandle> signIn);

    /**
     * Opens a remote file in the Editor; may connect and ask the user to sign in. Reports failures itself; does nothing
     * for a host that is no longer saved.
     *
     * @param hostId a saved host
     * @param path the file's absolute path
     * @param window the window the user acted in
     */
    void open(UUID hostId, String path, WindowHandle window);

    /**
     * Opens an SSH tab on the host whose shell starts in {@code folder}; may connect and ask the user to sign in. Does
     * nothing for a host that is no longer saved.
     *
     * @param hostId a saved host
     * @param folder an absolute folder
     * @param window the window to open the tab in
     */
    void openTerminal(UUID hostId, String folder, WindowHandle window);
}
