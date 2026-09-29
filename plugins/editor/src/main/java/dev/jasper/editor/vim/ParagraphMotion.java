package dev.jasper.editor.vim;

/**
 * The paragraph-motion extension (spec 8), ported from Daniel Bakker's paragraphmotion.vim: {@code }} and {@code {}
 * stop at lines holding only white space, not only at empty ones. As the plugin's mappings are {@code :} commands,
 * the motion is exclusive and, unlike Vim's own {@code }}, a delete with it is not put in register 1.
 */
final class ParagraphMotion {
    private ParagraphMotion() { }

    private static final String OPTION = "vim-paragraph-motion";

    static void register(VimCommands commands) {
        commands.motion("<Plug>(ParagraphNextMotion)", "forward [count] paragraphs, lines of white space ending them too (`}`, with `set vim-paragraph-motion`)",
            c -> move(c, true));
        commands.motion("<Plug>(ParagraphPrevMotion)", "back [count] paragraphs, lines of white space ending them too (`{`)", c -> move(c, false));
    }

    /** s:ParagraphMove. */
    private static Target move(Context c, boolean forward) {
        Extensions.require(c, OPTION);
        Buffer b = c.editor.buffer();
        Pos cursor = c.cursor();
        int last = b.count() - 1;
        if (forward) {
            // normal! 0, then for each paragraph: the first non-space from there, and the first white line after it.
            int line = cursor.line;
            for (int i = 0; i < c.count; i++) {
                int text = nonSpaceFrom(b, line);
                int white = text < 0 ? -1 : whiteAfter(b, text);
                // search('\%$', 'W') goes past the last character, and Vim puts the cursor back on it before the
                // operator sees it, so the motion stops short of that character.
                if (white < 0) return Target.exclusive(new Pos(last, Math.max(0, Text.last(b.line(last))))).asJump();
                line = white;
            }
            return Target.exclusive(new Pos(line, 0)).asJump();
        }
        // normal! ^, then for each paragraph: the last non-space at or before there, and the white line before it.
        String here = b.line(cursor.line);
        int col = Math.min(Text.firstNonBlank(here), Math.max(0, here.length() - 1));
        int line = nonSpaceBack(b, cursor.line, col);
        for (int i = 0; i < c.count; i++) {
            int white = line < 0 ? -1 : whiteBefore(b, line);
            if (white < 0) return Target.exclusive(new Pos(0, 0)).asJump();
            line = i + 1 < c.count ? nonSpaceBack(b, white, 0) : white;
        }
        return Target.exclusive(new Pos(line, 0)).asJump();
    }

    /** [[:space:]]: isspace(). */
    private static boolean space(char ch) { return ch == ' ' || ch == '\t' || ch == '\n' || ch == 0x0b || ch == '\f' || ch == '\r'; }

    /** {@code ^[[:space:]]*$}. */
    private static boolean white(String line) {
        for (int i = 0; i < line.length(); i++) if (!space(line.charAt(i))) return false;
        return true;
    }

    /** search('[^[:space:]]', 'Wc') from the start of {@code line}: the line of the first non-space, or -1. */
    private static int nonSpaceFrom(Buffer b, int line) {
        for (int n = line; n < b.count(); n++) if (!white(b.line(n))) return n;
        return -1;
    }

    /** search('^[[:space:]]*$', 'W') from a line with text: the next white line, or -1. */
    private static int whiteAfter(Buffer b, int line) {
        for (int n = line + 1; n < b.count(); n++) if (white(b.line(n))) return n;
        return -1;
    }

    /** search('[^[:space:]]', 'bcW') from {@code col}: the line of the last non-space at or before it, or -1. */
    private static int nonSpaceBack(Buffer b, int line, int col) {
        String text = b.line(line);
        for (int i = Math.min(col, text.length() - 1); i >= 0; i--) if (!space(text.charAt(i))) return line;
        for (int n = line - 1; n >= 0; n--) if (!white(b.line(n))) return n;
        return -1;
    }

    /** search('^[[:space:]]*$', 'bW') from a line with text: the white line before it, or -1. */
    private static int whiteBefore(Buffer b, int line) {
        for (int n = line - 1; n >= 0; n--) if (white(b.line(n))) return n;
        return -1;
    }
}
