package dev.jasper.sdk;

import java.util.List;

/**
 * The capability names a plugin declares in {@code plugin.toml} and the user consents to. They cover
 * effects the user cannot see; contributions to the window need none. They are not a sandbox.
 */
public final class Capabilities {
    /** Pane metadata, working directories, titles, command text, and every {@code jasper.terminal.*} topic. */
    public static final String TERMINAL_OBSERVE = "terminal.observe";
    /** Reading the text selected in a pane. */
    public static final String TERMINAL_SELECTION = "terminal.selection";
    /** Typing and pasting into a pane. */
    public static final String TERMINAL_INJECT = "terminal.inject";
    /** Opening local tabs and splits. */
    public static final String TERMINAL_OPEN = "terminal.open";
    /** Providing a pane's session, such as a remote connection. */
    public static final String SESSION_PROVIDE = "session.provide";
    /** Contribute command-palette scopes and open the palette. */
    public static final String PALETTE_CONTRIBUTE = "palette.contribute";
    /** Add places, such as saved hosts, to Jasper's file chooser. @since 0.10.0 */
    public static final String FILES_SOURCE = "files.source";
    /**
     * Accepting connections from other programs or computers, such as serving files. Nothing gates it: the user
     * consents to it when the plugin is installed. @since 0.11.0
     */
    public static final String NETWORK_LISTEN = "network.listen";
    /**
     * Reading the text on the clipboard with {@code Platform.clipboardText()}; the clipboard often holds something the
     * user just copied elsewhere, such as a password. Writing needs no capability. @since 0.16.1
     */
    public static final String CLIPBOARD_READ = "clipboard.read";
    /**
     * Adding variables, such as {@code EDITOR}, to the environment of new local shells with
     * {@code Terminals.environment}. @since 0.17.1
     */
    public static final String TERMINAL_ENVIRONMENT = "terminal.environment";
    /** Being the editor Jasper opens files with, through {@code Platform.editor}. @since 0.17.1 */
    public static final String EDITOR_PROVIDE = "editor.provide";
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE, PALETTE_CONTRIBUTE, FILES_SOURCE, NETWORK_LISTEN, CLIPBOARD_READ,
        TERMINAL_ENVIRONMENT, EDITOR_PROVIDE);

    private Capabilities() { }
}
