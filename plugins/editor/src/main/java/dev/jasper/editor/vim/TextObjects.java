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
 * Text objects (spec 4.1), ported from Vim's textobject.c (current_word, current_sent, current_par,
 * current_block, current_quote), with their Visual-mode branches: after an operator an object returns the span
 * it covers; in Visual mode it moves the area's ends and mode itself, as Vim's code does to VIsual, and returns
 * {@link #VISUAL_DONE}. Tags are Jasper's own scanner (Vim's current_tagblock searches with patterns). An object
 * returns null to fail.
 */
final class TextObjects {
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][^\\s/>]*)[^>]*?(/?)>");
    /** What an object returns when it has updated the Visual area. */
    static final Span VISUAL_DONE = new Span(new Pos(0, 0), new Pos(0, 0), Kind.INCLUSIVE);

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

    /** Sets the Visual area and mode, as the Visual branches assign VIsual and VIsual_mode. */
    private static Span visual(Context c, Pos anchor, Pos cursor, Mode mode) {
        c.editor.visualAnchor(anchor);
        c.editor.mode(mode);
        c.editor.setCursor(cursor, true);
        return VISUAL_DONE;
    }

    /** V becomes v; other Visual modes stay (current_word, current_quote). */
    private static Mode charwise(Context c) {
        Mode mode = c.editor.mode();
        return mode == Mode.VISUAL_LINE ? Mode.VISUAL : mode;
    }

    /** current_word. */
    static Span word(Context c, boolean include, boolean bigword) {
        Walk walk = c.walk(bigword);
        Pos cursor = c.cursor();
        Pos visual = c.visual;
        int count = c.count;
        boolean inclusive = true, includeWhite = false;
        Pos start = new Pos(0, 0);
        if (visual == null || cursor.equals(visual)) {
            walk.backInLine(cursor);
            start = cursor.copy();
            if ((walk.cls(cursor) == 0) == include) {
                if (!walk.endWord(cursor, 1, true, true)) return null;
            } else {
                walk.fwdWord(cursor, 1, true);
                if (cursor.col == 0) walk.decl(cursor);
                else walk.oneLeft(cursor);
                if (include) includeWhite = true;
            }
            if (visual != null) visual = start.copy();
            count--;
        }
        while (count > 0) {
            inclusive = true;
            if (visual != null && cursor.compareTo(visual) < 0) {
                if (walk.decl(cursor) == Walk.FAIL) return null;
                if (include != (walk.cls(cursor) != 0)) {
                    if (!walk.bckWord(cursor, 1, true)) return null;
                } else {
                    if (!walk.bckendWord(cursor, 1, true)) return null;
                    walk.incl(cursor);
                }
            } else {
                if (walk.incl(cursor) == Walk.FAIL) return null;
                if (include != (walk.cls(cursor) == 0)) {
                    if (!walk.fwdWord(cursor, 1, true) && count > 1) return null;
                    if (!walk.oneLeft(cursor)) inclusive = false;
                } else if (!walk.endWord(cursor, 1, true, true)) return null;
            }
            count--;
        }
        if (includeWhite && (walk.cls(cursor) != 0 || cursor.col == 0 && !inclusive)) {
            // Take white space before the word instead ("daw" on the last word of a sentence), but not indent.
            Pos back = start.copy();
            if (walk.oneLeft(back)) {
                walk.backInLine(back);
                if (walk.cls(back) == 0 && back.col > 0) {
                    if (visual != null) visual = back;
                    else start = back;
                }
            }
        }
        if (visual != null) return visual(c, visual, cursor, charwise(c));
        return new Span(start, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** current_sent. */
    static Span sentence(Context c, boolean include) {
        Walk walk = c.walk(false);
        Pos startPos = c.cursor(), pos = startPos.copy(), cursor = startPos.copy();
        Pos visual = c.visual;
        int count = c.count;
        Motions.findSentence(walk, cursor, true, 1);
        if (visual != null && !startPos.equals(visual)) return extendSentence(c, walk, startPos, pos, cursor, count, include);
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
        int ncount = include ? count * 2 : count - (startBlank ? 1 : 0);
        if (ncount > 0) findSentenceForward(walk, cursor, ncount, true);
        else walk.decl(cursor);
        if (include) {
            if (startBlank) {
                findFirstBlank(walk, cursor);
                ch = walk.gchar(cursor);
                if (ch == ' ' || ch == '\t') walk.decl(cursor);
            } else if ((ch = walk.gchar(cursor)) != ' ' && ch != '\t') findFirstBlank(walk, startPos);
        }
        if (visual != null) {
            // Avoid getting stuck with "is" on a single space before a sentence.
            if (startPos.equals(cursor)) return extendSentence(c, walk, startPos, pos, cursor.copy(), count, include);
            return visual(c, startPos, cursor, Mode.VISUAL);
        }
        boolean inclusive = walk.incl(cursor) == Walk.FAIL;
        return new Span(startPos, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** current_sent's "extend" branch: a Visual area bigger than one character grows by whole sentences. */
    private static Span extendSentence(Context c, Walk walk, Pos startPos, Pos pos, Pos nextStart, int count, boolean include) {
        Pos visual = c.visual;
        // Vim's cursor here is where findsent(FORWARD) left it: the start of the next sentence.
        Pos cursor = nextStart.copy();
        int ch;
        if (startPos.compareTo(visual) < 0) {
            boolean atStart = true;
            walk.decl(pos);
            while (pos.compareTo(nextStart) < 0) {
                ch = walk.gchar(pos);
                if (ch != ' ' && ch != '\t') {
                    atStart = false;
                    break;
                }
                walk.incl(pos);
            }
            if (!atStart) {
                Motions.findSentence(walk, cursor, false, 1);
                if (cursor.equals(startPos)) atStart = true;
                else Motions.findSentence(walk, cursor, true, 1);
            }
            if (include) count *= 2;
            while (count-- > 0) {
                if (atStart) findFirstBlank(walk, cursor);
                ch = walk.gchar(cursor);
                if (!atStart || !include && ch != ' ' && ch != '\t') Motions.findSentence(walk, cursor, false, 1);
                atStart = !atStart;
            }
        } else {
            walk.incl(pos);
            boolean atStart = true;
            if (!pos.equals(cursor)) {
                atStart = false;
                while (pos.compareTo(cursor) < 0) {
                    ch = walk.gchar(pos);
                    if (ch != ' ' && ch != '\t') {
                        atStart = true;
                        break;
                    }
                    walk.incl(pos);
                }
                if (atStart) Motions.findSentence(walk, cursor, false, 1);
                else cursor = startPos.copy();
            }
            if (include) count *= 2;
            findSentenceForward(walk, cursor, count, atStart);
        }
        c.editor.setCursor(cursor, true);
        return VISUAL_DONE;
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
        Pos visual = c.visual;
        if (visual != null && start != visual.line) return extendParagraph(c, start, include);
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
        if (visual != null) {
            // "Vipipip" on a single white line would get stuck: extend instead.
            if (c.editor.mode() == Mode.VISUAL_LINE && start == c.cursor().line) return extendParagraph(c, start, include);
            Pos anchor = visual.line != start ? new Pos(start, 0) : visual;
            return visual(c, anchor, new Pos(end, 0), Mode.VISUAL_LINE);
        }
        return new Span(new Pos(start, 0), new Pos(end, 0), Kind.LINEWISE);
    }

    /** current_par's "extend" branch: a Visual area over several lines grows by whole paragraphs. */
    private static Span extendParagraph(Context c, int startLine, boolean include) {
        Buffer b = c.buffer;
        int last = b.count() - 1;
        int dir = startLine < c.visual.line ? -1 : 1;
        boolean failed = false;
        for (int i = c.count; --i >= 0; ) {
            if (startLine == (dir < 0 ? 0 : last)) {
                failed = true;
                break;
            }
            int prevStartIsWhite = -1;
            for (int t = 0; t < 2; t++) {
                startLine += dir;
                int startIsWhite = white(b, startLine) ? 1 : 0;
                if (prevStartIsWhite == startIsWhite) {
                    startLine -= dir;
                    break;
                }
                while (startLine != (dir < 0 ? 0 : last)) {
                    if ((startIsWhite == 1) != white(b, startLine + dir)
                        || startIsWhite == 0 && Motions.startPS(b, startLine + (dir > 0 ? 1 : 0), 0, false)) break;
                    startLine += dir;
                }
                if (!include) break;
                if (startLine == (dir < 0 ? 0 : last)) break;
                prevStartIsWhite = startIsWhite;
            }
        }
        c.editor.setCursor(new Pos(startLine, 0), true);
        return failed ? null : VISUAL_DONE;
    }

    private static boolean white(Buffer b, int line) { return Text.blank(b.line(line)); }

    /** current_block. */
    static Span block(Context c, boolean include, char open, char close) {
        Buffer b = c.buffer;
        Walk walk = c.walk(false);
        Pos oldPos = c.cursor(), cursor = oldPos.copy();
        Pos oldStart = oldPos.copy(), oldEnd = oldPos.copy();
        Pos visual = c.visual;
        if (visual == null || visual.equals(cursor)) {
            if (open == '{') while (walk.inIndent(cursor, 1)) if (walk.inc(cursor) != 0) break;
            if (walk.gchar(cursor) == open) cursor.col++;
        } else if (visual.compareTo(cursor) < 0) {
            oldStart = visual.copy();
            cursor = visual.copy();
        } else oldEnd = visual.copy();
        Pos start = null;
        if (Matching.unmatched(b, cursor, open, close, true, false) != null) {
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
        Pos endPos = Matching.unmatched(b, cursor, open, close, false, false);
        if (endPos == null) return null;
        Pos end = endPos.copy();
        boolean sol = false;
        while (!include) {
            walk.incl(start);
            sol = end.col == 0;
            walk.decl(end);
            while (walk.inIndent(end, 1)) {
                sol = true;
                if (walk.decl(end) != 0) break;
            }
            if (start.equals(endPos) && visual != null) return null;
            if (start.compareTo(oldStart) >= 0 && oldEnd.compareTo(end) >= 0 && !start.equals(end) && visual != null) {
                // The inner block is no bigger than the Visual area: take the enclosing block.
                cursor = oldStart.copy();
                walk.decl(cursor);
                Pos outer = Matching.unmatched(b, cursor, open, close, true, false);
                if (outer == null) return null;
                start = outer.copy();
                Pos outerEnd = Matching.unmatched(b, outer, open, close, false, false);
                if (outerEnd == null) return null;
                endPos = outerEnd;
                end = outerEnd.copy();
            } else break;
        }
        if (visual != null) {
            if (sol && walk.gchar(end) != 0) walk.inc(end);
            return visual(c, start, end, Mode.VISUAL);
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
        Pos visual = c.visual;
        String line = c.buffer.line(cursor.line);
        int colStart = cursor.col, colEnd;
        boolean visEmpty = true, visBefore = false, insideQuotes = false, selectedQuote = false;
        if (visual != null) {
            if (visual.line != cursor.line) return null;
            visBefore = visual.compareTo(cursor) < 0;
            visEmpty = visual.equals(cursor);
        }
        if (!visEmpty) {
            int i;
            if (visBefore) {
                insideQuotes = visual.col > 0 && at(line, visual.col - 1) == quote && cursor.col < line.length() && at(line, cursor.col + 1) == quote;
                i = visual.col;
                colEnd = cursor.col;
            } else {
                insideQuotes = cursor.col > 0 && at(line, cursor.col - 1) == quote && visual.col < line.length() && at(line, visual.col + 1) == quote;
                i = cursor.col;
                colEnd = visual.col;
            }
            while (i <= colEnd && i < line.length()) if (line.charAt(i++) == quote) {
                selectedQuote = true;
                break;
            }
        }
        if (!visEmpty && at(line, colStart) == quote) {
            if (visBefore) {
                colStart = nextQuote(line, colStart + 1, quote, false);
                if (colStart < 0) return null;
                colEnd = nextQuote(line, colStart + 1, quote, true);
                if (colEnd < 0) {
                    colEnd = colStart;
                    colStart = cursor.col;
                }
            } else {
                colEnd = previousQuote(line, colStart, quote, false);
                if (at(line, colEnd) != quote) return null;
                colStart = previousQuote(line, colEnd, quote, true);
                if (at(line, colStart) != quote) {
                    colStart = colEnd;
                    colEnd = cursor.col;
                }
            }
        } else {
            int firstCol = colStart;
            if (!visEmpty) firstCol = visBefore ? nextQuote(line, colStart, quote, false) : previousQuote(line, colStart, quote, false);
            int[] pair = at(line, colStart) == quote || !visEmpty ? pairAround(line, firstCol, quote) : pairFrom(line, colStart, quote);
            if (pair == null) return null;
            colStart = pair[0];
            colEnd = pair[1];
        }
        if (include) {
            int[] area = withWhite(line, colStart, colEnd);
            colStart = area[0];
            colEnd = area[1];
        }
        if (!include && c.count < 2 && (visEmpty || !insideQuotes)) colStart++;
        Pos start = new Pos(cursor.line, colStart);
        Pos newVisual = visual;
        if (visual != null && (visEmpty || visBefore && !selectedQuote && (insideQuotes
            || at(line, visual.col) != quote && (visual.col == 0 || at(line, visual.col - 1) != quote)))) newVisual = start.copy();
        Pos end = new Pos(cursor.line, colEnd);
        boolean inclusive = false;
        if (include || c.count > 1 || !visEmpty && insideQuotes) {
            int next = Text.next(line, colEnd);
            if (next >= line.length()) inclusive = true;
            end.col = next;
        }
        if (visual != null) {
            Pos newCursor;
            if (visEmpty || visBefore) {
                newCursor = end.copy();
                if (newCursor.col > 0) newCursor.col = Text.previous(line, newCursor.col);
            } else {
                if (insideQuotes || !selectedQuote && at(line, visual.col) != quote && (visual.col >= line.length() || at(line, visual.col + 1) != quote)) {
                    Pos back = end.copy();
                    if (back.col > 0) back.col = Text.previous(line, back.col);
                    newVisual = back;
                }
                newCursor = new Pos(cursor.line, colStart);
            }
            return visual(c, newVisual, newCursor, charwise(c));
        }
        return new Span(start, end, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** The quoted string holding {@code firstCol}, a quote itself, counting pairs of quotes from the line's start. */
    private static int[] pairAround(String line, int firstCol, char quote) {
        int colStart = 0;
        while (true) {
            colStart = nextQuote(line, colStart, quote, false);
            if (colStart < 0 || colStart > firstCol) return null;
            int colEnd = nextQuote(line, colStart + 1, quote, true);
            if (colEnd < 0) return null;
            if (colStart <= firstCol && firstCol <= colEnd) return new int[] { colStart, colEnd };
            colStart = colEnd + 1;
        }
    }

    /** The quoted string {@code col} is in (from the quote before it), or else the next one on the line. */
    private static int[] pairFrom(String line, int col, char quote) {
        int colStart = previousQuote(line, col, quote, true);
        if (at(line, colStart) != quote) {
            colStart = nextQuote(line, colStart, quote, false);
            if (colStart < 0) return null;
        }
        int colEnd = nextQuote(line, colStart + 1, quote, true);
        return colEnd < 0 ? null : new int[] { colStart, colEnd };
    }

    /** {@code a"}'s white space: after the closing quote, or before the opening one when there is none after. */
    private static int[] withWhite(String line, int colStart, int colEnd) {
        if (white(at(line, colEnd + 1))) while (white(at(line, colEnd + 1))) colEnd++;
        else while (colStart > 0 && white(at(line, colStart - 1))) colStart--;
        return new int[] { colStart, colEnd };
    }

    /**
     * What {@code v} then {@code a"} selects at {@code col} (current_quote with an empty Visual area), on a line
     * that need not be the text's: the string and its white space, as {@code {start, end}}, both included; null
     * when there is none.
     */
    static int[] aroundQuote(String line, int col, char quote) {
        int[] pair = at(line, col) == quote ? pairAround(line, col, quote) : pairFrom(line, col, quote);
        return pair == null ? null : withWhite(line, pair[0], pair[1]);
    }

    private static char at(String line, int col) { return col >= 0 && col < line.length() ? line.charAt(col) : 0; }

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

    /** find_prev_quote, with 'quoteescape' {@code \} when {@code escape}. */
    private static int previousQuote(String line, int colStart, char quote, boolean escape) {
        while (colStart > 0) {
            colStart = Text.previous(line, colStart);
            int n = 0;
            if (escape) while (colStart - n > 0 && line.charAt(colStart - n - 1) == '\\') n++;
            if ((n & 1) == 1) colStart -= n;
            else if (line.charAt(colStart) == quote) break;
        }
        return colStart;
    }

    /** it and at: the {@code count}-th tag block around the cursor (or around the Visual area). */
    static Span tag(Context c, boolean include) {
        Buffer b = c.buffer;
        String text = b.text();
        Pos visual = c.visual;
        int cursor = b.offset(c.cursor());
        int low = cursor, high = cursor;
        if (visual != null && !visual.equals(c.cursor())) {
            low = Math.min(cursor, b.offset(visual));
            high = Math.max(cursor, b.offset(visual));
        }
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
                if (openStart <= low && high < m.end()) around.add(new int[] {openStart, openEnd, m.start(), m.end()});
                break;
            }
        }
        around.sort((x, y) -> Integer.compare(y[0], x[0]));
        int index = c.count - 1;
        if (visual != null) {
            // A Visual area that already covers the object takes the next enclosing one, as Vim's "do it again".
            while (index < around.size()) {
                int[] pair = around.get(index);
                int from = include ? pair[0] : pair[1], to = (include ? pair[3] : pair[2]) - 1;
                if (from == low && to == high) index++;
                else break;
            }
        }
        if (index >= around.size()) return null;
        int[] pair = around.get(index);
        int from = include ? pair[0] : pair[1], to = include ? pair[3] : pair[2];
        if (visual != null) {
            Pos start = b.pos(from), end = b.pos(Math.max(from, to - 1));
            return visual(c, start, to > from ? end : start, Mode.VISUAL);
        }
        return new Span(b.pos(from), b.pos(to), Kind.EXCLUSIVE);
    }
}
