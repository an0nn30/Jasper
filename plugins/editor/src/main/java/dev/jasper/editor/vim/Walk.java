package dev.jasper.editor.vim;

/**
 * Vim's character walking, ported from misc2.c, search.c and textobject.c so motions and text objects match Vim:
 * {@code inc}, {@code dec}, {@code incl}, {@code decl}, {@code gchar}, {@code cls} and the word movers. Positions
 * are mutated in place, as Vim's are. Return codes are Vim's: 0 moved within the line, 1 moved to another line,
 * 2 moved onto the end of the line, -1 could not move.
 */
final class Walk {
    static final int FAIL = -1;

    private final Buffer buffer;
    private final Keywords keywords;
    /** Vim's cls_bigword: every non-blank is one class, for W, B, E and gE. */
    boolean bigword;

    /** The closed fold holding a line (1-based) as {first, last}, or null; null when there are no folds. */
    java.util.function.IntFunction<int[]> folds;

    /** fwd_word and end_word: from inside a closed fold, go to the last character of its last line first. */
    private void toFoldEnd(Pos pos) {
        int[] fold = folds == null ? null : folds.apply(pos.line + 1);
        if (fold == null) return;
        pos.line = fold[1] - 1;
        String text = buffer.line(pos.line);
        pos.col = text.isEmpty() ? 0 : Text.last(text);
    }

    /** bck_word: from inside a closed fold, go to the start of its first line first. */
    private void toFoldStart(Pos pos) {
        int[] fold = folds == null ? null : folds.apply(pos.line + 1);
        if (fold == null) return;
        pos.line = fold[0] - 1;
        pos.col = 0;
    }

    Walk(Buffer buffer, Keywords keywords) {
        this.buffer = buffer;
        this.keywords = keywords;
    }

    Buffer buffer() { return buffer; }

    Keywords keywords() { return keywords; }

    /** @return the character at the position, or 0 on the end of the line (Vim's NUL) */
    int gchar(Pos pos) {
        String line = buffer.line(pos.line);
        return pos.col < line.length() ? line.codePointAt(pos.col) : 0;
    }

    int inc(Pos pos) {
        String line = buffer.line(pos.line);
        if (pos.col < line.length()) {
            pos.col = Text.next(line, pos.col);
            return pos.col < line.length() ? 0 : 2;
        }
        if (pos.line < buffer.count() - 1) {
            pos.line++;
            pos.col = 0;
            return 1;
        }
        return FAIL;
    }

    int dec(Pos pos) {
        if (pos.col > 0) {
            pos.col = Text.previous(buffer.line(pos.line), pos.col);
            return 0;
        }
        if (pos.line > 0) {
            pos.line--;
            pos.col = buffer.line(pos.line).length();
            return 1;
        }
        return FAIL;
    }

    /** inc, skipping the end of a non-empty line. */
    int incl(Pos pos) {
        int result = inc(pos);
        if (result >= 1 && pos.col > 0) result = inc(pos);
        return result;
    }

    /** dec, skipping the end of a non-empty line. */
    int decl(Pos pos) {
        int result = dec(pos);
        if (result == 1 && pos.col > 0) result = dec(pos);
        return result;
    }

    /** @return the character class at the position: 0 blank (or end of line), 1 punctuation, 2 and up words */
    int cls(Pos pos) {
        int c = gchar(pos);
        if (c == ' ' || c == '\t' || c == 0) return 0;
        int cls = keywords.charClass(c);
        return cls != 0 && bigword ? 1 : cls;
    }

    boolean lineEmpty(int line) { return buffer.line(line).isEmpty(); }

    /** @return whether only spaces and tabs precede column {@code pos.col + extra} (Vim's inindent) */
    boolean inIndent(Pos pos, int extra) { return Text.firstNonBlank(buffer.line(pos.line)) >= pos.col + extra; }

    /** Vim's fwd_word: moves to the start of the {@code count}-th next word. */
    boolean fwdWord(Pos pos, int count, boolean eol) {
        while (--count >= 0) {
            toFoldEnd(pos);
            int sclass = cls(pos);
            boolean lastLine = pos.line == buffer.count() - 1;
            int i = inc(pos);
            if (i == FAIL || i >= 1 && lastLine) return false;
            if (i >= 1 && eol && count == 0) return true;
            if (sclass != 0) {
                while (cls(pos) == sclass) {
                    i = inc(pos);
                    if (i == FAIL || i >= 1 && eol && count == 0) return true;
                }
            }
            while (cls(pos) == 0) {
                if (pos.col == 0 && lineEmpty(pos.line)) break;
                i = inc(pos);
                if (i == FAIL || i >= 1 && eol && count == 0) return true;
            }
        }
        return true;
    }

    /** Vim's bck_word: moves to the start of the {@code count}-th previous word. */
    boolean bckWord(Pos pos, int count, boolean stop) {
        while (--count >= 0) {
            toFoldStart(pos);
            int sclass = cls(pos);
            if (dec(pos) == FAIL) return false;
            boolean finished = false;
            if (!stop || sclass == cls(pos) || sclass == 0) {
                while (cls(pos) == 0) {
                    if (pos.col == 0 && lineEmpty(pos.line)) {
                        finished = true;
                        break;
                    }
                    if (dec(pos) == FAIL) return true;
                }
                if (!finished && skipChars(pos, cls(pos), false)) return true;
            }
            if (!finished) inc(pos);
            stop = false;
        }
        return true;
    }

    /** Vim's end_word: moves to the end of the {@code count}-th word. */
    boolean endWord(Pos pos, int count, boolean stop, boolean empty) {
        while (--count >= 0) {
            toFoldEnd(pos);
            int sclass = cls(pos);
            if (inc(pos) == FAIL) return false;
            boolean finished = false;
            if (cls(pos) == sclass && sclass != 0) {
                if (skipChars(pos, sclass, true)) return false;
            } else if (!stop || sclass == 0) {
                while (cls(pos) == 0) {
                    if (pos.col == 0 && lineEmpty(pos.line) && empty) {
                        finished = true;
                        break;
                    }
                    if (inc(pos) == FAIL) return false;
                }
                if (!finished && skipChars(pos, cls(pos), true)) return false;
            }
            if (!finished) dec(pos);
            stop = false;
        }
        return true;
    }

    /** Vim's bckend_word: moves to the end of the {@code count}-th previous word. */
    boolean bckendWord(Pos pos, int count, boolean eol) {
        while (--count >= 0) {
            int sclass = cls(pos);
            int i = dec(pos);
            if (i == FAIL) return false;
            if (eol && i == 1) return true;
            if (sclass != 0) {
                while (cls(pos) == sclass) {
                    i = dec(pos);
                    if (i == FAIL || eol && i == 1) return true;
                }
            }
            while (cls(pos) == 0) {
                if (pos.col == 0 && lineEmpty(pos.line)) break;
                i = dec(pos);
                if (i == FAIL || eol && i == 1) return true;
            }
        }
        return true;
    }

    /** @return true when the start or end of the buffer was hit */
    boolean skipChars(Pos pos, int cclass, boolean forward) {
        while (cls(pos) == cclass) if ((forward ? inc(pos) : dec(pos)) == FAIL) return true;
        return false;
    }

    /** Vim's back_in_line: back to the start of the run of characters of the cursor's class, within the line. */
    void backInLine(Pos pos) {
        int sclass = cls(pos);
        while (pos.col != 0) {
            dec(pos);
            if (cls(pos) != sclass) {
                inc(pos);
                break;
            }
        }
    }

    /** Vim's oneleft: false at the start of the line. */
    boolean oneLeft(Pos pos) {
        if (pos.col == 0) return false;
        pos.col = Text.previous(buffer.line(pos.line), pos.col);
        return true;
    }
}
