package dev.jasper.editor.vim;

/**
 * The part of one line inside a block of display columns: Vim's {@code struct block_def}, filled by
 * {@link #prep}, a port of ops.c's block_prep. A tab that straddles the block's edge is counted as
 * {@link #startspaces} or {@link #endspaces}: the spaces that stand for the part of it inside (or, when deleting,
 * outside) the block.
 */
final class Block {
    /** Which operation the block is prepared for; block_prep's answers differ for these. */
    enum Op { OTHER, INSERT, APPEND, REPLACE, LSHIFT }

    /** Display columns before the first character wholly inside the block. */
    int startspaces;
    /** Display columns after the last character wholly inside the block. */
    int endspaces;
    /** Length of the text inside the block, in UTF-16 units. */
    int textlen;
    /** Column of the first character (partly) inside the block. */
    int textcol;
    /** Display column of the first character wholly inside the block. */
    int startVcol;
    /** Display column of the first character wholly after the block. */
    int endVcol;
    /** Whether the line ends before the block does. */
    boolean isShort;
    /** Whether the block lies inside one character (a tab). */
    boolean isOneChar;
    /** Whether {@code $} made the block reach every line's end (Vim's is_MAX). */
    boolean isMax;
    /** Display columns and characters of white space just before the block. */
    int preWhitesp, preWhitespC;
    /** Display widths of the character after, and before, the block. */
    int endCharVcols, startCharVcols;

    private Block() { }

    /** @return the text inside the block, as op_yank's yank_copy_line builds it (padding spaces included) */
    String copy(String line) {
        return " ".repeat(startspaces) + line.substring(textcol, Math.min(line.length(), textcol + textlen)) + " ".repeat(Math.max(0, endspaces));
    }

    /**
     * block_prep.
     *
     * @param line the line's text
     * @param opStart the block's first display column
     * @param opEnd the block's last display column (inclusive)
     * @param tabstop 'tabstop'
     * @param isDel whether the text will be removed (a partly covered tab is then counted from the outside)
     * @param op the operation
     * @param inclusive the operator's inclusive flag (for {@link Op#APPEND} on short lines)
     * @return the block's part of the line
     */
    static Block prep(String line, int opStart, int opEnd, int tabstop, boolean isDel, Op op, boolean inclusive) {
        Block b = new Block();
        int incr = 0, col = 0, vcol = 0, prevPstart = 0;
        while (vcol < opStart && col < line.length()) {
            incr = Text.width(line, col, vcol, tabstop);
            vcol += incr;
            if (white(line.charAt(col))) {
                b.preWhitesp += incr;
                b.preWhitespC++;
            } else {
                b.preWhitesp = 0;
                b.preWhitespC = 0;
            }
            prevPstart = col;
            col = Text.next(line, col);
        }
        b.startVcol = vcol;
        int pstart = col;
        b.startCharVcols = incr;
        if (b.startVcol < opStart) {
            b.endVcol = b.startVcol;
            b.isShort = true;
            if (!isDel || op == Op.APPEND) b.endspaces = opEnd - opStart + 1;
        } else {
            b.startspaces = b.startVcol - opStart;
            if (isDel && b.startspaces != 0) b.startspaces = b.startCharVcols - b.startspaces;
            int pend = pstart;
            b.endVcol = b.startVcol;
            if (b.endVcol > opEnd) {
                b.isOneChar = true;
                if (op == Op.INSERT) b.endspaces = b.startCharVcols - b.startspaces;
                else if (op == Op.APPEND) {
                    b.startspaces += opEnd - opStart + 1;
                    b.endspaces = b.startCharVcols - b.startspaces;
                } else {
                    b.startspaces = opEnd - opStart + 1;
                    if (isDel && op != Op.LSHIFT) {
                        b.startspaces = b.startCharVcols - (b.startVcol - opStart);
                        b.endspaces = b.endVcol - opEnd - 1;
                    }
                }
            } else {
                int v = b.endVcol, prevPend = pend;
                while (v <= opEnd && pend < line.length()) {
                    prevPend = pend;
                    incr = Text.width(line, pend, v, tabstop);
                    v += incr;
                    pend = Text.next(line, pend);
                }
                b.endVcol = v;
                if (b.endVcol <= opEnd && (!isDel || op == Op.APPEND || op == Op.REPLACE)) {
                    b.isShort = true;
                    b.endspaces = op == Op.APPEND ? opEnd - b.endVcol + (inclusive ? 1 : 0) : 0;
                } else if (b.endVcol > opEnd) {
                    b.endspaces = b.endVcol - opEnd - 1;
                    if (!isDel && b.endspaces != 0) {
                        b.endspaces = incr - b.endspaces;
                        if (pend != pstart) pend = prevPend;
                    }
                }
            }
            b.endCharVcols = incr;
            if (isDel && b.startspaces != 0) pstart = prevPstart;
            b.textlen = pend - pstart;
        }
        b.textcol = pstart;
        return b;
    }

    static boolean white(char c) { return c == ' ' || c == '\t'; }
}
