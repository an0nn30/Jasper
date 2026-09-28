package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code gq} and {@code gw} (spec 4.1): Vim's internal formatter, ported from textformat.c (format_lines,
 * internal_format, fmt_check_par, same_leader), misc1.c (get_leader_len) and change.c (open_line's comment leader).
 * Paragraphs are joined and broken at 'textwidth' (79 when it is 0); with {@code q} in 'formatoptions' comment
 * leaders from 'comments' are kept, as with Vim's default {@code tcq}.
 */
final class Format {
    private final Context c;
    private final Buffer b;
    private final int tw, ts;
    private final boolean doComments, autoindent, expandtab;
    private final List<String[]> comments = new ArrayList<>();

    private Format(Context c) {
        this.c = c;
        this.b = c.buffer;
        int width = c.options.number("textwidth");
        this.tw = width > 0 ? width : 79;
        this.ts = c.tabstop();
        this.doComments = c.options.string("formatoptions").contains("q");
        this.autoindent = c.options.flag("autoindent");
        this.expandtab = c.options.flag("expandtab");
        for (String part : c.options.string("comments").split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) continue;
            comments.add(new String[] { part.substring(0, colon), part.substring(colon + 1) });
        }
    }

    static void register(VimCommands commands) {
        commands.operator("gq", "format the lines to 'textwidth', the cursor on the last formatted line", (c, span) -> op(c, span, false));
        commands.operator("gw", "format the lines to 'textwidth', keeping the cursor", (c, span) -> op(c, span, true));
    }

    /** op_format. */
    static void op(Context c, Span span, boolean keepCursor) {
        Pos saved = c.cursorStart;
        Format f = new Format(c);
        c.editor.setCursor(span.start(), true);
        c.editor.marks().set('[', span.start());
        f.formatLines(span.start().line, span.lineCount());
        Pos p = c.cursor();
        if (span.endAdjusted() && p.line < c.buffer.count() - 1) p = new Pos(p.line + 1, 0);
        p = new Pos(p.line, Text.firstNonBlank(c.buffer.line(p.line)));
        c.editor.setCursor(p);
        c.editor.marks().set(']', p);
        if (keepCursor && saved != null) c.editor.setCursor(saved);
    }

    // --- 'comments' (misc1.c's get_leader_len) ---

    /** A leader found at the start of a line: its length (with the white space after it) and its 'comments' part. */
    private record Leader(int length, int part) {
        static final Leader NONE = new Leader(0, -1);
    }

    private Leader leader(String line) {
        if (!doComments) return Leader.NONE;
        int i = 0, result = 0, part = -1;
        boolean gotCom = false;
        while (i < line.length() && Block.white(line.charAt(i))) i++;
        while (i < line.length()) {
            boolean found = false;
            int middleMatch = 0, savedPart = -1, matched = -1;
            for (int k = 0; k < comments.size(); k++) {
                String flags = comments.get(k)[0], string = comments.get(k)[1];
                if (middleMatch != 0 && flags.indexOf('m') < 0 && flags.indexOf('e') < 0) break;
                if (gotCom && flags.indexOf('n') < 0) continue;
                if (!string.isEmpty() && Block.white(string.charAt(0))) {
                    if (i == 0 || !Block.white(line.charAt(i - 1))) continue;
                    string = string.strip();
                }
                if (!line.startsWith(string, i)) continue;
                int j = string.length();
                if (flags.indexOf('b') >= 0 && i + j < line.length() && !Block.white(line.charAt(i + j))) continue;
                if (flags.indexOf('m') >= 0) {
                    if (middleMatch == 0) {
                        middleMatch = j;
                        savedPart = k;
                    }
                    continue;
                }
                if (middleMatch != 0 && j > middleMatch) middleMatch = 0;
                if (middleMatch == 0) i += j;
                found = true;
                matched = k;
                break;
            }
            if (middleMatch != 0) {
                if (!gotCom) matched = savedPart;
                i += middleMatch;
                found = true;
            }
            if (!found) break;
            if (!gotCom) part = matched;
            while (i < line.length() && Block.white(line.charAt(i))) i++;
            result = i;
            gotCom = true;
            if (matched < 0 || comments.get(matched)[0].indexOf('n') < 0) break;
        }
        return result == 0 ? Leader.NONE : new Leader(result, part);
    }

    private String flags(Leader l) { return l.part() < 0 ? "" : comments.get(l.part())[0]; }

    /** fmt_check_par: a blank line, a line holding only a leader, a comment end, or a paragraph macro is not text. */
    private boolean notParagraph(int line, Leader l) {
        String text = b.line(line);
        return Text.blank(text.substring(l.length())) || l.length() > 0 && flags(l).indexOf('e') >= 0
            || Motions.startPS(b, line, 0, false) && !text.isEmpty() && text.charAt(0) == '.';
    }

    /** same_leader. */
    private boolean sameLeader(int line, Leader l1, Leader l2) {
        if (l1.length() == 0) return l2.length() == 0;
        String f1 = flags(l1);
        if (f1.indexOf('f') >= 0) return l2.length() == 0;
        if (f1.indexOf('e') >= 0) return false;
        if (f1.indexOf('s') >= 0) {
            if (b.line(line).length() <= l1.length()) return false;
            return l2.length() != 0 && flags(l2).indexOf('m') >= 0;
        }
        String line1 = b.line(line), line2 = b.line(line + 1);
        int idx1 = 0, idx2;
        while (idx1 < line1.length() && Block.white(line1.charAt(idx1))) idx1++;
        for (idx2 = 0; idx2 < l2.length(); idx2++) {
            if (!Block.white(line2.charAt(idx2))) {
                if (idx1 >= line1.length() || line1.charAt(idx1++) != line2.charAt(idx2)) break;
            } else while (idx1 < line1.length() && Block.white(line1.charAt(idx1))) idx1++;
        }
        return idx2 == l2.length() && idx1 == l1.length();
    }

    // --- format_lines ---

    private void formatLines(int firstLine, int lineCount) {
        int maxLen = tw * 3;
        int lnum = firstLine;
        Leader leader = Leader.NONE, next;
        boolean isNotPar = lnum > 0 ? notParagraph(lnum - 1, leader = leader(b.line(lnum - 1))) : true;
        next = leader(b.line(lnum));
        boolean nextIsNotPar = notParagraph(lnum, next);
        boolean isEndPar = isNotPar || nextIsNotPar;
        boolean advance = true, needSetIndent = true, forceFormat = false;
        lnum--;
        for (int count = lineCount; count != 0; count--) {
            if (advance) {
                lnum++;
                isNotPar = nextIsNotPar;
                leader = next;
            }
            if (count == 1 || lnum == b.count() - 1) {
                nextIsNotPar = true;
                next = Leader.NONE;
            } else {
                next = leader(b.line(lnum + 1));
                nextIsNotPar = notParagraph(lnum + 1, next);
            }
            advance = true;
            isEndPar = isNotPar || nextIsNotPar;
            if (isNotPar) continue;
            if (lnum >= b.count() - 1 || !sameLeader(lnum, leader, next)) isEndPar = true;
            if (isEndPar || forceFormat) {
                if (needSetIndent) setIndent(lnum, Text.indent(b.line(lnum), ts));
                String line = b.line(lnum);
                int col = line.isEmpty() ? 0 : Text.last(line);
                while (col > 0 && Character.isWhitespace(line.charAt(col))) col = Text.previous(line, col);
                c.editor.setCursor(new Pos(lnum, col), true);
                lnum = internalFormat(lnum);
                needSetIndent = isEndPar;
                forceFormat = false;
            }
            if (!isEndPar) {
                advance = false;
                if (next.length() > 0) b.remove(b.start(lnum + 1), b.start(lnum + 1) + next.length());
                NormalCommands.joinLines(c, lnum, 2, true);
                forceFormat = b.line(lnum).length() > maxLen;
            }
        }
        c.editor.setCursor(new Pos(Math.max(0, Math.min(lnum, b.count() - 1)), c.cursor().col), true);
    }

    /** set_indent with SIN_CHANGED: the indent is rebuilt for 'expandtab' when it differs. */
    private void setIndent(int line, int width) {
        String text = b.line(line);
        int body = Text.firstNonBlank(text);
        String indent = Text.makeIndent(width, ts, expandtab);
        if (!text.substring(0, body).equals(indent)) b.setLine(line, indent + text.substring(body));
    }

    /**
     * internal_format with the cursor on the last non-blank of line {@code lnum}: breaks the line at the last white
     * space before 'textwidth', again on the new line, until it fits.
     *
     * @return the line the cursor ends on
     */
    private int internalFormat(int lnum) {
        boolean noLeader = false, comments = doComments;
        while (true) {
            Pos cursor = c.cursor();
            String line = b.line(cursor.line);
            int virtcol = Text.virtcol(line, cursor.col, ts) + (cursor.col < line.length() ? Text.width(line, cursor.col, Text.virtcol(line, cursor.col, ts), ts) : 1);
            if (virtcol <= tw) break;
            if (noLeader) comments = false;
            Leader leader = comments ? leader(line) : Leader.NONE;
            if (leader.length() == 0) noLeader = true;
            int startcol = cursor.col;
            if (startcol == 0) break;
            int wantcol = Text.columnAt(line, tw, ts);
            int col = startcol, foundcol = 0;
            while (true) {
                char cc = col < line.length() ? line.charAt(col) : 0;
                if (cc == ' ' || cc == '\t') {
                    while (col > 0 && (cc == ' ' || cc == '\t')) {
                        col = Text.previous(line, col);
                        cc = line.charAt(col);
                    }
                    if (col == 0 && (cc == ' ' || cc == '\t')) break;
                    if (col < leader.length()) break;
                    col = Text.next(line, col);
                    foundcol = col;
                    if (col <= wantcol) break;
                }
                if (col == 0) break;
                col = Text.previous(line, col);
            }
            if (foundcol == 0) {
                c.editor.setCursor(new Pos(cursor.line, startcol), true);
                break;
            }
            int wordStart = foundcol;
            while (wordStart < line.length() && Block.white(line.charAt(wordStart))) wordStart++;
            startcol = Math.max(0, startcol - wordStart);
            String prefix = newLinePrefix(line, comments ? leader : Leader.NONE);
            String head = line.substring(0, foundcol).stripTrailing();
            String tail = line.substring(wordStart);
            // open_line: the line is split at the break; its trailing white space goes, and the new line gets the
            // comment leader or the indent.
            b.replace(b.start(cursor.line) + head.length(), b.start(cursor.line) + wordStart, "\n" + prefix);
            int newLine = cursor.line + 1;
            int newCol = Math.min(prefix.length() + startcol, b.line(newLine).length());
            c.editor.setCursor(new Pos(newLine, newCol), true);
            lnum = newLine;
            if (tail.isEmpty()) break;
        }
        return c.cursor().line;
    }

    /** open_line's comment leader (and indent with 'autoindent') for the line after a break. */
    private String newLinePrefix(String line, Leader l) {
        int newindent = autoindent ? Text.indent(line, ts) : 0;
        if (l.length() == 0) return Text.makeIndent(newindent, ts, expandtab);
        String flags = flags(l);
        String leader = line.substring(0, l.length());
        String repl = null;
        boolean extraSpace = false, requireBlank = flags.indexOf('b') >= 0;
        int off = 0;
        char adjust = 0;
        for (int i = 0; i < flags.length(); i++) {
            char f = flags.charAt(i);
            if (f == 'r' || f == 'l') adjust = f;
            else if (Character.isDigit(f) || f == '-') {
                int j = i + 1;
                while (j < flags.length() && Character.isDigit(flags.charAt(j))) j++;
                off = Integer.parseInt(flags.substring(i, j));
                i = j - 1;
            }
        }
        if (flags.indexOf('s') >= 0 || flags.indexOf('m') >= 0) {
            int k = l.part();
            if (flags.indexOf('s') >= 0) {
                String middle = k + 1 < comments.size() ? comments.get(k + 1)[1] : "";
                repl = middle;
                requireBlank = k + 1 < comments.size() && comments.get(k + 1)[0].indexOf('b') >= 0;
            }
            if (!Block.white(leader.charAt(leader.length() - 1)) && requireBlank) extraSpace = true;
        } else if (flags.indexOf('f') >= 0) repl = "";
        if (repl != null) {
            int start = 0;
            while (start < leader.length() && Block.white(leader.charAt(start))) start++;
            StringBuilder p = new StringBuilder(leader);
            if (adjust == 'r') {
                int end = leader.length();
                while (end > start && Block.white(leader.charAt(end - 1))) end--;
                int from = Math.max(start, end - repl.length());
                p.replace(from, end, repl);
                for (int i = 0; i < from; i++) if (!Block.white(p.charAt(i))) p.setCharAt(i, ' ');
            } else {
                int fit = 0;
                String rest = leader.substring(start);
                while (fit < rest.length() && fit + 1 <= repl.length()) fit++;
                p.replace(start, start + fit, repl);
                for (int i = start + repl.length(); i < p.length(); i++) if (!Block.white(p.charAt(i))) p.setCharAt(i, ' ');
            }
            leader = p.toString();
            if (autoindent) newindent = Text.indent(leader, ts);
            if (newindent + off < 0) {
                off = -newindent;
                newindent = 0;
            } else newindent += off;
            while (off > 0 && !leader.isEmpty() && leader.charAt(leader.length() - 1) == ' ' && leader.strip().indexOf('\t') < 0) {
                leader = leader.substring(0, leader.length() - 1);
                off--;
            }
            if (!leader.isEmpty() && Block.white(leader.charAt(leader.length() - 1))) extraSpace = false;
        }
        if (extraSpace) leader += " ";
        if (newindent > 0) return Text.makeIndent(newindent, ts, expandtab) + leader.stripLeading();
        return leader;
    }
}
