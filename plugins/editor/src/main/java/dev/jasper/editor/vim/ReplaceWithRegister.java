package dev.jasper.editor.vim;

/**
 * The ReplaceWithRegister extension (spec 8), ported from Ingo Karkat's ReplaceWithRegister.vim 1.43:
 * {@code ["x]gr{motion}}, {@code grr} and Visual {@code gr} replace text with a register's, keeping the register and
 * the unnamed register as they were. As the plugin does, the replacement is a Visual-mode put over the text, with a
 * linewise register put charwise over a characterwise area.
 */
final class ReplaceWithRegister {
    private ReplaceWithRegister() { }

    private static final String OPTION = "replacewithregister";

    static void register(VimCommands commands) {
        commands.operator("<Plug>ReplaceWithRegisterOperator", "replace {motion}'s text with the register (`gr`, with `set ReplaceWithRegister`)",
            ReplaceWithRegister::operator);
        commands.action("<Plug>ReplaceWithRegisterLine", "replace [count] lines with the register (`grr`)", true, ReplaceWithRegister::line);
        commands.visual("<Plug>ReplaceWithRegisterVisual", "replace the Visual area with the register (`gr`)", false, true,
            ReplaceWithRegister::visual);
    }

    /** {@code gr{motion}}: g@ and ReplaceWithRegister#Operator for the motion's type. */
    private static void operator(Context c, Span span) {
        Extensions.require(c, OPTION);
        String type = Script.operatorMarks(c, span);
        replace(c, Script.register(c), type);
    }

    /** {@code grr}: {@code V{count}_<Esc>}, then the operator on the Visual area. */
    private static void line(Context c) {
        Extensions.require(c, OPTION);
        char register = Script.register(c);
        Script.normal(c.editor, "V" + c.count + "_<Esc>");
        replace(c, register, "visual");
    }

    /** Visual {@code gr}: the area as {@code :<C-u>} leaves it (the Visual marks), then the operator on it. */
    private static void visual(Context c) {
        Extensions.require(c, OPTION);
        char register = Script.register(c);
        Visual.end(c.editor);
        replace(c, register, "visual");
    }

    /** ReplaceWithRegister#Operator: the register, corrected for the area's type while it is put, then restored. */
    private static void replace(Context c, char register, String type) {
        VimEditor editor = c.editor;
        String pasteText = Script.getreg(editor, register), regType = Script.regtype(editor, register);
        boolean corrected = correctForRegtype(editor, type, register, regType, pasteText);
        try {
            put(c, register, type);
        } finally {
            if (corrected) Script.setreg(editor, register, pasteText, regType);
        }
    }

    /**
     * s:CorrectForRegtype: over a block, a single line is repeated for each of its lines and several lines are put
     * blockwise; over anything else a linewise register loses its last line break and is put charwise.
     *
     * @return whether the register was changed and must be put back
     */
    private static boolean correctForRegtype(VimEditor editor, String type, char register, String regType, String pasteText) {
        boolean block = type.equals("visual") ? editor.marks().visualMode == Mode.VISUAL_BLOCK : type.equals("block");
        if (block) {
            int lines = pasteText.isEmpty() ? 0 : pasteText.split("\n", -1).length - (pasteText.endsWith("\n") ? 1 : 0);
            if (regType.equals("v") || regType.equals("V") && lines == 1) {
                int height = Math.abs(MarkCommands.get(editor, '>').line - MarkCommands.get(editor, '<').line) + 1;
                if (height > 1) {
                    String line = pasteText.endsWith("\n") ? pasteText.substring(0, pasteText.length() - 1) : pasteText;
                    String[] parts = line.split("\n", -1);
                    StringBuilder repeated = new StringBuilder();
                    for (int i = 0; i < height; i++) {
                        if (i > 0) repeated.append('\n');
                        repeated.append(String.join("\n", parts));
                    }
                    Script.setreg(editor, register, repeated.toString(), "\u0016");
                    return true;
                }
            } else if (regType.equals("V") && lines > 1) {
                String text = pasteText.endsWith("\n") ? pasteText.substring(0, pasteText.length() - 1) : pasteText;
                Script.setreg(editor, register, text, "\u0016");
                return true;
            }
        } else if (regType.equals("V") && pasteText.endsWith("\n")) {
            Script.setreg(editor, register, pasteText.substring(0, pasteText.length() - 1), "v");
            return true;
        }
        return false;
    }

    /**
     * s:ReplaceWithRegister: a Visual put over the area ({@code gv"xp}, or {@code g`[vg`]"xp} for a motion; before the
     * cursor with {@code P} when the motion covered nothing), with 'clipboard' empty and the unnamed register put back.
     */
    private static void put(Context c, char register, String type) {
        VimEditor editor = c.editor;
        Options options = editor.options();
        String clipboard = options.string("clipboard");
        options.set("clipboard", "");
        String saved = Script.getreg(editor, '"'), savedType = Script.regtype(editor, '"');
        String paste = register == '"' ? "" : "\"" + register;
        try {
            if (type.equals("visual")) Script.normal(editor, "gv" + paste + "p");
            else {
                Pos start = editor.marks().get('['), end = editor.marks().get(']');
                if (start.compareTo(end) > 0) Script.normal(editor, paste + "P");
                else Script.normal(editor, "g`[" + (type.equals("line") ? "V" : "v") + "g`]" + paste + "p");
            }
        } finally {
            if (!savedType.isEmpty()) Script.setreg(editor, '"', saved, savedType);
            options.set("clipboard", clipboard);
        }
    }
}
