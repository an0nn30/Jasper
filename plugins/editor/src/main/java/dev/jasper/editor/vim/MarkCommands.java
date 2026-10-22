package dev.jasper.editor.vim;

/**
 * Marks, the jumplist and the change list (spec 3.6), ported from Vim's normal.c (nv_mark, nv_gomark,
 * nv_cursormark, nv_pcmark, {@code gi}) and mark.c (setmark_pos, getmark_buf_fnum, check_mark).
 */
final class MarkCommands {
    private MarkCommands() { }

    static void register(VimCommands commands) {
        commands.actionWithChar("m", "set mark {char} at the cursor", false, MarkCommands::set);
        commands.motionWithChar("'", "to the first non-blank of mark {char}'s line", c -> go(c, true));
        commands.motionWithChar("`", "to mark {char}", c -> go(c, false));
        commands.motionWithChar("g'", "to the first non-blank of mark {char}'s line, leaving the jumplist alone", c -> go(c, true, false));
        commands.motionWithChar("g`", "to mark {char}, leaving the jumplist alone", c -> go(c, false, false));
        commands.action("<C-o>", "to an older position in the jumplist", false, c -> jump(c, -c.count));
        // CTRL-I arrives as <Tab>, as in Vim (KeyInput.normalized).
        commands.action("<Tab>", "to a newer position in the jumplist (CTRL-I)", false, c -> jump(c, c.count));
        commands.action("g;", "to an older position in the change list", false, c -> change(c, -c.count));
        commands.action("g,", "to a newer position in the change list", false, c -> change(c, c.count));
        commands.action("gi", "Insert mode where it was last left (the '^ mark)", true, MarkCommands::insertAgain);
        commands.visual("m", "set mark {char} at the cursor", true, false, MarkCommands::set);
    }

    /** nv_mark. */
    static void set(Context c) {
        int name = c.argumentChar();
        if (name < 0 || !setmark(c.editor, (char) name, c.cursor())) throw Context.fail();
    }

    /**
     * setmark_pos with the cursor at {@code pos}, for {@code m} and {@code :mark}.
     *
     * @return false for a name that is not a mark
     */
    static boolean setmark(VimEditor editor, char name, Pos pos) {
        Marks marks = editor.marks();
        if (name == '\'' || name == '`') marks.setpcmarkKeep(pos);
        else if (name >= 'a' && name <= 'z' || name == '[' || name == ']' || name == '"') marks.set(name, pos);
        else if (name == '<' || name == '>') {
            marks.set(name == '<' ? Visual.START : Visual.END, pos);
            if (marks.visualMode() == null) marks.visualMode(Mode.VISUAL);
        } else if (name >= 'A' && name <= 'Z' || name >= '0' && name <= '9') editor.vim().globalMark(name, editor, pos);
        else return false;
        return true;
    }

    /**
     * getmark_buf_fnum.
     *
     * @return the mark's position, which may be past the end of its line or the text; null when it is not set
     */
    static Pos get(VimEditor editor, char name) {
        Marks marks = editor.marks();
        return switch (name) {
            case '\'', '`' -> marks.pcmark();
            case '<', '>' -> {
                Pos start = marks.get(Visual.START), end = marks.get(Visual.END);
                Pos pos;
                if (start != null && ((name == '<') == (end == null || start.compareTo(end) < 0))) pos = start;
                else pos = end;
                if (pos != null && marks.visualMode() == Mode.VISUAL_LINE) pos = new Pos(pos.line, name == '<' ? 0 : Visual.MAX);
                yield pos;
            }
            default -> {
                if (name >= 'A' && name <= 'Z' || name >= '0' && name <= '9') yield editor.vim().globalMark(name, editor);
                yield marks.get(name);
            }
        };
    }

    /** nv_gomark: ' is linewise to the first non-blank, ` exclusive to the mark itself; both are jumps. */
    static Target go(Context c, boolean linewise) { return go(c, linewise, true); }

    /** nv_gomark: with {@code jump} false ({@code g'}, {@code g`}) the jumplist is left alone. */
    static Target go(Context c, boolean linewise, boolean jump) {
        int name = c.argumentChar();
        if (name < 0) throw Context.fail();
        if (name >= 'A' && name <= 'Z' || name >= '0' && name <= '9') {
            Vim.GlobalMark mark = c.vim.globalMarks().get((char) name);
            if (mark != null && mark.editor() != c.editor) {
                elsewhere(c, mark, linewise);
                throw VimFailure.quiet();
            }
        }
        Pos pos = get(c.editor, (char) name);
        pos = check(c.buffer, pos);
        if (linewise) {
            String line = c.buffer.line(pos.line);
            Target target = Target.linewise(new Pos(pos.line, Text.firstNonBlank(line)), Target.Column.RESET);
            return jump ? target.asJump() : target;
        }
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (!c.inVisual() && col >= line.length() && !line.isEmpty()) col = Text.last(line);
        Target target = Target.exclusive(new Pos(pos.line, col)).usingRegisterOne().withoutAdjust();
        return jump ? target.asJump() : target;
    }

    /**
     * An uppercase mark in another tab (spec 3.6): the view it was set in is selected and its cursor goes to the mark. An
     * operator or Visual mode cannot reach into another tab, so there the command fails.
     */
    private static void elsewhere(Context c, Vim.GlobalMark mark, boolean linewise) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        VimEditor other = mark.editor();
        var buffers = c.vim.host().documents().buffers().apply(other);
        var buffer = buffers.stream().filter(VimHost.Buffer::current).findFirst().orElseThrow(() -> new VimFailure("E20: Mark not set"));
        Pos pos = check(other.buffer(), mark.pos());
        String line = other.buffer().line(pos.line);
        int col = linewise ? Text.firstNonBlank(line) : Math.min(pos.col, line.length());
        if (col >= line.length() && !line.isEmpty()) col = Text.last(line);
        other.marks().setpcmark(other.cursor());
        other.setCursor(new Pos(pos.line, col));
        other.forgetColumn();
        c.vim.host().documents().show().accept(other);
    }

    /** check_mark: E20 for a mark that is not set, E19 for one past the last line. */
    static Pos check(Buffer b, Pos pos) {
        if (pos == null || pos.line < 0) throw new VimFailure("E20: Mark not set");
        if (pos.line >= b.count()) throw new VimFailure("E19: Mark has invalid line number");
        return pos;
    }

    /** nv_pcmark for CTRL-O and CTRL-I; not with an operator or in Visual mode. */
    static void jump(Context c, int count) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        Pos pos = c.editor.marks().movemark(count, c.cursor());
        if (pos == null) throw Context.fail();
        moveTo(c, check(c.buffer, pos));
    }

    /** nv_pcmark for g; and g,. */
    static void change(Context c, int count) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        Marks marks = c.editor.marks();
        Pos pos = marks.movechangelist(count);
        if (pos == null) {
            if (marks.changes().isEmpty()) throw new VimFailure("E664: Changelist is empty");
            throw new VimFailure(count < 0 ? "E662: At start of changelist" : "E663: At end of changelist");
        }
        moveTo(c, check(c.buffer, pos));
    }

    private static void moveTo(Context c, Pos pos) {
        Pos before = c.cursor();
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (col >= line.length() && !line.isEmpty()) col = Text.last(line);
        c.editor.setCursor(new Pos(pos.line, col));
        c.editor.forgetColumn();
        c.editor.machine().foldOpenAfter("mark", before);
    }

    /** gi: Insert mode at the {@code '^} mark, or at the cursor when it is not set. */
    static void insertAgain(Context c) {
        Pos last = c.editor.marks().get('^');
        if (last != null) {
            int line = Math.min(last.line, c.buffer.count() - 1);
            int col = Math.min(last.col, c.buffer.line(line).length());
            c.editor.setCursor(new Pos(line, col), true);
        }
        c.editor.insert().begin("i", c.count, false, -1);
    }
}
