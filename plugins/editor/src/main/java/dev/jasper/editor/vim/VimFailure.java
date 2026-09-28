package dev.jasper.editor.vim;

/**
 * A Vim command failed (spec 2.3): the engine rings the visual bell, shows the message if there is one, drops the
 * pending command and stops a running repeat. Thrown from anywhere inside a command; it carries no stack trace.
 */
final class VimFailure extends RuntimeException {
    /** A failure Vim gives no sign of: no message and no bell (an Ex address that stops short). */
    final boolean quiet;

    VimFailure(String message) { this(message, false); }

    private VimFailure(String message, boolean quiet) {
        super(message, null, false, false);
        this.quiet = quiet;
    }

    static VimFailure bell() { return new VimFailure(""); }

    static VimFailure quiet() { return new VimFailure("", true); }
}
