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
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE);

    private Capabilities() { }
}
