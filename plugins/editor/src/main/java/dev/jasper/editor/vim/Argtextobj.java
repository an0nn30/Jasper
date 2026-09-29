package dev.jasper.editor.vim;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The argtextobj extension (spec 8), ported from Takahiro Suzuki's argtextobj.vim 1.1.1: {@code ia} is the function
 * argument at the cursor, {@code aa} the argument with its comma and the space after it. Like the plugin it walks
 * out with {@code [(} to the parenthesis a name stands before (all the way out with
 * {@code g:argumentobject_force_toplevel}), finds its pair with {@code %}, yanks what is between, and splits that at
 * the commas outside quotes and brackets; the cursor then moves with {@code h} and {@code l} and the area is selected
 * charwise. Quoted strings are found on a copy of the line, where the plugin replaces them in the text and puts the
 * line back.
 */
final class Argtextobj {
    private Argtextobj() { }

    private static final String OPTION = "argtextobj";
    /** Vim's {@code [^']} and {@code .}: neither matches a line break. */
    private static final Pattern SINGLE = Pattern.compile("'([^'\\n]*?)'"), SQUARE = Pattern.compile("\\[([^'\\n]*?)\\]"),
        ANGLE = Pattern.compile("<([^'\\n]*?)>"), DOUBLE = Pattern.compile("\"([^'\\n]*?)\""), PARENS = Pattern.compile("\\(([^()\\n]*?)\\)");

    static void register(VimCommands commands) {
        commands.object("<Plug>InnerArgument", "the function argument at the cursor (`ia`, with `set argtextobj`)", c -> select(c, true));
        commands.object("<Plug>OuterArgument", "the function argument with its comma and the space after it (`aa`)", c -> select(c, false));
    }

    /**
     * The mappings: in Visual mode {@code <Esc>} first, then {@code :call s:MotionArgument()}. When it selects
     * nothing, an operator gets the {@code :} motion to wherever the cursor went, and Visual mode has ended.
     */
    private static Span select(Context c, boolean inner) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        if (c.visual != null) Visual.end(editor);
        Pos start = editor.cursor();
        if (motionArgument(c, inner) || c.visual != null) return TextObjects.VISUAL_DONE;
        Pos end = editor.cursor();
        return new Span(Pos.min(start, end), Pos.max(start, end), Target.Kind.EXCLUSIVE);
    }

    /** s:MotionArgument; returns false where it returns before selecting. */
    private static boolean motionArgument(Context c, boolean inner) {
        VimEditor editor = c.editor;
        char current = under(editor);
        if (current == ',' || current == '(') Script.normal(editor, "l");
        Pos undoCursor = getOutOfDoubleQuote(editor);
        Pos rightup = outerFunctionParenthesis(c);
        // The plugin looks for the parenthesis on the cursor's line, at the column it found.
        if (at(editor.buffer().line(editor.cursor().line), rightup.col) != '(') return false;
        Pos rightupPair = pair(editor, rightup);
        String arglist = innerText(editor, rightup, rightupPair);
        int offset = editor.cursor().col - rightup.col - 1;
        String sub = replace(SINGLE, arglist, m -> "(" + underscores(m.group(1)) + ")");
        sub = replace(SQUARE, sub, m -> "(" + underscores(m.group(1)) + ")");
        sub = replace(ANGLE, sub, m -> "(" + underscores(m.group(1)) + ")");
        sub = replace(DOUBLE, sub, m -> "(" + m.group(1) + ")");
        while (sub.indexOf('(') >= 0 && sub.indexOf(')') >= 0) {
            String next = replace(PARENS, sub, m -> "<" + m.group(1).replace(',', '_') + ">");
            // Parentheses that no longer pair up: Vim would loop for ever here.
            if (next.equals(sub)) break;
            sub = next;
        }
        int begin = Math.max(sub.lastIndexOf(',', offset) + 1, 0);
        int comma = offset >= sub.length() ? -1 : sub.indexOf(',', Math.max(0, offset));
        int end = comma == -1 ? sub.length() - 1 : comma - 1;
        int left = offset - begin, right = end - begin;
        boolean deleteTrailingSpace = false;
        if (inner) {
            moveLeft(editor, left);
            right -= moveToNextNonSpace(editor);
        } else if (begin == 0 && end == sub.length() - 1) moveLeft(editor, left);
        else if (begin == 0) {
            moveLeft(editor, left);
            right++;
            deleteTrailingSpace = true;
        } else {
            moveLeft(editor, left + 1);
            right++;
        }
        Script.normal(editor, "v");
        moveRight(editor, right);
        if (deleteTrailingSpace) {
            Script.normal(editor, "l");
            moveToNextNonSpace(editor);
            Script.normal(editor, "h");
        }
        editor.machine().undoCursor(undoCursor);
        return true;
    }

    /**
     * s:GetOutOfDoubleQuote: every {@code a"} area on the line, taken in turn from its first non-blank, is filled
     * with {@code _} ({@code ?} when the cursor is on a {@code _}); when that covered the cursor, the cursor goes to
     * the quote before it ({@code F"}), or left ({@code h}) from where the first non-blank then was.
     *
     * @return where the cursor was when the plugin first changed the text: the start of the first {@code a"} area,
     *     which {@code gvr_} replaced, or else the cursor, where {@code setline()} put the line back
     */
    private static Pos getOutOfDoubleQuote(VimEditor editor) {
        Pos cursor = editor.cursor();
        String work = editor.buffer().line(cursor.line);
        char repl = at(work, cursor.col) == '_' ? '?' : '_';
        int first;
        Pos changed = cursor;
        boolean replaced = false;
        while (true) {
            first = Math.min(Text.firstNonBlank(work), Math.max(0, work.length() - 1));
            int[] area = TextObjects.aroundQuote(work, first, '"');
            if (area == null) break;
            if (!replaced) changed = new Pos(cursor.line, area[0]);
            replaced = true;
            StringBuilder filled = new StringBuilder(work);
            for (int i = area[0]; i <= area[1]; i++) filled.setCharAt(i, repl);
            work = filled.toString();
        }
        if (at(work, cursor.col) == repl) Script.normal(editor, cursor.col == first ? "h" : "F\"");
        return changed;
    }

    /** s:GetOuterFunctionParenthesis: {@code [(} until the parenthesis follows a name, or as far out as it goes. */
    private static Pos outerFunctionParenthesis(Context c) {
        VimEditor editor = c.editor;
        boolean toplevel = !String.valueOf(c.vim.variables().getOrDefault("argumentobject_force_toplevel", 0L)).equals("0");
        Pos save = editor.cursor(), before = save;
        Script.normal(editor, "[(");
        Pos rightup = editor.cursor();
        while (!rightup.equals(before)) {
            Pos p = editor.cursor();
            if (!toplevel && p.col > 0 && word(editor.buffer().line(p.line).charAt(p.col - 1))) break;
            before = rightup;
            Script.normal(editor, "[(");
            rightup = editor.cursor();
        }
        editor.setCursor(save);
        return rightup;
    }

    /** s:GetPair: {@code %h} from the parenthesis. */
    private static Pos pair(VimEditor editor, Pos pos) {
        Pos save = editor.cursor();
        editor.setCursor(pos);
        Script.normal(editor, "%h");
        Pos pair = editor.cursor();
        editor.setCursor(save);
        return pair;
    }

    /** s:GetInnerText: {@code lv}, to the other end, {@code y}; the unnamed register then gets back its text. */
    private static String innerText(VimEditor editor, Pos from, Pos to) {
        Pos save = editor.cursor();
        String regSave = Script.getreg(editor, '"');
        editor.setCursor(from);
        Script.normal(editor, "lv");
        editor.setCursor(to);
        Script.normal(editor, "y");
        String val = Script.getreg(editor, '"');
        editor.setCursor(save);
        // let @@ = reg_save
        Script.setreg(editor, '"', regSave, "");
        return val;
    }

    /** s:MoveToNextNonSpace: {@code l} over spaces; the number of moves. */
    private static int moveToNextNonSpace(VimEditor editor) {
        Pos old = editor.cursor();
        int moved = 0;
        while (under(editor) == ' ') {
            Script.normal(editor, "l");
            if (old.equals(editor.cursor())) break;
            old = editor.cursor();
            moved++;
        }
        return moved;
    }

    private static void moveLeft(VimEditor editor, int num) { if (num > 0) Script.normal(editor, num + "h"); }

    private static void moveRight(VimEditor editor, int num) { if (num > 0) Script.normal(editor, num + "l"); }

    private static char under(VimEditor editor) {
        Pos p = editor.cursor();
        return at(editor.buffer().line(p.line), p.col);
    }

    private static char at(String line, int col) { return col >= 0 && col < line.length() ? line.charAt(col) : 0; }

    /** {@code [a-zA-Z0-9_]}. */
    private static boolean word(char ch) { return ch < 128 && (Character.isLetterOrDigit(ch) || ch == '_'); }

    /** substitute(s, ".", "_", "g"): every character but a line break. */
    private static String underscores(String s) { return s.replaceAll("[^\\n]", "_"); }

    /** substitute(s, pattern, '\=…', 'g'). */
    private static String replace(Pattern pattern, String s, Function<Matcher, String> with) {
        Matcher m = pattern.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(with.apply(m)));
        m.appendTail(out);
        return out.toString();
    }
}
