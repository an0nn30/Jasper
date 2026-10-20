package dev.jasper.terminal.internal.text;

/**
 * The file a click names, before the view reports it as a supported {@code FileLink}: a path as written, or the decoded
 * path of a {@code file} URI on this machine.
 *
 * @param path the path
 * @param line the 1-based line written after the path, or 0
 * @param column the 1-based column written after the line, or 0
 */
public record FileTarget(String path, int line, int column) { }
