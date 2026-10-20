package dev.jasper.terminal.view;

import java.util.Objects;

/**
 * A file that a Cmd-click (Ctrl-click on Linux and Windows) named in terminal output, reported to
 * {@link TerminalView#setFileLinkHandler}. The view resolves nothing: what the path means, and whether the file exists
 * and opens, is the application's decision. The path is unauthenticated program output: an OSC 8 link's target can
 * differ from the text shown.
 *
 * @param path the path as written ({@code ~/}, {@code ./} and relative paths unresolved), or the decoded absolute path
 *             of a {@code file:} link to this machine
 * @param line the 1-based line written after the path, or 0 when there is none
 * @param column the 1-based column written after the line, or 0 when there is none
 */
public record FileLink(String path, int line, int column) {
    /**
     * Validates the components.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws IllegalArgumentException when {@code path} is blank, or {@code line} or {@code column} is negative
     */
    public FileLink {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) throw new IllegalArgumentException("A file link needs a path");
        if (line < 0 || column < 0) throw new IllegalArgumentException("Line and column are 1-based, or 0 for none");
    }
}
