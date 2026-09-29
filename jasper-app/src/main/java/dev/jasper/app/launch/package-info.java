/**
 * Immutable launch capture, shell profiles and shell integration extraction; ShellLauncher starts off EDT and delivers on EDT. Receiving pane owns the session.
 * ShellProfileCatalog is EDT-confined, detects WSL distributions and PowerShell 7 on a background thread, and is owned and closed by the application.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.config, dev.jasper.app.lifecycle, dev.jasper.terminal.session.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.launch;
