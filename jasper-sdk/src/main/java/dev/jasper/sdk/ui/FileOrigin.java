package dev.jasper.sdk.ui;

/**
 * Where an open of a file came from, which a {@link FileEditor} can tell apart through
 * {@link FileEditor#edit(java.nio.file.Path, java.util.OptionalInt, java.util.Optional, FileOrigin)}.
 *
 * @since 0.31.0
 */
public enum FileOrigin {
    /**
     * An open Jasper or a plugin asked for: {@code Platform.openInEditor}, {@code Platform.openFile}, a Cmd-click,
     * File &rsaquo; Open File&hellip;, a settings file.
     */
    JASPER,
    /**
     * A file another program handed to Jasper: the system's open-file event, a launch's files, a second launch, the
     * {@code jasper} command.
     */
    OUTSIDE
}
