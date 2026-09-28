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

    static VimFailure fail() { return VimFailure.bell(); }
}
