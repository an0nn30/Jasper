package dev.jasper.editor.vim;

/** What one command runs with: the editor, its count, register and argument, and the operator waiting for it. */
final class Context {
    final VimEditor editor;
    final Buffer buffer;
    final Options options;
    final Registers registers;
    final Vim vim;
    /** The count, 1 when none was typed; after an operator, the product of both counts. */
    int count = 1;
    boolean hasCount;
    /** The register typed with {@code "x}, or 0. */
    char register;
    /** The key read as the command's argument ({@code f}'s character), or null. */
    KeyInput argument;
    /** The pending operator's keys ({@code d}, {@code gU}), or null in Normal mode. */
    String operator;
    /** The forced motion type after the operator: {@code v}, {@code V}, or 0. */
    char forced;
    /** In Visual mode, the other end of the area (Vim's VIsual); null otherwise. */
    Pos visual;
    /** The command line typed for {@code /} and {@code ?}, or null. */
    String line;
    /** For an operator, where the cursor was when it started (Vim's oap->cursor_start, for {@code gw}). */
    Pos cursorStart;

    Context(VimEditor editor) {
        this.editor = editor;
        this.buffer = editor.buffer();
        this.options = editor.options();
        this.registers = editor.vim().registers();
        this.vim = editor.vim();
    }

    /** @return a copy of the cursor position */
    Pos cursor() { return editor.cursor(); }

    Walk walk(boolean bigword) {
        var walk = new Walk(buffer, options.keywords());
        walk.bigword = bigword;
        return walk;
    }

    /** @return the argument's character, or -1 when the argument is a named key or has modifiers */
    int argumentChar() { return argument == null ? -1 : argument.plainChar(); }

    int tabstop() { return options.number("tabstop"); }

    /** @return whether Visual mode is active, where the cursor may sit on the end of a line (Vim's past_line) */
    boolean inVisual() { return visual != null; }

    static VimFailure fail() { return VimFailure.bell(); }
}
