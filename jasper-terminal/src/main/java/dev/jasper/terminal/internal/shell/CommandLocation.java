package dev.jasper.terminal.internal.shell;

/** Shell command tracking value.
 * @param row absolute cursor row
 * @param column zero-based cursor cell
 */
public record CommandLocation(long row, int column) { }
