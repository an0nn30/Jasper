package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Kind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text objects after an operator (spec 4.1), ported from Vim's textobject.c (current_word, current_sent,
 * current_par, current_block, current_quote). Plan V1b adds their Visual-mode branches. Tags are Jasper's own
 * scanner (Vim's current_tagblock is regex-driven). An object returns null to fail.
 */
final class TextObjects {
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][^\\s/>]*)[^>]*?(/?)>");

    private TextObjects() { }

    static void register(VimCommands commands) {
        commands.object("iw", "inner word", c -> word(c, false, false));
        commands.object("aw", "a word, with white space", c -> word(c, true, false));
        commands.object("iW", "inner WORD", c -> word(c, false, true));
        commands.object("aW", "a WORD, with white space", c -> word(c, true, true));
        commands.object("is", "inner sentence", c -> sentence(c, false));
        commands.object("as", "a sentence, with white space", c -> sentence(c, true));
        commands.object("ip", "inner paragraph", c -> paragraph(c, false));
        commands.object("ap", "a paragraph, with the blank lines after it", c -> paragraph(c, true));
        for (String keys : List.of("i(", "i)", "ib")) commands.object(keys, "inside ( )", c -> block(c, false, '(', ')'));
        for (String keys : List.of("a(", "a)", "ab")) commands.object(keys, "a ( ) block", c -> block(c, true, '(', ')'));
        for (String keys : List.of("i{", "i}", "iB")) commands.object(keys, "inside { }", c -> block(c, false, '{', '}'));
        for (String keys : List.of("a{", "a}", "aB")) commands.object(keys, "a { } block", c -> block(c, true, '{', '}'));
        for (String keys : List.of("i[", "i]")) commands.object(keys, "inside [ ]", c -> block(c, false, '[', ']'));
        for (String keys : List.of("a[", "a]")) commands.object(keys, "a [ ] block", c -> block(c, true, '[', ']'));
        for (String keys : List.of("i<lt>", "i>")) commands.object(keys, "inside angle brackets", c -> block(c, false, '<', '>'));
        for (String keys : List.of("a<lt>", "a>")) commands.object(keys, "an angle-bracket block", c -> block(c, true, '<', '>'));
        for (char quote : new char[] {'"', '\'', '`'}) {
            commands.object("i" + quote, "inside " + quote + " quotes", c -> quote(c, false, quote));
            commands.object("a" + quote, "a " + quote + " string, with white space", c -> quote(c, true, quote));
        }
        commands.object("it", "inside a tag block", c -> tag(c, false));
        commands.object("at", "a tag block, with its tags", c -> tag(c, true));
    }

    /** current_word. */
    static Span word(Context c, boolean include, boolean bigword) {
        Walk walk = c.walk(bigword);
        Pos cursor = c.cursor();
        int count = c.count;
        boolean inclusive = true, includeWhite = false;
        walk.backInLine(cursor);
        Pos start = cursor.copy();
        if ((walk.cls(cursor) == 0) == include) {
            if (!walk.endWord(cursor, 1, true, true)) return null;
        } else {
            walk.fwdWord(cursor, 1, true);
            if (cursor.col == 0) walk.decl(cursor);
            else walk.oneLeft(cursor);
            if (include) includeWhite = true;
        }
        count--;
        while (count > 0) {
            inclusive = true;
            if (walk.incl(cursor) == Walk.FAIL) return null;
            if (include != (walk.cls(cursor) == 0)) {
                if (!walk.fwdWord(cursor, 1, true) && count > 1) return null;
                if (!walk.oneLeft(cursor)) inclusive = false;
            } else if (!walk.endWord(cursor, 1, true, true)) return null;
            count--;
        }
        if (includeWhite && (walk.cls(cursor) != 0 || cursor.col == 0 && !inclusive)) {
            // Take white space before the word instead ("daw" on the last word of a sentence), but not indent.
            Pos back = start.copy();
            if (walk.oneLeft(back)) {
                walk.backInLine(back);
                if (walk.cls(back) == 0 && back.col > 0) start = back;
            }
        }
        return new Span(start, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** current_sent. */
    static Span sentence(Context c, boolean include) {
        Walk walk = c.walk(false);
        Pos startPos = c.cursor(), pos = startPos.copy(), cursor = startPos.copy();
        Motions.findSentence(walk, cursor, true, 1);
        int ch;
        while ((ch = walk.gchar(pos)) == ' ' || ch == '\t') walk.incl(pos);
        boolean startBlank;
        if (pos.equals(cursor)) {
            startBlank = true;
            findFirstBlank(walk, startPos);
        } else {
            startBlank = false;
            Motions.findSentence(walk, cursor, false, 1);
            startPos = cursor.copy();
        }
        int ncount = include ? c.count * 2 : c.count - (startBlank ? 1 : 0);
        if (ncount > 0) findSentenceForward(walk, cursor, ncount, true);
        else walk.decl(cursor);
        if (include) {
            if (startBlank) {
                findFirstBlank(walk, cursor);
                ch = walk.gchar(cursor);
                if (ch == ' ' || ch == '\t') walk.decl(cursor);
            } else if ((ch = walk.gchar(cursor)) != ' ' && ch != '\t') findFirstBlank(walk, startPos);
        }
        boolean inclusive = walk.incl(cursor) == Walk.FAIL;
        return new Span(startPos, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    private static void findFirstBlank(Walk walk, Pos pos) {
        while (walk.decl(pos) != Walk.FAIL) {
            int ch = walk.gchar(pos);
            if (ch != ' ' && ch != '\t') {
                walk.incl(pos);
                break;
            }
        }
    }

    private static void findSentenceForward(Walk walk, Pos cursor, int count, boolean atStart) {
        while (count-- > 0) {
            Motions.findSentence(walk, cursor, true, 1);
            if (atStart) findFirstBlank(walk, cursor);
            if (count == 0 || atStart) walk.decl(cursor);
            atStart = !atStart;
        }
    }

    /** current_par. */
    static Span paragraph(Context c, boolean include) {
        Buffer b = c.buffer;
        int last = b.count() - 1;
        int start = c.cursor().line;
        boolean whiteInFront = white(b, start);
        while (start > 0) {
            if (whiteInFront) {
                if (!white(b, start - 1)) break;
            } else if (white(b, start - 1) || Motions.startPS(b, start, 0, false)) break;
            start--;
        }
        int end = start;
        while (end <= last && white(b, end)) end++;
        end--;
        int i = c.count;
        if (!include && whiteInFront) i--;
        while (i-- > 0) {
            if (end == last) return null;
            boolean doWhite = !include && white(b, end + 1);
            if (include || !doWhite) {
                end++;
                while (end < last && !white(b, end + 1) && !Motions.startPS(b, end + 1, 0, false)) end++;
            }
            if (i == 0 && whiteInFront && include) break;
            if (include || doWhite) while (end < last && white(b, end + 1)) end++;
        }
        if (!whiteInFront && !white(b, end) && include) while (start > 0 && white(b, start - 1)) start--;
        return new Span(new Pos(start, 0), new Pos(end, 0), Kind.LINEWISE);
    }

    private static boolean white(Buffer b, int line) { return Text.blank(b.line(line)); }

    /** current_block. */
    static Span block(Context c, boolean include, char open, char close) {
        Buffer b = c.buffer;
        Walk walk = c.walk(false);
        Pos cursor = c.cursor();
        if (open == '{') while (walk.inIndent(cursor, 1)) if (walk.inc(cursor) != 0) break;
        if (walk.gchar(cursor) == open) cursor.col++;
        Pos start = null;
        Pos found = Matching.unmatched(b, cursor, open, close, true, false);
        if (found != null) {
            for (int n = c.count; n > 0; n--) {
                Pos next = Matching.unmatched(b, cursor, open, close, true, false);
                if (next == null) break;
                cursor = next;
                start = next.copy();
            }
        } else {
            for (int n = c.count; n > 0; n--) {
                Pos next = Matching.scan(b, cursor, close, open, false, false, false);
                if (next == null) break;
                cursor = next;
                start = next.copy();
            }
        }
        if (start == null) return null;
        Pos end = Matching.unmatched(b, cursor, open, close, false, false);
        if (end == null) return null;
        boolean sol = false;
        if (!include) {
            walk.incl(start);
            sol = end.col == 0;
            walk.decl(end);
            while (walk.inIndent(end, 1)) {
                sol = true;
                if (walk.decl(end) != 0) break;
            }
        }
        if (sol) {
            walk.incl(end);
            return new Span(start, end, Kind.EXCLUSIVE);
        }
        if (start.compareTo(end) <= 0) return new Span(start, end, Kind.INCLUSIVE);
        return new Span(start, start.copy(), Kind.EXCLUSIVE);
    }

    /** current_quote, within the cursor's line. */
    static Span quote(Context c, boolean include, char quote) {
        Pos cursor = c.cursor();
        String line = c.buffer.line(cursor.line);
        int colStart = cursor.col, colEnd;
        if (colStart < line.length() && line.charAt(colStart) == quote) {
            int firstCol = colStart;
            colStart = 0;
            while (true) {
                colStart = nextQuote(line, colStart, quote, false);
                if (colStart < 0 || colStart > firstCol) return null;
                colEnd = nextQuote(line, colStart + 1, quote, true);
                if (colEnd < 0) return null;
                if (colStart <= firstCol && firstCol <= colEnd) break;
                colStart = colEnd + 1;
            }
        } else {
            colStart = previousQuote(line, colStart, quote);
            if (colStart >= line.length() || line.charAt(colStart) != quote) {
                colStart = nextQuote(line, colStart, quote, false);
                if (colStart < 0) return null;
            }
            colEnd = nextQuote(line, colStart + 1, quote, true);
            if (colEnd < 0) return null;
        }
        if (include) {
            if (colEnd + 1 < line.length() && white(line.charAt(colEnd + 1))) {
                while (colEnd + 1 < line.length() && white(line.charAt(colEnd + 1))) colEnd++;
            } else while (colStart > 0 && white(line.charAt(colStart - 1))) colStart--;
        }
        if (!include && c.count < 2) colStart++;
        Pos start = new Pos(cursor.line, colStart), end = new Pos(cursor.line, colEnd);
        boolean inclusive = false;
        if (include || c.count > 1) {
            int next = Text.next(line, colEnd);
            if (next >= line.length()) inclusive = true;
            end.col = next;
        }
        return new Span(start, end, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    private static boolean white(char c) { return c == ' ' || c == '\t'; }

    /** find_next_quote, with 'quoteescape' {@code \} when {@code escape}. */
    private static int nextQuote(String line, int col, char quote, boolean escape) {
        while (true) {
            if (col >= line.length()) return -1;
            char c = line.charAt(col);
            if (escape && c == '\\') {
                col++;
                if (col >= line.length()) return -1;
            } else if (c == quote) return col;
            col = Text.next(line, col);
        }
    }

    /** find_prev_quote. */
    private static int previousQuote(String line, int colStart, char quote) {
        while (colStart > 0) {
            colStart = Text.previous(line, colStart);
            int n = 0;
            while (colStart - n > 0 && line.charAt(colStart - n - 1) == '\\') n++;
            if ((n & 1) == 1) colStart -= n;
            else if (line.charAt(colStart) == quote) break;
        }
        return colStart;
    }

    /** it and at: the {@code count}-th tag block around the cursor, by Jasper's tag scanner. */
    static Span tag(Context c, boolean include) {
        Buffer b = c.buffer;
        String text = b.text();
        int cursor = b.offset(c.cursor());
        List<int[]> around = new ArrayList<>();
        Deque<Object[]> open = new ArrayDeque<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            if (!m.group(3).isEmpty()) continue;
            String name = m.group(2).toLowerCase(Locale.ROOT);
            if (m.group(1).isEmpty()) {
                open.push(new Object[] {name, m.start(), m.end()});
                continue;
            }
            while (!open.isEmpty()) {
                Object[] top = open.pop();
                if (!top[0].equals(name)) continue;
                int openStart = (Integer) top[1], openEnd = (Integer) top[2];
                if (openStart <= cursor && cursor < m.end()) around.add(new int[] {openStart, openEnd, m.start(), m.end()});
                break;
            }
        }
        if (around.size() < c.count) return null;
        around.sort((x, y) -> Integer.compare(y[0], x[0]));
        int[] pair = around.get(c.count - 1);
        int from = include ? pair[0] : pair[1], to = include ? pair[3] : pair[2];
        return new Span(b.pos(from), b.pos(to), Kind.EXCLUSIVE);
    }
}
