/**
 * The {@code jasper} command's side of Jasper: the command socket and its wire format (shared with the native client
 * in {@code packaging/cli}), the calls it dispatches, the subcommand list {@code jasper --help} reads, and putting
 * the command on {@code PATH}. The home's owner binds the listener before AWT and holds its calls until the
 * application is composed; the application closes it at shutdown and the process hook is its backup.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.commandline;
