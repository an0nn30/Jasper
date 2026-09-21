/**
 * Immutable launch capture and shell integration extraction; ShellLauncher starts off EDT and delivers on EDT. Receiving pane owns the session.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.config, dev.jasper.terminal.session.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.launch;
