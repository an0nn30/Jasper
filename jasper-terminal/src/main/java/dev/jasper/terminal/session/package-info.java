/**
 * Supported session lifecycle, explicit launch settings, and event listeners. The session owns its child process; callers close it when its pane is disposed.
 * <p>Depends on config and internal composition/process/emulation, never view or app. TerminalSessionListener callbacks run on their event source thread; process startup belongs off EDT.
 */
package dev.jasper.terminal.session;
