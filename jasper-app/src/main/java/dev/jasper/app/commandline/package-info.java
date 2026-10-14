/**
 * The {@code jasper} command's side of Jasper: the command socket and its wire format (shared with the native client
 * in {@code packaging/cli}), the calls it dispatches, the subcommand list {@code jasper --help} reads, and putting
 * the command on {@code PATH}. Bootstrap binds the listener after plugins start; the application closes it at
 * shutdown and the process hook is its backup.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.commandline;
