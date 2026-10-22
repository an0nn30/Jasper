package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Target.Kind;

/**
 * Visual mode (spec 4.1), ported from Vim's normal.c (nv_visual, v_visop, v_swap_corners, nv_put_opt, the
 * {@code gv} command) and ops.c's do_pending_operator, which turns the Visual area into an operator's span:
 * charwise areas include the character under the cursor and, when the cursor is on the end of a line, the line
 * break; linewise areas are whole lines; block areas are display columns.
 */
final class Visual {
    /** Vim's MAXCOL for the remembered column: {@code $} was used. */
    static final int MAX = Integer.MAX_VALUE;
    /** Mark names for the last Visual area's two ends (Vim's b_visual); not user marks. */
    static final char START = '\u0001', END = '\u0002';

    /**
     * The size of a Visual area, so {@code .} can repeat an operator on the same size (Vim's redo_VIsual).
     *
     * @param mode the Visual mode
     * @param lines the number of lines
     * @param vcol the width in display columns, the last column for multi-line charwise areas, or {@link #MAX}
     * @param count the count typed with the operator, or 0
     */
    record Redo(Mode mode, int lines, int vcol, int count) { }

    private Visual() { }

    static void register(VimCommands commands) {
        commands.action("v", "start charwise Visual mode", false, c -> start(c, Mode.VISUAL));
        commands.action("V", "start linewise Visual mode", false, c -> start(c, Mode.VISUAL_LINE));
        commands.action("<C-v>", "start blockwise Visual mode", false, c -> start(c, Mode.VISUAL_BLOCK));
        commands.action("gv", "select the last Visual area again", false, Visual::reselect);
        commands.visual("v", "charwise Visual mode, or back to Normal mode", false, false, c -> switchTo(c, Mode.VISUAL));
        commands.visual("V", "linewise Visual mode, or back to Normal mode", false, false, c -> switchTo(c, Mode.VISUAL_LINE));
        commands.visual("<C-v>", "blockwise Visual mode, or back to Normal mode", false, false, c -> switchTo(c, Mode.VISUAL_BLOCK));
        commands.visual("o", "go to the other end of the area", false, false, c -> swap(c, false));
        commands.visual("O", "go to the other corner of the block (the other end otherwise)", false, false, c -> swap(c, true));
        commands.visual("gv", "swap with the last Visual area", false, false, Visual::reselect);
        commands.visual("x", "delete", false, true, c -> operate(c, "d", false));
        commands.visual("<Del>", "delete", false, true, c -> operate(c, "d", false));
        commands.visual("s", "change", false, true, c -> operate(c, "c", false));
        commands.visual("X", "delete the lines (in a block, the block)", false, true, c -> operate(c, "d", true));
        commands.visual("D", "delete the lines; in a block, to the end of each line", false, true, c -> operate(c, "D", true));
        commands.visual("Y", "yank the lines (in a block, the block)", false, false, c -> operate(c, "y", true));
        commands.visual("C", "change the lines; in a block, to the end of each line", false, true, c -> operate(c, "C", true));
        commands.visual("S", "change the lines (in a block, the block)", false, true, c -> operate(c, "c", true));
        commands.visual("R", "change the lines (in a block, the block)", false, true, c -> operate(c, "c", true));
        commands.visual("u", "lower case", false, true, c -> operate(c, "gu", false));
        commands.visual("U", "upper case", false, true, c -> operate(c, "gU", false));
        commands.visual("~", "toggle case", false, true, c -> operate(c, "g~", false));
        commands.visual("J", "join the lines, with spaces", false, true, c -> operate(c, "J", false));
        commands.visual("gJ", "join the lines, without spaces", false, true, c -> operate(c, "gJ", false));
        commands.visual("r", "replace every character with {char}", true, true, c -> operate(c, "r", false));
        commands.visual("p", "replace with the register", false, true, c -> put(c, false));
        commands.visual("P", "replace with the register, keeping the registers", false, true, c -> put(c, true));
        commands.visual("I", "insert before the area (in a block, on every line)", false, true, c -> operate(c, "I", true));
        commands.visual("A", "append after the area (in a block, on every line)", false, true, c -> operate(c, "A", true));
    }

    /** v, V, CTRL-V in Normal mode: start Visual mode at the cursor (n_start_visual_mode). */
    static void start(Context c, Mode mode) {
        VimEditor editor = c.editor;
        editor.visualAnchor(editor.cursor());
        editor.mode(mode);
        if (c.hasCount && c.count > 1) {
            // With a count, select that many characters or lines (nv_visual).
            Context move = new Context(editor);
            move.count = c.count - 1;
            move.visual = editor.visualAnchor();
            Target target = mode == Mode.VISUAL_LINE ? Motions.down(move, move.count) : Motions.right(move, 'l');
            if (target != null) editor.setCursor(target.pos(), true);
        }
    }

    /** v, V, CTRL-V in Visual mode: the same mode ends Visual mode, another switches to it. */
    static void switchTo(Context c, Mode mode) {
        if (c.editor.mode() == mode) end(c.editor);
        else c.editor.mode(mode);
    }

    /** end_visual_mode: remembers the area for gv and '< '>, and keeps the cursor off the end of the line. */
    static void end(VimEditor editor) {
        remember(editor, editor.mode());
        editor.visualAnchor(null);
        editor.mode(Mode.NORMAL);
        editor.setCursor(editor.cursor());
    }

    static void remember(VimEditor editor, Mode mode) {
        Marks marks = editor.marks();
        marks.set(START, editor.visualAnchor());
        marks.set(END, editor.cursor());
        marks.visualMode(mode);
        marks.visualCurswant(editor.curswant());
    }

    /** v_swap_corners. */
    static void swap(Context c, boolean corner) {
        VimEditor editor = c.editor;
        Pos anchor = editor.visualAnchor(), cursor = editor.cursor();
        if (corner && editor.mode() == Mode.VISUAL_BLOCK) {
            int ts = c.tabstop();
            int left = Math.min(startVcol(c.buffer, anchor, ts), startVcol(c.buffer, cursor, ts));
            int right = Math.max(endVcol(c.buffer, anchor, ts), endVcol(c.buffer, cursor, ts));
            Pos newAnchor = new Pos(anchor.line, coladvance(c.buffer, anchor.line, left, ts));
            Pos newCursor = new Pos(cursor.line, coladvance(c.buffer, cursor.line, right, ts));
            if (newCursor.col == cursor.col) {
                newAnchor = new Pos(anchor.line, coladvance(c.buffer, anchor.line, right, ts));
                newCursor = new Pos(cursor.line, coladvance(c.buffer, cursor.line, left, ts));
                editor.curswant(left);
            } else editor.curswant(right);
            editor.visualAnchor(newAnchor);
            editor.setCursor(newCursor, true);
            return;
        }
        editor.visualAnchor(cursor);
        editor.setCursor(anchor, true);
        editor.forgetColumn();
    }

    /** gv: select the last Visual area again; in Visual mode, swap the current area with it. */
    static void reselect(Context c) {
        VimEditor editor = c.editor;
        Marks marks = editor.marks();
        Pos start = marks.get(START), end = marks.get(END);
        if (start == null || end == null || marks.visualMode() == null) throw Context.fail();
        Mode mode = marks.visualMode();
        int curswant = marks.visualCurswant();
        if (editor.mode().visual()) remember(editor, editor.mode());
        editor.visualAnchor(clamp(c.buffer, start));
        editor.mode(mode);
        editor.setCursor(clamp(c.buffer, end), true);
        editor.curswant(curswant);
    }

    private static Pos clamp(Buffer b, Pos pos) {
        int line = Math.min(pos.line, b.count() - 1);
        return new Pos(line, Math.min(pos.col, b.line(line).length()));
    }

    /**
     * An operator on the Visual area: v_visop's uppercase variants ({@code linewise}: X, D, Y, C, S, R, I, A become
     * linewise, except in a block, where D and C go to each line's end), then do_pending_operator.
     */
    static void operate(Context c, String operator, boolean linewise) {
        VimEditor editor = c.editor;
        Mode original = editor.mode();
        Mode mode = original;
        // D and C: to the end of each line of a block; otherwise d and c.
        boolean toEnd = operator.equals("D") || operator.equals("C");
        if (toEnd) operator = operator.toLowerCase(java.util.Locale.ROOT);
        if (linewise) {
            if (mode != Mode.VISUAL_BLOCK) mode = Mode.VISUAL_LINE;
            else if (toEnd) editor.curswant(MAX);
        }
        editor.mode(mode);
        editor.machine().applyVisual(c, operator, range(c, mode, operator), original);
    }

    /** What an operator on the Visual area acts on, and the area's size for {@code .}. */
    record Range(Span span, Redo redo) { }

    static Range range(Context c, Mode mode, String operator) {
        Buffer b = c.buffer;
        int ts = c.tabstop();
        VimEditor editor = c.editor;
        Pos anchor = editor.visualAnchor(), cursor = editor.cursor();
        Pos start = anchor.copy();
        if (mode == Mode.VISUAL_LINE) start.col = 0;
        Pos end;
        if (start.compareTo(cursor) < 0) end = cursor.copy();
        else {
            end = start;
            start = cursor.copy();
        }
        int lines = end.line - start.line + 1;
        boolean max = editor.curswant() == MAX;
        Span.Columns block = null;
        if (mode == Mode.VISUAL_BLOCK) {
            int startVcol = startVcol(b, start, ts), endVcol = endVcol(b, start, ts);
            int s = startVcol(b, end, ts), e = endVcol(b, end, ts);
            if (s < startVcol) startVcol = s;
            if (e > endVcol) endVcol = e;
            if (max) {
                endVcol = 0;
                for (int line = start.line; line <= end.line; line++) endVcol = Math.max(endVcol, Text.virtcol(b.line(line), b.line(line).length(), ts));
            }
            end = new Pos(end.line, coladvance(b, end.line, endVcol, ts));
            start = new Pos(start.line, coladvance(b, start.line, startVcol, ts));
            block = new Span.Columns(startVcol, endVcol, max);
        }
        int vcol;
        if (max) vcol = MAX;
        else if (mode == Mode.VISUAL_BLOCK) vcol = block.endVcol() - block.startVcol() + 1;
        else {
            int endV = endVcol(b, end, ts);
            vcol = lines <= 1 ? endV - startVcol(b, start, ts) + 1 : endV;
        }
        Redo redo = new Redo(mode, lines, vcol, c.hasCount ? c.count : 0);
        Kind kind;
        if (mode == Mode.VISUAL_LINE) kind = Kind.LINEWISE;
        else {
            kind = Kind.INCLUSIVE;
            if (mode != Mode.VISUAL_BLOCK && end.col >= b.line(end.line).length()) {
                kind = Kind.EXCLUSIVE;
                if (!onLines(operator) && end.line < b.count() - 1) end = new Pos(end.line + 1, 0);
            }
        }
        return new Range(new Span(start, end, kind, false, block, true), redo);
    }

    /** op_on_lines: operators that work on whole lines, so a charwise area never takes the line break for them. */
    private static boolean onLines(String operator) {
        return switch (operator) {
            case "<lt>", ">", "=", "J", "gJ", "gq", "gw" -> true;
            default -> false;
        };
    }

    /** redo_VIsual_busy: {@code .} makes an area of the remembered size at the cursor. */
    static void reselectForRedo(VimEditor editor, Redo redo) {
        Buffer b = editor.buffer();
        int ts = editor.options().number("tabstop");
        Pos start = editor.cursor();
        editor.visualAnchor(start);
        editor.mode(redo.mode());
        Pos cursor = new Pos(Math.min(start.line + redo.lines() - 1, b.count() - 1), start.col);
        if (redo.vcol() == MAX) {
            editor.curswant(MAX);
            cursor.col = b.line(cursor.line).length();
        } else if (redo.mode() == Mode.VISUAL) {
            int want = redo.lines() <= 1 ? Text.virtcol(b.line(start.line), start.col, ts) + redo.vcol() - 1 : redo.vcol();
            editor.curswant(want);
            cursor.col = coladvance(b, cursor.line, want, ts);
        } else if (redo.mode() == Mode.VISUAL_BLOCK) {
            int want = startVcol(b, start, ts) + redo.vcol() - 1;
            editor.curswant(want);
            cursor.col = coladvance(b, cursor.line, want, ts);
        }
        editor.setCursor(cursor, true);
    }

    /** nv_put_opt in Visual mode: the register's text replaces the area. */
    static void put(Context c, boolean keepRegisters) {
        VimEditor editor = c.editor;
        Mode mode = editor.mode();
        char regname = Registers.effective(c.register, c.options.string("clipboard"));
        boolean saving = regname == 0 || regname == '"' || Character.isDigit(regname) || regname == '-'
            || (regname == '*' || regname == '+') && !c.options.string("clipboard").isEmpty();
        char name = regname == 0 ? '"' : regname;
        // The delete may overwrite the register being put: its content is saved first, put back for the put, and
        // the deleted text restored afterwards (nv_put_opt's reg1 and reg2).
        Content saved = saving ? c.registers.get(name).orElse(null) : null;
        Range range = range(c, mode, "d");
        remember(editor, mode);
        editor.visualAnchor(null);
        editor.mode(Mode.NORMAL);
        Context delete = new Context(editor);
        delete.register = keepRegisters ? '_' : 0;
        editor.setCursor(range.span().start(), true);
        // A linewise delete of every line leaves Vim's buffer empty (ML_EMPTY).
        Buffer b = editor.buffer();
        boolean empty = range.span().linewise() && range.span().start().line == 0 && range.span().end().line == b.count() - 1;
        Operators.delete(delete, range.span(), false);
        editor.marks().set('[', range.span().start());
        char target = 0;
        Content deleted = null;
        if (saving) {
            target = c.registers.resolve(name);
            deleted = c.registers.get(target).orElse(null);
            c.registers.restore(target, saved);
        }
        int flags = mode == Mode.VISUAL_LINE ? Put.LINE : mode == Mode.VISUAL ? Put.LINE_SPLIT : 0;
        boolean forward = false;
        if (mode == Mode.VISUAL_BLOCK) flags |= Put.LINE_FORWARD;
        Pos opStart = editor.marks().get('[');
        Pos cursor = editor.cursor();
        if (opStart != null && (mode != Mode.VISUAL_LINE && cursor.col < opStart.col || mode == Mode.VISUAL_LINE && cursor.line < opStart.line)) forward = true;
        Pos endMark = editor.marks().get(END), startMark = editor.marks().get(START);
        int visualEnd = Math.max(endMark == null ? -1 : endMark.line, startMark == null ? -1 : startMark.line);
        Context put = new Context(editor);
        put.register = c.register;
        put.count = c.count;
        put.hasCount = c.hasCount;
        try {
            Put.put(put, forward, c.count, flags, mode == Mode.VISUAL_BLOCK ? visualEnd : -1);
            // When all lines were selected and deleted, the put leaves the empty line that was left, which goes now;
            // the cursor, if it was on it, goes to the end of the last line.
            if (empty && b.count() > 1 && b.line(b.count() - 1).isEmpty()) {
                int last = b.count() - 1;
                b.deleteLines(last, last);
                if (editor.cursor().line >= last) editor.setCursor(new Pos(last - 1, Math.max(0, Text.last(b.line(last - 1)))));
            }
        } finally {
            if (saving) c.registers.restore(target, deleted);
            editor.machine().visualChange(range.redo());
        }
        editor.marks().set(START, editor.marks().get('['));
        editor.marks().set(END, editor.marks().get(']'));
        editor.machine().visualChange(range.redo());
    }

    /** getvvcol's start: the display column where the character at {@code pos} starts. */
    static int startVcol(Buffer b, Pos pos, int ts) { return Text.virtcol(b.line(pos.line), Math.min(pos.col, b.line(pos.line).length()), ts); }

    /** getvvcol's end: the last display column of the character at {@code pos} (the end of a line counts as one). */
    static int endVcol(Buffer b, Pos pos, int ts) {
        String line = b.line(pos.line);
        int col = Math.min(pos.col, line.length());
        int start = Text.virtcol(line, col, ts);
        return col < line.length() ? start + Text.width(line, col, start, ts) - 1 : start;
    }

    /** coladvance in Visual mode: the character covering display column {@code vcol}, or the end of the line. */
    static int coladvance(Buffer b, int line, int vcol, int ts) { return Text.columnAt(b.line(line), vcol, ts); }
}
