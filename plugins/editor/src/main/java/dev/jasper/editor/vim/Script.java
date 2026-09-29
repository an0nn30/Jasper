package dev.jasper.editor.vim;

import java.util.List;

/**
 * What the emulated extensions (spec 8) use in place of the Vim script they are ported from: {@code :normal!},
 * {@code getreg()}, {@code getregtype()} and {@code setreg()}, and the marks an operator function finds. The ports
 * follow their plugins' scripts step by step on Jasper's own commands, so they behave as the plugins do in Vim.
 */
final class Script {
    private Script() { }

    /**
     * {@code :normal! {keys}} inside a command: the keys run unmapped, and whatever waits for a key at the end gets
     * {@code <Esc>}. A failing key rings the bell and ends the keys, as in Vim; the command goes on.
     *
     * @param keys Vim notation, {@code <lt>} for {@code <}
     * @return whether no key failed
     */
    static boolean normal(VimEditor editor, String keys) { return normal(editor, KeyInput.parse(keys)); }

    static boolean normal(VimEditor editor, List<KeyInput> keys) {
        boolean[] ok = { true };
        editor.machine().nested(() -> {
            VimFailure failed = editor.typeahead().execute(keys, false);
            if (failed != null) {
                ok[0] = false;
                if (!failed.quiet) editor.bell();
                if (!editor.mode().inserting()) editor.machine().reset();
            }
            ExLines.endPending(editor);
        });
        return ok[0];
    }

    /** @return getreg(): the register's text, a linewise one ending with a line break; empty when it is empty */
    static String getreg(VimEditor editor, char name) {
        return editor.vim().registers().get(name).map(Registers.Content::text).orElse("");
    }

    /** @return getregtype(): {@code v}, {@code V}, or CTRL-V and the width; empty for an empty register */
    static String regtype(VimEditor editor, char name) {
        return editor.vim().registers().get(name).map(content -> switch (content.type()) {
            case CHAR -> "v";
            case LINE -> "V";
            case BLOCK -> "\u0016" + (content.width() + 1);
        }).orElse("");
    }

    /**
     * setreg(): sets a register's text; {@code type} is {@code v}, {@code V} or CTRL-V (a block as wide as its
     * widest line), and an empty type is charwise, or linewise for text ending with a line break.
     */
    static void setreg(VimEditor editor, char name, String text, String type) {
        Registers.Type kind = type.startsWith("V") ? Registers.Type.LINE : type.startsWith("\u0016") ? Registers.Type.BLOCK
            : type.isEmpty() && text.endsWith("\n") ? Registers.Type.LINE : Registers.Type.CHAR;
        Registers.Content content = Registers.Content.of(text, kind);
        if (kind == Registers.Type.BLOCK) {
            int ts = editor.options().number("tabstop");
            int width = 0;
            for (String line : content.lines()) width = Math.max(width, Text.virtcol(line, line.length(), ts));
            content = new Registers.Content(content.lines(), kind, Math.max(0, width - 1));
        }
        editor.vim().registers().set(name, content);
    }

    /**
     * op_function: the operator's area in the {@code '[} and {@code ']} marks, the end moved back a character for an
     * exclusive motion.
     *
     * @return the type an operator function gets: {@code line}, {@code block} or {@code char}
     */
    static String operatorMarks(Context c, Span span) {
        Marks marks = c.editor.marks();
        Pos start = span.start().copy(), end = span.end().copy();
        if (!span.linewise() && !span.blockwise() && span.kind() == Target.Kind.EXCLUSIVE) Search.decl(c.buffer, end);
        marks.set('[', start);
        marks.set(']', end);
        return span.blockwise() ? "block" : span.linewise() ? "line" : "char";
    }

    /** @return v:register: the register typed, or {@code "} (the clipboard's with 'clipboard' holding unnamed) */
    static char register(Context c) {
        if (c.register != 0) return c.register;
        char effective = Registers.effective((char) 0, c.options.string("clipboard"));
        return effective == 0 ? '"' : effective;
    }
}
