package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Pattern search (spec 3.7), ported from Vim's search.c: searchit (the count loop, where a forward search may start
 * and 'wrapscan') and do_search (offsets and {@code ;}), with normal.c's normal_search, nv_next and nv_ident for
 * {@code n}, {@code N}, {@code *} and {@code #}. The last pattern, its direction and offset are global, as in Vim.
 */
final class Search {
    /** The last search's offset (Vim's soffset): a line offset, or a character offset from the match's start or end. */
    record Offset(boolean line, boolean end, long off) {
        static final Offset NONE = new Offset(false, false, 0);
    }

    /** A match: its start, and the position after its end. */
    record Match(Pos start, Pos end) { }

    private String pattern;
    private char direction = '/';
    private Offset offset = Offset.NONE;
    private boolean noHighlight;
    private final List<String> history = new ArrayList<>();

    /** @return the last search pattern, or null */
    String pattern() { return pattern; }

    /** @return {@code /} or {@code ?} */
    char direction() { return direction; }

    /** @return whether {@code :noh} hid the highlight until the next search */
    boolean highlightHidden() { return noHighlight; }

    void hideHighlight() { noHighlight = true; }

    /** @return the search history, oldest first */
    List<String> history() { return history; }

    void remember(String line) {
        if (line.isEmpty()) return;
        history.remove(line);
        history.add(line);
        if (history.size() > 50) history.removeFirst();
    }

    static void register(VimCommands commands) {
        commands.motionWithLine("/", "search forward for a pattern, with an offset", c -> c.editor.vim().search().command(c, '/', c.line));
        commands.motionWithLine("?", "search backward for a pattern, with an offset", c -> c.editor.vim().search().command(c, '?', c.line));
        commands.motion("n", "repeat the last search", c -> c.editor.vim().search().next(c, false));
        commands.motion("N", "repeat the last search in the other direction", c -> c.editor.vim().search().next(c, true));
        commands.motion("*", "search forward for the keyword under the cursor", c -> c.editor.vim().search().word(c, '*', false));
        commands.motion("#", "search backward for the keyword under the cursor", c -> c.editor.vim().search().word(c, '#', false));
        commands.motion("g*", "search forward for the keyword under the cursor, also inside words", c -> c.editor.vim().search().word(c, '*', true));
        commands.motion("g#", "search backward for the keyword under the cursor, also inside words", c -> c.editor.vim().search().word(c, '#', true));
    }

    /** nv_search: the typed line is a pattern, perhaps with an offset and {@code ;} searches after it. */
    Target command(Context c, char dir, String line) {
        remember(line);
        return normal(c, dir, line, true, false, false);
    }

    /** nv_next: n and N; if an offset leaves the cursor where it was, the search is repeated once more. */
    Target next(Context c, boolean reverse) {
        Pos old = c.cursor();
        Target target = normal(c, (char) 0, null, true, reverse, false);
        if (target != null && target.pos().equals(old) && !lastWrapped) {
            Context again = copy(c);
            again.count = c.count + 1;
            Target retry = normal(again, (char) 0, null, true, reverse, false);
            if (retry != null) return retry;
        }
        return target;
    }

    private static Context copy(Context c) {
        Context copy = new Context(c.editor);
        copy.count = c.count;
        copy.hasCount = c.hasCount;
        copy.operator = c.operator;
        copy.visual = c.visual;
        return copy;
    }

    /** nv_ident for * # g* g#: the keyword (or other text) under or after the cursor, as a whole word unless g. */
    Target word(Context c, char command, boolean g) {
        Buffer b = c.buffer;
        Pos p = c.cursor();
        String line = b.line(p.line);
        Keywords keywords = c.options.keywords();
        int[] found = identifier(line, p.col, keywords);
        if (found == null) throw new VimFailure("E348: No string under cursor");
        int start = found[0], end = found[1];
        String text = line.substring(start, end);
        StringBuilder pat = new StringBuilder();
        if (!g && keywords.isWord(text.codePointAt(0))) pat.append("\\<");
        String special = command == '#' ? "/?.*~[^$\\" : "/.*~[^$\\";
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (special.indexOf(ch) >= 0) pat.append('\\');
            pat.append(ch);
        }
        if (!g && keywords.isWord(text.codePointBefore(text.length()))) pat.append("\\>");
        remember(pat.toString());
        // The previous context is set first, so `` returns here; then the cursor goes to the word's start, so the
        // search skips the word.
        Context from = copy(c);
        c.editor.marks().setpcmark(p);
        c.editor.setCursor(new Pos(p.line, start), true);
        try {
            Target target = normal(from, command == '*' ? '/' : '?', pat.toString(), false, false, true);
            if (target == null) c.editor.setCursor(p, true);
            return target;
        } catch (RuntimeException failure) {
            c.editor.setCursor(p, true);
            throw failure;
        }
    }

    /**
     * find_ident_at_pos with FIND_IDENT|FIND_STRING.
     *
     * @return the start and end columns of the keyword, or other non-blank text, under or after {@code col}; or null
     */
    static int[] identifier(String line, int startcol, Keywords k) {
        int col = 0, thisClass = 0, i;
        for (i = 0; i < 2; i++) {
            col = startcol;
            while (col < line.length()) {
                thisClass = cls(k, line.codePointAt(col));
                if (thisClass != 0 && (i == 1 || thisClass != 1)) break;
                col += Character.charCount(line.codePointAt(col));
            }
            thisClass = col < line.length() ? cls(k, line.codePointAt(col)) : 0;
            while (col > 0 && thisClass != 0) {
                int prevcol = line.offsetByCodePoints(col, -1);
                int prevClass = cls(k, line.codePointAt(prevcol));
                // FIND_IDENT is set, so any change of class ends the text.
                if (thisClass != prevClass) break;
                col = prevcol;
            }
            if (thisClass > 2) thisClass = 2;
            if (thisClass == 2) break;
        }
        if (col >= line.length() || i == 0 && thisClass != 2) return null;
        int cls = cls(k, line.codePointAt(col));
        int end = col;
        while (end < line.length() && (i == 0 ? cls(k, line.codePointAt(end)) == cls : cls(k, line.codePointAt(end)) != 0))
            end += Character.charCount(line.codePointAt(end));
        return new int[] { col, end };
    }

    private static int cls(Keywords k, int c) { return k.charClass(c); }

    private boolean lastWrapped;

    /**
     * normal_search and do_search.
     *
     * @param dir {@code /} or {@code ?}, or 0 to use the last direction
     * @param pat the typed line (pattern, offset, {@code ;}…), or null to use the last pattern and offset
     * @param mark whether the search sets the previous-context mark (the jumplist)
     * @param reverse N: the other direction
     * @param noSmartcase for * and #, which ignore 'smartcase'
     * @return where the cursor goes, or null
     */
    Target normal(Context c, char dir, String pat, boolean mark, boolean reverse, boolean noSmartcase) {
        Buffer b = c.buffer;
        Pos pos = c.cursor();
        lastWrapped = false;
        int[] fold = c.editor.folds().closed(pos.line + 1);
        if (dir == 0) dir = direction;
        else direction = dir;
        if (reverse) dir = dir == '/' ? '?' : '/';
        // do_search: from inside a closed fold, a search starts after its end (forward) or at its start (backward).
        if (fold != null) pos = dir == '/' ? new Pos(fold[1] - 1, Integer.MAX_VALUE / 2) : new Pos(fold[0] - 1, 0);
        noHighlight = false;
        boolean end = false;
        int retval;
        String rest = pat;
        while (true) {
            String searchstr = rest;
            if (rest == null || rest.isEmpty() || rest.charAt(0) == dir) {
                if (pattern == null) throw new VimFailure("E35: No previous regular expression");
                searchstr = "";
            }
            String after = null;
            if (rest != null && !rest.isEmpty()) {
                int stop = skipPattern(rest, dir);
                searchstr = rest.substring(0, stop);
                String p = stop < rest.length() ? rest.substring(stop + 1) : "";
                boolean line = false, endOff = false;
                long off = 0;
                int i = 0;
                if (i < p.length() && (p.charAt(i) == '+' || p.charAt(i) == '-' || Character.isDigit(p.charAt(i)))) line = true;
                else if (i < p.length() && "esb".indexOf(p.charAt(i)) >= 0) {
                    endOff = p.charAt(i) == 'e';
                    i++;
                }
                if (i < p.length() && (Character.isDigit(p.charAt(i)) || p.charAt(i) == '+' || p.charAt(i) == '-')) {
                    int j = i + 1;
                    while (j < p.length() && Character.isDigit(p.charAt(j))) j++;
                    String number = p.substring(i, j);
                    if (number.equals("+")) off = 1;
                    else if (number.equals("-")) off = -1;
                    else off = Long.parseLong(number.startsWith("+") ? number.substring(1) : number);
                    i = j;
                }
                offset = new Offset(line, endOff, off);
                after = p.substring(i);
            }
            if (!searchstr.isEmpty()) pattern = searchstr;
            end = offset.end();
            // A character offset is taken off the start position, so ?pat?e+2 and /pat/s-2 do not get stuck.
            if (!offset.line() && offset.off() != 0) {
                long n = offset.off();
                if (n > 0) {
                    for (; n > 0; n--) if (!decl(b, pos)) break;
                    if (n > 0) pos = new Pos(-1, Integer.MAX_VALUE);
                } else {
                    for (; n < 0; n++) if (!incl(b, pos)) break;
                    if (n < 0) pos = new Pos(b.count(), 0);
                }
            }
            VimRegex re = compile(c, pattern, noSmartcase);
            Pos found = searchit(c, pos, dir == '/', re, c.count, end, pattern);
            if (found == null) return null;
            pos = found;
            retval = 1;
            if (offset.line()) {
                long line = pos.line + offset.off();
                pos = new Pos((int) Math.max(0, Math.min(b.count() - 1, line)), 0);
                retval = 2;
            } else {
                long n = offset.off();
                if (n > 0) {
                    for (; n > 0; n--) if (!incl(b, pos)) break;
                } else for (; n < 0; n++) if (!decl(b, pos)) break;
            }
            if (after == null || !after.startsWith(";")) break;
            if (after.length() < 2 || after.charAt(1) != '/' && after.charAt(1) != '?')
                throw new VimFailure("E386: Expected '?' or '/' after ';'");
            dir = after.charAt(1);
            rest = after.substring(2);
        }
        // do_search echoes the search ("/pattern/e"), unless wrapping gave its warning.
        if (!lastWrapped) c.editor.message(echo(dir), false);
        // check_cursor: in Normal mode and with an operator the cursor stays on a character.
        String text = b.line(pos.line);
        if (!c.inVisual() && pos.col >= text.length() && !text.isEmpty()) pos = new Pos(pos.line, Text.last(text));
        Target.Kind kind = retval == 2 ? Target.Kind.LINEWISE : end ? Target.Kind.INCLUSIVE : Target.Kind.EXCLUSIVE;
        Target target = new Target(pos, kind, Target.Column.RESET, mark, true, true);
        return target;
    }

    /** @return the search as do_search echoes it: the pattern and any offset */
    private String echo(char dir) {
        StringBuilder out = new StringBuilder().append(dir).append(pattern);
        if (offset.line() || offset.end() || offset.off() != 0) {
            out.append(dir);
            if (offset.end()) out.append('e');
            else if (!offset.line()) out.append('s');
            if (offset.off() != 0 || offset.line()) out.append(offset.off() >= 0 ? "+" : "").append(offset.off());
        }
        return out.toString();
    }

    /** skip_regexp: the end of a pattern typed after {@code /} or {@code ?}, skipping brackets and backslashes. */
    static int skipPattern(String s, char delim) {
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == delim) return i;
            if (ch == '[') {
                int close = s.indexOf(']', i + 2);
                if (close > 0) {
                    i = close + 1;
                    continue;
                }
            }
            if (ch == '\\' && i + 1 < s.length()) i++;
            i++;
        }
        return s.length();
    }

    VimRegex compile(Context c, String pat, boolean noSmartcase) {
        Options o = c.options;
        try {
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase") && !noSmartcase,
                o.keywords().javaClass(), null));
        } catch (IllegalArgumentException bad) {
            throw new VimFailure(bad.getMessage());
        }
    }

    /**
     * searchit: the {@code count}-th match from {@code start}, forward or backward, wrapping with 'wrapscan'.
     *
     * @param atEnd the 'e' offset: the match's last character counts, and the result is it
     * @return the match position (its start, or its last character with {@code atEnd}), or null with a message
     */
    Pos searchit(Context c, Pos start, boolean forward, VimRegex re, int count, boolean atEnd, String shown) {
        Buffer b = c.buffer;
        String text = b.text(0, b.length());
        Matcher m = re.pattern().matcher(text);
        Pos pos = start.copy();
        boolean wrapscan = c.options.flag("wrapscan");
        int lastLine = b.count() - 1;
        boolean found = false;
        int lnum = 0;
        do {
            int startCharLen;
            if (pos.col == Integer.MAX_VALUE) startCharLen = 0;
            else if (pos.line >= 0 && pos.line <= lastLine) {
                String l = b.line(pos.line);
                startCharLen = pos.col >= l.length() ? 1 : Character.charCount(l.codePointAt(pos.col));
            } else startCharLen = 1;
            int extraCol = forward ? startCharLen : 0;
            Pos startPos = pos.copy();
            found = false;
            boolean atFirstLine = true;
            if (pos.line < 0) {
                pos = new Pos(0, 0);
                atFirstLine = false;
            }
            if (!forward && startPos.col == 0) {
                lnum = pos.line - 1;
                atFirstLine = false;
            } else lnum = pos.line;
            if (pos.line > lastLine) atFirstLine = false;
            for (int loop = 0; loop <= 1; loop++) {
                for (; lnum >= 0 && lnum <= lastLine; lnum += forward ? 1 : -1, atFirstLine = false) {
                    Match match = forward ? forwardIn(b, m, re, lnum, atFirstLine, startPos, extraCol, atEnd)
                        : backwardIn(b, m, re, lnum, loop > 0, startPos, extraCol, atEnd);
                    if (match != null) {
                        if (atEnd && !match.start().equals(match.end())) {
                            Pos e = match.end().copy();
                            if (e.col == 0) {
                                if (e.line > 0) {
                                    e.line--;
                                    e.col = b.line(e.line).length();
                                }
                            } else e.col = Text.previous(b.line(e.line), e.col);
                            pos = e;
                        } else pos = match.start().copy();
                        found = true;
                        break;
                    }
                    if (loop > 0 && lnum == startPos.line) break;
                }
                if (!wrapscan || found || loop > 0) break;
                lnum = forward ? 0 : lastLine;
                lastWrapped = true;
                c.editor.message(forward ? "search hit BOTTOM, continuing at TOP" : "search hit TOP, continuing at BOTTOM", true);
            }
        } while (--count > 0 && found);
        if (!found) {
            if (wrapscan) throw new VimFailure("E486: Pattern not found: " + shown);
            if (lnum < 0) throw new VimFailure("E384: Search hit TOP without match for: " + shown);
            throw new VimFailure("E385: Search hit BOTTOM without match for: " + shown);
        }
        if (pos.line > lastLine) {
            String l = b.line(lastLine);
            pos = new Pos(lastLine, l.isEmpty() ? 0 : Text.last(l));
        }
        return pos;
    }

    /** The first match attempted at or after {@code col} of line {@code lnum}; null when no attempt there matches. */
    private static Matcher attempt(Buffer b, Matcher m, int lnum, int col) {
        int from = b.offset(lnum, col), lineEnd = b.end(lnum);
        if (from > b.length()) return null;
        if (!m.find(from) || m.start() > lineEnd) return null;
        // The text's final line break ends the last line; nothing starts after it.
        if (m.start() >= b.length() && b.length() > 0) return null;
        return m;
    }

    private static Match match(Buffer b, VimRegex re, Matcher m) {
        int s = Math.min(re.start(m), b.length()), e = Math.min(re.end(m), b.length());
        return new Match(position(b, s), position(b, Math.max(s, e)));
    }

    /** An offset as a position; the offset after the final line break is the end of the last line. */
    private static Pos position(Buffer b, int offset) {
        if (offset >= b.length() && b.length() > 0) {
            int last = b.count() - 1;
            return new Pos(last, b.line(last).length());
        }
        return b.pos(offset);
    }

    private static Match forwardIn(Buffer b, Matcher m, VimRegex re, int lnum, boolean atFirstLine, Pos startPos, int extraCol, boolean atEnd) {
        Matcher found = attempt(b, m, lnum, 0);
        if (found == null) return null;
        Match match = match(b, re, found);
        if (!atFirstLine) return match;
        boolean firstMatch = true;
        while (match.start().line == lnum && (atEnd && firstMatch
            ? endCol(b, match) - 1 < startPos.col + extraCol
            : match.start().col - (match.start().col >= b.line(lnum).length() ? 1 : 0) < startPos.col + extraCol)) {
            // Try again one character after where this attempt started.
            int matchcol = found.start() - b.start(lnum);
            String line = b.line(lnum);
            if (matchcol < line.length()) matchcol += Character.charCount(line.codePointAt(matchcol));
            else return null;
            found = attempt(b, m, lnum, matchcol);
            if (found == null) return null;
            match = match(b, re, found);
        }
        return match;
    }

    /** The end column of a match on its start line, or a large value when it ends on a later line. */
    private static int endCol(Buffer b, Match match) {
        return match.end().line == match.start().line ? match.end().col : Integer.MAX_VALUE / 2;
    }

    private static Match backwardIn(Buffer b, Matcher m, VimRegex re, int lnum, boolean loop, Pos startPos, int extraCol, boolean atEnd) {
        Matcher found = attempt(b, m, lnum, 0);
        if (found == null) return null;
        Match best = null;
        Match match = match(b, re, found);
        String line = b.line(lnum);
        while (true) {
            boolean ok;
            if (loop) ok = true;
            else if (atEnd) ok = match.end().line < startPos.line || match.end().line == startPos.line && match.end().col - 1 < startPos.col + extraCol;
            else ok = match.start().line < startPos.line || match.start().line == startPos.line && match.start().col < startPos.col + extraCol;
            if (!ok) break;
            best = match;
            if (match.start().line != lnum) break;
            int matchcol = match.start().col;
            if (matchcol < line.length()) matchcol += Character.charCount(line.codePointAt(matchcol));
            else break;
            found = attempt(b, m, lnum, matchcol);
            if (found == null) break;
            match = match(b, re, found);
        }
        return best;
    }

    /** incl: one character forward, onto the next line's start after the last; false at the end of the text. */
    static boolean incl(Buffer b, Pos p) {
        String line = b.line(p.line);
        if (p.col < line.length()) {
            p.col += Character.charCount(line.codePointAt(p.col));
            if (p.col < line.length()) return true;
        }
        if (p.line < b.count() - 1) {
            p.line++;
            p.col = 0;
            return true;
        }
        return false;
    }

    /** decl: one character back, onto the previous line's end; false at the start of the text. */
    static boolean decl(Buffer b, Pos p) {
        if (p.col == Integer.MAX_VALUE) p.col = b.line(p.line).length();
        if (p.col > 0) {
            String line = b.line(p.line);
            p.col = Math.min(p.col, line.length());
            if (p.col > 0) {
                p.col = Text.previous(line, p.col);
                return true;
            }
        }
        if (p.line > 0) {
            p.line--;
            p.col = b.line(p.line).length();
            return true;
        }
        return false;
    }

    /** @return every match in the text, for 'hlsearch'; empty when there is no pattern or it is invalid */
    List<Match> all(VimEditor editor) {
        if (pattern == null || noHighlight) return List.of();
        VimRegex re = quietly(editor, pattern);
        return re == null ? List.of() : matches(editor.buffer(), re);
    }

    /**
     * 'incsearch': the match a search for the typed line would find from the cursor, without moving it, changing
     * the last pattern or giving messages.
     *
     * @return the match, or null
     */
    Match preview(VimEditor editor, char type, String line) {
        String pat = line.substring(0, skipPattern(line, type));
        if (pat.isEmpty()) pat = pattern;
        if (pat == null) return null;
        VimRegex re = quietly(editor, pat);
        if (re == null) return null;
        List<Match> found = matches(editor.buffer(), re);
        if (found.isEmpty()) return null;
        Pos cursor = editor.cursor();
        boolean wrap = editor.options().flag("wrapscan");
        if (type == '/') {
            for (Match m : found) if (m.start().compareTo(cursor) > 0) return m;
            return wrap ? found.getFirst() : null;
        }
        for (int i = found.size() - 1; i >= 0; i--) if (found.get(i).start().compareTo(cursor) < 0) return found.get(i);
        return wrap ? found.getLast() : null;
    }

    private static VimRegex quietly(VimEditor editor, String pat) {
        try {
            Options o = editor.options();
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase"), o.keywords().javaClass(), null));
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    private static List<Match> matches(Buffer b, VimRegex re) {
        List<Match> out = new ArrayList<>();
        Matcher m = re.pattern().matcher(b.text(0, b.length()));
        int from = 0;
        while (from <= b.length() && m.find(from) && out.size() < 10_000) {
            if (m.start() >= b.length() && b.length() > 0) break;
            out.add(match(b, re, m));
            from = m.end() > m.start() ? m.end() : m.start() + 1;
        }
        return out;
    }
}
