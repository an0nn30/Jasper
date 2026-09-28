package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.List;
import java.util.regex.Matcher;

/**
 * {@code :s}, {@code :&}, {@code :&&} and {@code :~}: a port of Vim's ex_substitute (ex_cmds.c) with vim_regsub and
 * regtilde (regexp.c). The pattern, the substitute string and the flags are remembered for the next {@code :s}
 * without them. Confirmation ({@code c}) asks before each substitution; the substitution waits for the answer and
 * then goes on where it stopped.
 *
 * <p>Lines are matched as Vim matches them: a pattern that cannot span lines against the line alone, one that can
 * against the whole text. Until a line is done its text in the buffer is the original, so later matches in it are
 * found in the original text, as in Vim.
 */
final class ExSubstitute {
    private ExSubstitute() { }

    /** CAR: in a substitute string it breaks the line. */
    private static final char CAR = '\r';

    /** ex_substitute. */
    static void substitute(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        Search search = editor.vim().search();
        String arg = cmd.arg;
        if (!ex.globalBusy()) {
            ex.subNsubs = 0;
            ex.subNlines = 0;
        }
        long startNsubs = ex.subNsubs;
        int whichPat = cmd.name().equals("~") ? -1 : Search.SUBSTITUTE;
        String pat = null, sub;
        int i = 0;
        boolean endcolumn = false;
        char first = arg.isEmpty() ? 0 : arg.charAt(0);
        if (cmd.typed.startsWith("s") && first != 0 && !Ex.isWhite(first) && "0123456789cegriIp|\"".indexOf(first) < 0) {
            if (first < 128 && Character.isLetter(first)) throw new VimFailure("E146: Regular expressions can't be delimited by letters");
            char delimiter;
            if (first == '\\') {
                i++;
                char which = i < arg.length() ? arg.charAt(i) : 0;
                if (which == 0 || "/?&".indexOf(which) < 0) throw new VimFailure("E10: \\ should be followed by /, ? or &");
                if (which != '&') whichPat = Search.SEARCH;
                pat = "";
                delimiter = which;
                i++;
            } else {
                whichPat = -1;
                delimiter = first;
                i++;
                int end = i + Search.skipPattern(arg.substring(i), delimiter);
                pat = arg.substring(i, end);
                i = end < arg.length() ? end + 1 : end;
            }
            StringBuilder s = new StringBuilder();
            while (i < arg.length()) {
                char ch = arg.charAt(i);
                if (ch == delimiter) {
                    i++;
                    break;
                }
                if (ch == '\\' && i + 1 < arg.length()) {
                    s.append(ch);
                    i++;
                }
                s.append(arg.charAt(i++));
            }
            sub = s.toString();
            search.oldSub = sub;
        } else {
            if (search.oldSub == null) throw new VimFailure("E33: No previous substitute regular expression");
            sub = search.oldSub;
            endcolumn = editor.curswant() == Integer.MAX_VALUE;
        }
        String rest = arg.substring(Math.min(i, arg.length()));
        // ":s/\n//" joins lines.
        if (pat != null && pat.equals("\\n") && sub.isEmpty()
            && (rest.isEmpty() || rest.length() == 1 && "glp#".indexOf(rest.charAt(0)) >= 0)) {
            join(cmd, rest);
            search.save(Search.SUBSTITUTE, pat);
            search.remember(pat);
            return;
        }
        if (rest.startsWith("&")) rest = rest.substring(1);
        else {
            // 'gdefault' makes g the default; a g flag then turns it off.
            search.subAll = editor.options().flag("gdefault");
            search.subAsk = false;
            search.subError = true;
            search.subPrint = search.subList = search.subCount = search.subNumber = false;
            search.subIgnoreCase = 0;
        }
        int f = 0;
        for (; f < rest.length(); f++) {
            char ch = rest.charAt(f);
            switch (ch) {
                case 'g' -> search.subAll = !search.subAll;
                case 'c' -> search.subAsk = !search.subAsk;
                case 'n' -> search.subCount = true;
                case 'e' -> search.subError = !search.subError;
                case 'r' -> whichPat = -1;
                case 'p' -> search.subPrint = true;
                case '#' -> search.subPrint = search.subNumber = true;
                case 'l' -> search.subPrint = search.subList = true;
                case 'i', 'I' -> search.subIgnoreCase = ch;
                default -> { }
            }
            if ("gcnerp#liI".indexOf(ch) < 0) break;
        }
        rest = rest.substring(f);
        if (search.subCount) search.subAsk = false;
        boolean saveAll = search.subAll, saveAsk = search.subAsk;
        rest = rest.substring(Ex.skipWhite(rest, 0));
        if (!rest.isEmpty() && Character.isDigit(rest.charAt(0))) {
            int d = 0;
            long count = 0;
            while (d < rest.length() && Character.isDigit(rest.charAt(d))) count = Math.min(Integer.MAX_VALUE, count * 10 + rest.charAt(d++) - '0');
            rest = rest.substring(d);
            if (count <= 0 && search.subError) throw new VimFailure("E939: Positive count required");
            cmd.line1 = cmd.line2;
            cmd.line2 = (int) Math.min(editor.buffer().count(), cmd.line2 + count - 1);
        }
        rest = rest.substring(Ex.skipWhite(rest, 0));
        if (!rest.isEmpty() && rest.charAt(0) != '"') {
            if (rest.charAt(0) == '|') cmd.nextcmd = rest.substring(1);
            else throw new VimFailure("E488: Trailing characters: " + rest);
        }
        VimRegex regex;
        String used;
        try {
            used = search.choose(pat, Search.SUBSTITUTE, whichPat);
            Options o = editor.options();
            boolean ic = search.subIgnoreCase == 'i' ? true : search.subIgnoreCase != 'I' && o.flag("ignorecase");
            boolean scs = search.subIgnoreCase == 0 && o.flag("smartcase");
            regex = VimRegex.compile(used, new VimRegex.Settings(ic, scs, o.keywords().javaClass(), search.prevSub));
        } catch (VimFailure | IllegalArgumentException bad) {
            if (search.subError) {
                editor.message(bad.getMessage(), true);
                throw new VimFailure("E476: Invalid command");
            }
            return;
        }
        if (sub.startsWith("\\=")) throw new VimFailure("E319: Sorry, the command is not available in this version");
        sub = regtilde(search, sub);
        new Run(cmd, regex, used, sub, startNsubs, endcolumn, saveAll, saveAsk).start();
    }

    /** The {@code :%s/\n//} shortcut: the range's lines and the next one are joined, without spaces. */
    private static void join(ExCommand cmd, String flags) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        editor.setCursor(new Pos(cmd.line1 - 1, 0));
        if (flags.equals("l")) cmd.flags = Ex.LIST;
        else if (flags.equals("#")) cmd.flags = Ex.NUMBER;
        else if (flags.equals("p")) cmd.flags = Ex.PRINT;
        int joined = cmd.line2 - cmd.line1 + 1;
        if (cmd.line2 < editor.buffer().count()) joined++;
        if (joined > 1) {
            NormalCommands.joinLines(cmd.context(), cmd.line1 - 1, joined, false);
            ex.subNsubs = joined - 1;
            ex.subNlines = 1;
            message(editor, false);
            ExInfo.mayPrint(cmd);
        }
    }

    /**
     * regtilde: {@code ~} in the substitute string stands for the previous one; the result is remembered as the
     * previous one (reg_prev_sub), for the next {@code ~}.
     */
    private static String regtilde(Search search, String source) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '~') {
                if (search.prevSub != null) out.append(search.prevSub);
                continue;
            }
            if (c == '\\' && i + 1 < source.length()) {
                out.append(c);
                c = source.charAt(++i);
            }
            out.append(c);
        }
        String result = out.toString();
        search.prevSub = result.isEmpty() ? null : result;
        return result;
    }

    /**
     * do_sub_msg: "3 substitutions on 2 lines" (or "matches" for {@code n}) when more than 'report' substitutions
     * were made and the command was typed or changed more than one line.
     *
     * @return whether it gave the message
     */
    static boolean message(VimEditor editor, boolean countOnly) {
        Ex ex = editor.ex();
        long nsubs = ex.subNsubs, nlines = ex.subNlines;
        int report = editor.options().number("report");
        if (!(nsubs > report && (ex.typed() || nlines > 1 || report < 1) || countOnly)) return false;
        String what = countOnly ? (nsubs == 1 ? " match" : " matches") : (nsubs == 1 ? " substitution" : " substitutions");
        editor.say(nsubs + what + " on " + nlines + (nlines == 1 ? " line" : " lines"));
        return true;
    }

    /**
     * vim_regsub with REGSUB_BACKSLASH and REGSUB_MAGIC: {@code &} and {@code \0}–{@code \9} are the match and its
     * groups, <code>&#92;u &#92;U &#92;l &#92;L &#92;e &#92;E</code> change case, {@code \r} and a CR break the line, {@code \n} is a NUL and
     * {@code \t} a tab. Other backslashes stay, to be removed afterwards; a CR or backslash from the match is
     * escaped so it stays as it is, and a line break inside a group becomes a CR.
     */
    static String regsub(String source, java.util.function.IntFunction<String> group) {
        StringBuilder dst = new StringBuilder();
        char funcOne = 0, funcAll = 0;
        int n = source.length();
        for (int i = 0; i < n; ) {
            int c = source.codePointAt(i);
            i += Character.charCount(c);
            int no = -1;
            if (c == '&') no = 0;
            else if (c == '\\' && i < n) {
                char next = source.charAt(i);
                if (next >= '0' && next <= '9') {
                    no = next - '0';
                    i++;
                } else if ("uUlLeE".indexOf(next) >= 0) {
                    i++;
                    switch (next) {
                        case 'u' -> funcOne = 'u';
                        case 'U' -> funcAll = 'u';
                        case 'l' -> funcOne = 'l';
                        case 'L' -> funcAll = 'l';
                        default -> funcOne = funcAll = 0;
                    }
                    continue;
                }
            }
            if (no < 0) {
                if (c == '\\' && i < n) {
                    char next = source.charAt(i);
                    switch (next) {
                        case 'r' -> { c = CAR; i++; }
                        case 'n' -> { c = 0; i++; }
                        case 't' -> { c = '\t'; i++; }
                        case 'b' -> { c = 8; i++; }
                        default -> {
                            dst.append('\\');
                            c = source.codePointAt(i);
                            i += Character.charCount(c);
                        }
                    }
                }
                if (funcOne != 0) {
                    c = apply(funcOne, c);
                    funcOne = 0;
                } else if (funcAll != 0) c = apply(funcAll, c);
                dst.appendCodePoint(c);
            } else {
                String text = group.apply(no);
                if (text == null) continue;
                for (int k = 0; k < text.length(); ) {
                    int g = text.codePointAt(k);
                    k += Character.charCount(g);
                    if (g == '\n') dst.append(CAR);
                    else if (g == CAR || g == '\\') dst.append('\\').appendCodePoint(g);
                    else {
                        if (funcOne != 0) {
                            g = apply(funcOne, g);
                            funcOne = 0;
                        } else if (funcAll != 0) g = apply(funcAll, g);
                        dst.appendCodePoint(g);
                    }
                }
            }
        }
        return dst.toString();
    }

    private static int apply(char func, int c) { return func == 'u' ? Character.toUpperCase(c) : Character.toLowerCase(c); }

    /** One {@code :s} over its range: the state of ex_substitute's loops, so a confirmation can pause them. */
    private static final class Run {
        private final ExCommand cmd;
        private final VimEditor editor;
        private final Ex ex;
        private final Search search;
        private final Buffer b;
        private final VimRegex regex;
        private final String used, sub;
        private final long startNsubs;
        private final boolean endcolumn, saveAll, saveAsk;
        private final Pos oldCursor;
        private int line2, lnum;
        private boolean gotQuit, gotMatch;
        /** Inside a line's loop, and its state (Vim's names). */
        private boolean inLine;
        private int subFirstlnum, copycol, matchcol, prevMatchcol, nmatch, nmatchTl;
        private String subFirstline;
        private StringBuilder newStart;
        private boolean didSub, doAgain, skipMatch;
        private int cursorLine, cursorCol;
        /** The last match: its start and end, lines relative to where the search started. */
        private int startLine, startCol, endLine, endCol;
        private String[] groups = new String[10];
        /** For a pattern that can span lines: the text it is matched against, rebuilt after a change. */
        private String text;
        private int[] lineStarts;
        private int textVersion = -1, version;

        Run(ExCommand cmd, VimRegex regex, String used, String sub, long startNsubs, boolean endcolumn, boolean saveAll, boolean saveAsk) {
            this.cmd = cmd;
            this.editor = cmd.editor;
            this.ex = editor.ex();
            this.search = editor.vim().search();
            this.b = editor.buffer();
            this.regex = regex;
            this.used = used;
            this.sub = sub;
            this.startNsubs = startNsubs;
            this.endcolumn = endcolumn;
            this.saveAll = saveAll;
            this.saveAsk = saveAsk;
            this.oldCursor = editor.cursor();
            this.line2 = cmd.line2;
            this.lnum = cmd.line1;
            this.cursorLine = oldCursor.line + 1;
            this.cursorCol = oldCursor.col;
        }

        void start() { run(null); }

        /** The loops; returns when done, or with the confirmation question open. */
        private void run(KeyInput answer) {
            while (true) {
                if (!inLine) {
                    if (lnum > line2 || gotQuit) {
                        finish();
                        return;
                    }
                    nmatch = exec(lnum, 0);
                    if (nmatch == 0) {
                        lnum++;
                        continue;
                    }
                    subFirstlnum = lnum;
                    copycol = 0;
                    matchcol = 0;
                    prevMatchcol = Integer.MAX_VALUE;
                    newStart = null;
                    didSub = false;
                    nmatchTl = 0;
                    skipMatch = false;
                    subFirstline = null;
                    if (!gotMatch) {
                        editor.setCursor(new Pos(cursorLine - 1, cursorCol));
                        ex.setpcmark();
                        gotMatch = true;
                    }
                    inLine = true;
                }
                Flow flow = iteration(answer);
                answer = null;
                if (flow == Flow.ASK) return;
                if (flow == Flow.BREAK) {
                    if (didSub) ex.subNlines++;
                    inLine = false;
                    lnum++;
                }
            }
        }

        private enum Flow { NEXT, BREAK, ASK }

        /** One pass of the loop over a line's matches: 1. an empty match, 2. counting or asking, 3. substituting, 4. the next match. */
        private Flow iteration(KeyInput answer) {
            boolean toSkip = false;
            if (answer == null) {
                if (startLine > 0) {
                    lnum += startLine;
                    subFirstlnum += startLine;
                    nmatch -= startLine;
                    subFirstline = null;
                }
                if (lnum > b.count()) return Flow.BREAK;
                if (subFirstline == null) subFirstline = b.line(subFirstlnum - 1);
                cursorLine = lnum;
                doAgain = false;
                if (matchcol == prevMatchcol && endLine == 0 && matchcol == endCol) {
                    // An empty match where the last one ended does not count; look one character further.
                    if (matchcol >= subFirstline.length()) skipMatch = true;
                    else matchcol = Text.next(subFirstline, matchcol);
                    toSkip = true;
                } else {
                    matchcol = endCol;
                    prevMatchcol = matchcol;
                    if (search.subCount) {
                        if (nmatch > 1) {
                            matchcol = subFirstline.length();
                            nmatch = 1;
                            skipMatch = true;
                        }
                        ex.subNsubs++;
                        didSub = true;
                        toSkip = true;
                    } else if (search.subAsk) {
                        ask();
                        return Flow.ASK;
                    }
                }
            } else {
                int typed = answer.plainChar();
                if (typed == 'q' || answer.is("<Esc>") || answer.is("<C-c>")) gotQuit = true;
                else if (typed == 'l') {
                    search.subAll = false;
                    line2 = lnum;
                } else if (typed == 'a') search.subAsk = false;
                else if (typed != 'y' && typed != 'n') {
                    if (answer.is("<C-e>")) editor.view().scrollRedraw(true, 1);
                    else if (answer.is("<C-y>")) editor.view().scrollRedraw(false, 1);
                    ask();
                    return Flow.ASK;
                }
                editor.confirmMatch(null);
                if (typed == 'n') {
                    if (nmatch > 1) {
                        matchcol = subFirstline.length();
                        skipMatch = true;
                    }
                    toSkip = true;
                }
                if (gotQuit) toSkip = true;
            }
            if (!toSkip) substituteMatch();
            return skip();
        }

        /**
         * The question, with the match shown and the cursor on it. As in Vim (without 'u' in 'cpoptions'), what was
         * changed before it is its own undo step. The cursor column counts the substitutions already made in the line.
         */
        private void ask() {
            editor.area().vimUndo().sync();
            cursorCol = startCol;
            if (newStart != null) cursorCol += newStart.length() + subFirstline.length() - copycol - b.line(lnum - 1).length();
            editor.setCursor(new Pos(lnum - 1, Math.min(startCol, b.line(lnum - 1).length())));
            int from = b.offset(lnum - 1 + 0, Math.min(startCol, b.line(lnum - 1).length()));
            int endLineIndex = Math.min(b.count() - 1, lnum - 1 + endLine - startLine);
            int to = b.offset(endLineIndex, Math.min(endCol, b.line(endLineIndex).length()));
            editor.confirmMatch(new int[] { from, Math.max(to, from + 1) });
            editor.ask("replace with " + sub + " (y/n/a/q/l/^E/^Y)?", this::run);
        }

        /** Step 3: the substitution goes into the new line; a CR in it breaks the line there. */
        private void substituteMatch() {
            cursorCol = startCol;
            String replacement = regsub(sub, n -> groups[n]);
            if (nmatch > b.count() - subFirstlnum + 1) {
                nmatch = b.count() - subFirstlnum + 1;
                skipMatch = true;
            }
            if (nmatch > 1) nmatchTl += nmatch - 1;
            if (newStart == null) newStart = new StringBuilder();
            newStart.append(subFirstline, Math.min(copycol, subFirstline.length()), Math.min(startCol, subFirstline.length()));
            int newEnd = newStart.length();
            newStart.append(replacement);
            ex.subNsubs++;
            didSub = true;
            cursorCol = 0;
            if (nmatch > 1) {
                subFirstlnum += nmatch - 1;
                subFirstline = b.line(subFirstlnum - 1);
                if (subFirstlnum <= line2) doAgain = true;
                else search.subAll = false;
            }
            copycol = endCol;
            if (skipMatch) {
                subFirstline = "";
                copycol = 0;
            }
            for (int i = newEnd; i < newStart.length(); i++) {
                char c = newStart.charAt(i);
                if (c == '\\' && i + 1 < newStart.length()) newStart.deleteCharAt(i);
                else if (c == CAR) {
                    stepCursor();
                    b.insertLines(lnum - 1, List.of(newStart.substring(0, i)));
                    changed();
                    subFirstlnum++;
                    lnum++;
                    line2++;
                    cursorLine++;
                    newStart.delete(0, i + 1);
                    i = -1;
                }
            }
        }

        /** The "skip" part: decide whether this was the line's last match, and replace the line when it was. */
        private Flow skip() {
            boolean lastone = skipMatch || gotQuit || lnum > line2 || !(search.subAll || doAgain)
                || matchcol >= subFirstline.length() && nmatch <= 1 && !regex.multiline();
            nmatch = -1;
            boolean replace = lastone || nmatchTl > 0;
            if (!replace) {
                nmatch = exec(subFirstlnum, matchcol);
                replace = nmatch == 0 || startLine > 0;
            }
            if (replace) {
                if (newStart != null) {
                    newStart.append(subFirstline.substring(Math.min(copycol, subFirstline.length())));
                    matchcol = subFirstline.length() - matchcol;
                    prevMatchcol = subFirstline.length() - prevMatchcol;
                    stepCursor();
                    b.setLine(lnum - 1, newStart.toString());
                    changed();
                    if (nmatchTl > 0) {
                        b.deleteLines(lnum, lnum + nmatchTl - 1);
                        changed();
                        line2 -= nmatchTl;
                        nmatchTl = 0;
                    }
                    subFirstlnum = lnum;
                    subFirstline = newStart.toString();
                    newStart = null;
                    matchcol = subFirstline.length() - matchcol;
                    prevMatchcol = subFirstline.length() - prevMatchcol;
                    copycol = 0;
                }
                if (nmatch == -1 && !lastone) nmatch = exec(subFirstlnum, matchcol);
                if (nmatch <= 0) {
                    if (nmatch == -1) lnum -= startLine;
                    return Flow.BREAK;
                }
            }
            return Flow.NEXT;
        }

        /** The undo step starts with the cursor where Vim's u_save sees it: on the line, at its start. */
        private void stepCursor() { editor.area().vimUndo().stepCursor(lnum - 1, 0); }

        private void changed() { version++; }

        /**
         * vim_regexec_multi: the first match starting in line {@code line} (1-based) at or after {@code col}.
         *
         * @return how many lines the match spans (its end line minus its start line, plus one), or 0 for none
         */
        private int exec(int line, int col) {
            if (line < 1 || line > b.count()) return 0;
            String lineText = b.line(line - 1);
            if (col > lineText.length()) return 0;
            if (!regex.multiline()) {
                Matcher m = regex.pattern().matcher(lineText + "\n");
                if (!m.find(col) || m.start() > lineText.length()) return 0;
                int s = Math.min(regex.start(m), lineText.length()), e = Math.min(Math.max(regex.end(m), s), lineText.length());
                startLine = endLine = 0;
                startCol = s;
                endCol = e;
                for (int n = 0; n < 10; n++) {
                    int gs = regex.groupStart(m, n), ge = regex.groupEnd(m, n);
                    groups[n] = gs < 0 || ge < 0 ? null : (lineText + "\n").substring(gs, Math.max(gs, Math.min(ge, lineText.length())));
                }
                return 1;
            }
            if (textVersion != version || text == null) {
                String doc = b.text();
                text = doc.endsWith("\n") ? doc : doc + "\n";
                lineStarts = new int[b.count() + 1];
                for (int i = 0; i < b.count(); i++) lineStarts[i] = b.start(i);
                lineStarts[b.count()] = text.length();
                textVersion = version;
            }
            int base = lineStarts[line - 1];
            Matcher m = regex.pattern().matcher(text);
            if (!m.find(base + col) || m.start() > base + lineText.length()) return 0;
            int s = regex.start(m), e = Math.max(regex.end(m), s);
            int[] sp = position(s), ep = position(e);
            startLine = sp[0] - (line - 1);
            startCol = sp[1];
            endLine = ep[0] - (line - 1);
            endCol = ep[1];
            for (int n = 0; n < 10; n++) {
                int gs = regex.groupStart(m, n), ge = regex.groupEnd(m, n);
                groups[n] = gs < 0 || ge < 0 ? null : text.substring(gs, Math.max(gs, ge));
            }
            return endLine + 1;
        }

        /** An offset in {@link #text} as {line (0-based), column}; past the last line is the line after it. */
        private int[] position(int offset) {
            int count = lineStarts.length - 1;
            int lo = 0, hi = count;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (lineStarts[mid] <= offset) lo = mid;
                else hi = mid - 1;
            }
            if (lo >= count) return new int[] { count, 0 };
            return new int[] { lo, offset - lineStarts[lo] };
        }

        /** The end of ex_substitute: the cursor, the {@code '[ ']} marks, the message, and printing. */
        private void finish() {
            editor.confirmMatch(null);
            Pos cursor = new Pos(Math.max(0, Math.min(cursorLine, b.count()) - 1), cursorCol);
            if (search.subCount) cursor = oldCursor;
            editor.setCursor(cursor);
            if (ex.subNsubs > startNsubs) {
                Marks marks = editor.marks();
                marks.set('[', new Pos(cmd.line1 - 1, 0));
                marks.set(']', new Pos(Math.max(0, Math.min(line2, b.count()) - 1), 0));
                if (!ex.globalBusy()) {
                    if (!search.subAsk) {
                        if (endcolumn) {
                            String line = b.line(editor.cursor().line);
                            editor.setCursor(new Pos(editor.cursor().line, line.isEmpty() ? 0 : Text.last(line)));
                            editor.curswant(Integer.MAX_VALUE);
                        } else editor.ex().beginline(editor.cursor().line + 1, false);
                    }
                    message(editor, search.subCount);
                } else ex.globalNeedBeginline = true;
                if (search.subPrint)
                    editor.say(ExInfo.line(editor, editor.cursor().line + 1, search.subNumber, search.subList));
            } else if (!ex.globalBusy() && !gotMatch && search.subError) {
                search.subAll = saveAll;
                search.subAsk = saveAsk;
                throw new VimFailure("E486: Pattern not found: " + used);
            }
            search.subAll = saveAll;
            search.subAsk = saveAsk;
        }
    }
}
