package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Column;

/**
 * Normal-mode and operator-pending motions (spec 4.1), ported from Vim's normal.c and search.c so that where they
 * land, whether they fail, and how an operator treats them match Vim. A motion returns null to fail.
 */
final class Motions {
    private static final String PARAGRAPHS = "IPLPPPQPP TPHPLIPpLpItpplpipbp", SECTIONS = "SHNHH HUnhsh";

    private Motions() { }

    static void register(VimCommands commands) {
        commands.motion("h", "left", c -> left(c, false));
        commands.motion("<Left>", "left", c -> left(c, false));
        commands.motion("<BS>", "left, wrapping to the previous line ('whichwrap' b)", c -> left(c, true));
        commands.motion("<C-h>", "left, wrapping like BS", c -> left(c, true));
        commands.motion("l", "right", c -> right(c, 'l'));
        commands.motion("<Right>", "right", c -> right(c, '>'));
        commands.motion(" ", "right, wrapping to the next line ('whichwrap' s)", c -> right(c, 's'));
        commands.motion("j", "down", c -> down(c, c.count));
        commands.motion("<Down>", "down", c -> down(c, c.count));
        commands.motion("<C-j>", "down", c -> down(c, c.count));
        commands.motion("<C-n>", "down", c -> down(c, c.count));
        commands.motion("k", "up", c -> up(c, c.count));
        commands.motion("<Up>", "up", c -> up(c, c.count));
        commands.motion("<C-p>", "up", c -> up(c, c.count));
        commands.motion("+", "down to the first non-blank", c -> firstNonBlank(c, down(c, c.count)));
        commands.motion("<CR>", "down to the first non-blank", c -> firstNonBlank(c, down(c, c.count)));
        commands.motion("-", "up to the first non-blank", c -> firstNonBlank(c, up(c, c.count)));
        commands.motion("_", "count - 1 lines down, first non-blank", c -> firstNonBlank(c, c.count == 1 ? Target.linewise(c.cursor(), Column.KEEP) : down(c, c.count - 1)));
        commands.motion("G", "to the last line, or line {count}", c -> goToLine(c, c.hasCount ? c.count : c.buffer.count()));
        commands.motion("gg", "to the first line, or line {count}", c -> goToLine(c, c.hasCount ? c.count : 1));
        commands.motion("0", "to the first column", c -> Target.exclusive(new Pos(c.cursor().line, 0)));
        commands.motion("<Home>", "to the first column", c -> Target.exclusive(new Pos(c.cursor().line, 0)));
        commands.motion("^", "to the first non-blank", c -> Target.exclusive(new Pos(c.cursor().line, Text.firstNonBlank(c.buffer.line(c.cursor().line)))));
        commands.motion("$", "to the end of the line, {count} - 1 lines down", Motions::endOfLine);
        commands.motion("<End>", "to the end of the line", Motions::endOfLine);
        commands.motion("g_", "to the last non-blank, {count} - 1 lines down", Motions::lastNonBlank);
        commands.motion("|", "to display column {count}", c -> Target.exclusive(new Pos(c.cursor().line, Text.columnAt(c.buffer.line(c.cursor().line), c.count - 1, c.tabstop()))));
        commands.motion("go", "to character {count} of the text", Motions::goToOffset);
        commands.motion("w", "to the start of the next word", c -> word(c, false, false));
        commands.motion("W", "to the start of the next WORD", c -> word(c, true, false));
        commands.motion("e", "to the end of the word", c -> word(c, false, true));
        commands.motion("E", "to the end of the WORD", c -> word(c, true, true));
        commands.motion("b", "back to the start of the word", c -> backWord(c, false));
        commands.motion("B", "back to the start of the WORD", c -> backWord(c, true));
        commands.motion("ge", "back to the end of the previous word", c -> backEnd(c, false));
        commands.motion("gE", "back to the end of the previous WORD", c -> backEnd(c, true));
        commands.motionWithChar("f", "to the {count}-th {char} to the right", c -> typedFind(c, true, false));
        commands.motionWithChar("F", "to the {count}-th {char} to the left", c -> typedFind(c, false, false));
        commands.motionWithChar("t", "till before the {count}-th {char} to the right", c -> typedFind(c, true, true));
        commands.motionWithChar("T", "till after the {count}-th {char} to the left", c -> typedFind(c, false, true));
        commands.motion(";", "repeat the last f, F, t or T", c -> find(c, true, false, true, false));
        commands.motion(",", "repeat the last f, F, t or T the other way", c -> find(c, true, false, true, true));
        commands.motion("(", "back {count} sentences", c -> sentence(c, false));
        commands.motion(")", "forward {count} sentences", c -> sentence(c, true));
        commands.motion("{", "back {count} paragraphs", c -> paragraph(c, false, 0, false));
        commands.motion("}", "forward {count} paragraphs", c -> paragraph(c, true, 0, false));
        commands.motion("[[", "back to a { in column 1", c -> section(c, false, '{'));
        commands.motion("]]", "forward to a { in column 1", c -> section(c, true, '{'));
        commands.motion("[]", "back to a } in column 1", c -> section(c, false, '}'));
        commands.motion("][", "forward to a } in column 1", c -> section(c, true, '}'));
        commands.motion("%", "to the matching bracket, or to {count} percent of the text", Motions::percent);
        commands.motion("[(", "back to the unmatched (", c -> unmatched(c, '(', ')', true));
        commands.motion("[{", "back to the unmatched {", c -> unmatched(c, '{', '}', true));
        commands.motion("])", "forward to the unmatched )", c -> unmatched(c, '(', ')', false));
        commands.motion("]}", "forward to the unmatched }", c -> unmatched(c, '{', '}', false));
    }

    /** nv_left: fails only when it could not move at all and no operator waits. */
    static Target left(Context c, boolean backspace) {
        Pos p = c.cursor();
        boolean wrap = backspace && c.options.has("whichwrap", "b");
        boolean moved = false, noAdjust = false;
        for (int n = c.count; n > 0; n--) {
            if (p.col > 0) {
                p.col = Text.previous(c.buffer.line(p.line), p.col);
                moved = true;
            } else if (wrap && p.line > 0) {
                p.line--;
                String line = c.buffer.line(p.line);
                p.col = Text.last(line);
                // Deleting the line break before the first character: Vim puts the cursor past the end of the line above.
                if (("d".equals(c.operator) || "c".equals(c.operator)) && !line.isEmpty()) {
                    p.col = line.length();
                    noAdjust = true;
                }
                moved = true;
            } else break;
        }
        if (!moved && c.operator == null) return null;
        Target target = Target.exclusive(p);
        return noAdjust ? target.withoutAdjust() : target;
    }

    /** nv_right: with an operator, stopping at the last character makes the motion inclusive. */
    static Target right(Context c, char which) {
        Pos p = c.cursor();
        boolean wraps = c.options.has("whichwrap", String.valueOf(which));
        boolean inclusive = false;
        // In Visual mode l may go onto the end of the line (nv_right's past_line).
        boolean pastLine = c.inVisual();
        for (int n = c.count; n > 0; n--) {
            String line = c.buffer.line(p.line);
            int next = Text.next(line, p.col);
            if (p.col < line.length() && (next < line.length() || pastLine)) {
                p.col = next;
                continue;
            }
            if (wraps && p.line < c.buffer.count() - 1) {
                if (c.operator != null && !inclusive && !line.isEmpty()) inclusive = true;
                else {
                    p.line++;
                    p.col = 0;
                    inclusive = false;
                }
                continue;
            }
            if (c.operator == null) {
                if (n == c.count) return null;
            } else if (!line.isEmpty()) inclusive = true;
            break;
        }
        return inclusive ? Target.inclusive(p) : Target.exclusive(p);
    }

    /**
     * cursor_down: fails only on the last line (or in a closed fold that ends there); a count past the end stops on
     * the last line; a closed fold counts as one line.
     */
    static Target down(Context c, int n) {
        int lnum = c.cursor().line + 1;
        if (n > 0) {
            int[] fold = c.editor.folds().closed(lnum);
            if ((fold != null ? fold[1] : lnum) >= c.buffer.count()) return null;
        }
        int line = c.editor.view().downFrom(lnum, n) - 1;
        return Target.linewise(new Pos(line, c.editor.wantedColumn(line)), Column.KEEP);
    }

    /** cursor_up: fails on the first line; a closed fold counts as one line. */
    static Target up(Context c, int n) {
        int lnum = c.cursor().line + 1;
        if (n > 0 && lnum <= 1) return null;
        int line = c.editor.view().upFrom(lnum, n, c.editor.mode().inserting()) - 1;
        return Target.linewise(new Pos(line, c.editor.wantedColumn(line)), Column.KEEP);
    }

    private static Target firstNonBlank(Context c, Target target) {
        if (target == null) return null;
        int line = target.pos().line;
        return Target.linewise(new Pos(line, Text.firstNonBlank(c.buffer.line(line))), Column.RESET);
    }

    /** G, gg and N%: linewise to a line, the first non-blank with 'startofline'. */
    static Target goToLine(Context c, int number) {
        int line = Math.max(0, Math.min(number, c.buffer.count()) - 1);
        boolean sol = c.options.flag("startofline");
        int col = sol ? Text.firstNonBlank(c.buffer.line(line)) : c.editor.wantedColumn(line);
        return Target.linewise(new Pos(line, col), sol ? Column.RESET : Column.KEEP).asJump();
    }

    static Target endOfLine(Context c) {
        Pos p = c.cursor();
        int down = c.count - 1, last = c.buffer.count() - 1;
        if (down > 0 && p.line >= last) return null;
        int line = Math.min(last, p.line + down);
        // In Visual mode the cursor goes onto the end of the line, so the area takes the line break (coladvance).
        String text = c.buffer.line(line);
        return new Target(new Pos(line, c.inVisual() ? text.length() : Text.last(text)), Target.Kind.INCLUSIVE, Column.END, false, false, true);
    }

    static Target lastNonBlank(Context c) {
        Pos p = c.cursor();
        int down = c.count - 1, last = c.buffer.count() - 1;
        if (down > 0 && p.line >= last) return null;
        int line = Math.min(last, p.line + down);
        String text = c.buffer.line(line);
        int col = Text.last(text);
        while (col > 0 && (text.charAt(col) == ' ' || text.charAt(col) == '\t')) col = Text.previous(text, col);
        return Target.inclusive(new Pos(line, col));
    }

    static Target goToOffset(Context c) {
        int offset = Math.max(0, Math.min(c.count - 1, c.buffer.length() - 1));
        return Target.exclusive(c.buffer.pos(Math.max(0, offset))).asJump();
    }

    /** nv_wordcmd: w, W, e and E, with the cw special case and the stop at the end of a line for operators. */
    static Target word(Context c, boolean bigword, boolean end) {
        Pos start = c.cursor(), p = start.copy();
        Walk walk = c.walk(bigword);
        boolean wordEnd = end, stop = false;
        if (!end && "c".equals(c.operator)) {
            int ch = walk.gchar(p);
            if (ch != 0) {
                if (ch != ' ' && ch != '\t') wordEnd = true;
                stop = true;
            }
        }
        boolean ok = wordEnd ? walk.endWord(p, c.count, stop, false) : walk.fwdWord(p, c.count, c.operator != null);
        boolean inclusive = wordEnd;
        // adjust_cursor: never leave the cursor on the end of the line after moving forward.
        if (p.compareTo(start) > 0 && p.col > 0 && walk.gchar(p) == 0) {
            p.col = Text.previous(c.buffer.line(p.line), p.col);
            inclusive = true;
        }
        if (!ok && c.operator == null) return null;
        return inclusive ? Target.inclusive(p) : Target.exclusive(p);
    }

    static Target backWord(Context c, boolean bigword) {
        Pos p = c.cursor();
        return c.walk(bigword).bckWord(p, c.count, false) ? Target.exclusive(p) : null;
    }

    static Target backEnd(Context c, boolean bigword) {
        Pos p = c.cursor();
        return c.walk(bigword).bckendWord(p, c.count, false) ? Target.inclusive(p) : null;
    }

    /** f, F, t and T typed: with sneak on, they take {@code ;} and {@code ,} back from it. */
    private static Target typedFind(Context c, boolean forward, boolean till) {
        Sneak.findTyped(c);
        return find(c, forward, till, false, false);
    }

    /** searchc: f, F, t, T, ; and ,. */
    static Target find(Context c, boolean forward, boolean till, boolean repeat, boolean reverse) {
        int ch;
        boolean stop = true;
        if (repeat) {
            Vim.Find last = c.vim.lastFind();
            if (last == null) return null;
            forward = reverse != last.forward();
            till = last.till();
            ch = last.character();
            // Without ';' in 'cpoptions', ";" after t moves at least one character.
            if (c.count == 1 && till) stop = false;
        } else {
            ch = c.argumentChar();
            if (c.argument != null && c.argument.is("<Tab>")) ch = '\t';
            if (ch < 0) return null;
            c.vim.lastFind(new Vim.Find(forward, till, ch));
        }
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        int col = p.col;
        for (int count = c.count; count > 0; count--) {
            while (true) {
                if (forward) {
                    col = Text.next(line, col);
                    if (col >= line.length()) return null;
                } else {
                    if (col == 0) return null;
                    col = Text.previous(line, col);
                }
                if (line.codePointAt(col) == ch && stop) break;
                stop = true;
            }
        }
        if (till) col = forward ? Text.previous(line, col) : Text.next(line, col);
        Pos result = new Pos(p.line, col);
        return forward ? Target.inclusive(result) : Target.exclusive(result);
    }

    /** nv_brace: ( and ). */
    static Target sentence(Context c, boolean forward) {
        Pos p = c.cursor();
        Walk walk = c.walk(false);
        if (!findSentence(walk, p, forward, c.count)) return null;
        boolean inclusive = false;
        if (p.col > 0 && walk.gchar(p) == 0) {
            p.col = Text.previous(c.buffer.line(p.line), p.col);
            inclusive = true;
        }
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).usingRegisterOne().asJump();
    }

    /** nv_findpar: { and }. */
    static Target paragraph(Context c, boolean forward, int what, boolean both) {
        Pos p = findParagraph(c.buffer, c.cursor().line, forward, c.count, what, both, c.editor.folds().any() ? c.editor.folds()::closed : null);
        if (p == null) return null;
        boolean inclusive = p.line == c.buffer.count() - 1 && what != '}' && forward && !c.buffer.line(p.line).isEmpty();
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).usingRegisterOne().asJump();
    }

    /** nv_brackets: [[, ]], [] and ][. */
    static Target section(Context c, boolean forward, char what) {
        boolean both = c.operator != null && forward && what == '{';
        Pos p = findParagraph(c.buffer, c.cursor().line, forward, c.count, what, both, c.editor.folds().any() ? c.editor.folds()::closed : null);
        if (p == null) return null;
        boolean inclusive = p.line == c.buffer.count() - 1 && what != '}' && forward && !c.buffer.line(p.line).isEmpty();
        if (!inclusive && c.operator == null) p.col = Text.firstNonBlank(c.buffer.line(p.line));
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).asJump();
    }

    static Target percent(Context c) {
        if (c.hasCount) {
            if (c.count > 100) return null;
            return goToLine(c, (int) ((c.count * (long) c.buffer.count() + 99) / 100)).withColumn(Column.RESET);
        }
        Pos match = Matching.percent(c.buffer, c.cursor(), c.options.string("matchpairs"));
        return match == null ? null : Target.inclusive(match).asJump().usingRegisterOne();
    }

    static Target unmatched(Context c, char open, char close, boolean backward) {
        Pos p = c.cursor();
        for (int n = c.count; n > 0; n--) {
            Pos found = Matching.unmatched(c.buffer, p, open, close, backward, true);
            if (found == null) {
                if (n == c.count) return null;
                break;
            }
            p = found;
        }
        return Target.exclusive(p).asJump();
    }

    /** findpar (textobject.c): the {@code count}-th paragraph or section boundary; null when there are too few. */
    static Pos findParagraph(Buffer buffer, int start, boolean forward, int count, int what, boolean both,
                             java.util.function.IntFunction<int[]> folds) {
        int curr = start, dir = forward ? 1 : -1, last = buffer.count() - 1;
        while (count-- > 0) {
            boolean didSkip = false;
            for (boolean first = true; ; first = false) {
                if (!buffer.line(curr).isEmpty()) didSkip = true;
                // A closed fold the search starts in is skipped whole.
                boolean foldSkipped = false;
                int[] fold = first && folds != null ? folds.apply(curr + 1) : null;
                if (fold != null) {
                    curr = (dir > 0 ? fold[1] : fold[0]) - 1 + dir;
                    foldSkipped = true;
                }
                if (!first && didSkip && startPS(buffer, curr, what, both)) break;
                if (foldSkipped) curr -= dir;
                curr += dir;
                if (curr < 0 || curr > last) {
                    if (count > 0) return null;
                    curr -= dir;
                    break;
                }
            }
        }
        if (both && buffer.line(curr).startsWith("}")) curr = Math.min(curr + 1, last);
        Pos p = new Pos(curr, 0);
        if (curr == last && what != '}' && forward) p.col = Text.last(buffer.line(curr));
        return p;
    }

    /** startPS: a paragraph or section starts at the line (an empty line for {@code para} 0). */
    static boolean startPS(Buffer buffer, int line, int para, boolean both) {
        String s = buffer.line(line);
        int c = s.isEmpty() ? 0 : s.charAt(0);
        if (c == para || c == '\f' || both && c == '}') return true;
        return c == '.' && (inMacro(SECTIONS, s) || para == 0 && inMacro(PARAGRAPHS, s));
    }

    /** inmacro: the nroff macro after the line's leading dot is listed in {@code macros} (two characters each). */
    private static boolean inMacro(String macros, String line) {
        char s0 = line.length() > 1 ? line.charAt(1) : 0, s1 = line.length() > 2 ? line.charAt(2) : 0;
        for (int i = 0; i < macros.length(); i += 2) {
            char m0 = macros.charAt(i), m1 = i + 1 < macros.length() ? macros.charAt(i + 1) : 0;
            boolean first = m0 == s0 || m0 == ' ' && (s0 == 0 || s0 == ' ');
            boolean second = m1 == s1 || (m1 == 0 || m1 == ' ') && (s0 == 0 || s1 == 0 || s1 == ' ');
            if (first && second) return true;
        }
        return false;
    }

    /** findsent (textobject.c). */
    static boolean findSentence(Walk walk, Pos pos, boolean forward, int count) {
        Buffer buffer = walk.buffer();
        boolean noskip = false;
        while (count-- > 0) {
            boolean found = false;
            if (walk.gchar(pos) == 0) {
                do {
                    if ((forward ? walk.incl(pos) : walk.decl(pos)) == Walk.FAIL) break;
                } while (walk.gchar(pos) == 0);
                if (forward) found = true;
            } else if (forward && pos.col == 0 && startPS(buffer, pos.line, 0, false)) {
                if (pos.line == buffer.count() - 1) return false;
                pos.line++;
                found = true;
            } else if (!forward) walk.decl(pos);

            if (!found) {
                boolean foundDot = false;
                int c;
                while ((c = walk.gchar(pos)) == ' ' || c == '\t' || c != 0 && ".!?)]\"'".indexOf(c) >= 0) {
                    Pos t = pos.copy();
                    if (walk.decl(t) == Walk.FAIL || buffer.line(t.line).isEmpty() && forward) break;
                    if (foundDot) break;
                    if (".!?".indexOf(c) >= 0) foundDot = true;
                    int before = walk.gchar(t);
                    if (")]\"'".indexOf(c) >= 0 && (before == 0 || ".!?)]\"'".indexOf(before) < 0)) break;
                    walk.decl(pos);
                }
                int startLine = pos.line;
                while (true) {
                    c = walk.gchar(pos);
                    if (c == 0 || pos.col == 0 && startPS(buffer, pos.line, 0, false)) {
                        if (!forward && pos.line != startLine) {
                            pos.line++;
                            pos.col = Math.min(pos.col, buffer.line(pos.line).length());
                        }
                        break;
                    }
                    if (c == '.' || c == '!' || c == '?') {
                        Pos t = pos.copy();
                        int after;
                        while (true) {
                            if (walk.inc(t) == Walk.FAIL) {
                                after = -1;
                                break;
                            }
                            after = walk.gchar(t);
                            if (after == 0 || ")]\"'".indexOf(after) < 0) break;
                        }
                        if (after == -1 || after == ' ' || after == '\t' || after == 0) {
                            pos.set(t);
                            if (walk.gchar(pos) == 0) walk.inc(pos);
                            break;
                        }
                    }
                    if ((forward ? walk.incl(pos) : walk.decl(pos)) == Walk.FAIL) {
                        if (count > 0) return false;
                        noskip = true;
                        break;
                    }
                }
            }
            while (!noskip && (walk.gchar(pos) == ' ' || walk.gchar(pos) == '\t')) if (walk.incl(pos) == Walk.FAIL) break;
        }
        return true;
    }
}
