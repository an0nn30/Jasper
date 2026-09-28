package dev.jasper.editor.vim;

/**
 * A Vim command failed (spec 2.3): the engine rings the visual bell, shows the message if there is one, drops the
 * pending command and stops a running repeat. Thrown from anywhere inside a command; it carries no stack trace.
 */
final class VimFailure extends RuntimeException {
    VimFailure(String message) { super(message, null, false, false); }

    static VimFailure bell() { return new VimFailure(""); }
}
