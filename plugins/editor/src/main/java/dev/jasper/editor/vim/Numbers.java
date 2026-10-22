package dev.jasper.editor.vim;

/**
 * {@code <C-a>} and {@code <C-x>} (spec 4.1), ported from Vim's ops.c (op_addsub, do_addsub) and charset.c
 * (vim_str2nr): decimal, and with 'nrformats' binary ({@code 0b}), octal (a leading 0) and hex ({@code 0x}), with
 * alpha, unsigned and blank; numbers are unsigned 64-bit as in Vim, so they wrap the same way. In Visual mode each
 * line's first number changes, and {@code g<C-a>} adds progressively more on each line.
 */
final class Numbers {
    private Numbers() { }

    static void register(VimCommands commands) {
        commands.action("<C-a>", "add {count} to the number at or after the cursor", true, c -> addsub(c, false));
        commands.action("<C-x>", "subtract {count} from the number at or after the cursor", true, c -> addsub(c, true));
        commands.visual("<C-a>", "add {count} to the first number in each line", false, true, c -> Visual.operate(c, "<C-a>", false));
        commands.visual("<C-x>", "subtract {count} from the first number in each line", false, true, c -> Visual.operate(c, "<C-x>", false));
        commands.visual("g<C-a>", "add {count}, 2 × {count}, … to the numbers of successive lines", false, true, c -> Visual.operate(c, "g<C-a>", false));
        commands.visual("g<C-x>", "subtract {count}, 2 × {count}, … from the numbers of successive lines", false, true, c -> Visual.operate(c, "g<C-x>", false));
    }

    /** op_addsub in Normal mode. */
    static void addsub(Context c, boolean subtract) {
        Pos p = c.cursor();
        if (!change(c, p, 0, c.count, subtract, false, false)) throw Context.fail();
    }

    /** op_addsub on a Visual area; {@code progressive} for g CTRL-A and g CTRL-X. */
    static void visual(Context c, Span span, boolean subtract, boolean progressive) {
        Buffer b = c.buffer;
        long step = c.count, amount = step;
        int changes = 0;
        Pos startpos = null;
        Pos end = span.end().copy();
        for (int line = span.start().line; line <= span.end().line; line++) {
            Pos pos;
            int length;
            if (span.blockwise()) {
                Block bd = Operators.prep(c, span, b.line(line), false, Block.Op.OTHER);
                pos = new Pos(line, bd.textcol);
                length = bd.textlen;
            } else if (span.linewise()) {
                c.editor.setCursor(new Pos(c.cursor().line, 0), true);
                pos = new Pos(line, 0);
                length = b.line(line).length();
            } else {
                if (line == span.start().line && span.kind() != Target.Kind.INCLUSIVE) Search.decl(b, end);
                length = b.line(line).length();
                pos = new Pos(line, 0);
                if (line == span.start().line) {
                    pos.col = span.start().col;
                    length -= span.start().col;
                }
                if (line == end.line) {
                    length = b.line(end.line).length();
                    if (end.col >= length) end.col = length - 1;
                    length = end.col - pos.col + 1;
                }
            }
            Pos before = c.editor.marks().get('[');
            boolean one = change(c, pos, length, amount, subtract, true, span.linewise());
            if (one) {
                if (changes == 0) startpos = c.editor.marks().get('[');
                changes++;
            } else if (before != null) c.editor.marks().set('[', before);
            if (progressive && one) amount += step;
        }
        if (changes > 0) c.editor.marks().set('[', startpos);
    }

    /**
     * do_addsub.
     *
     * @param length in Visual mode, how much of the line to look in
     * @return whether a number changed
     */
    private static boolean change(Context c, Pos pos, int length, long amount, boolean sub, boolean visual, boolean linewise) {
        Buffer b = c.buffer;
        String nf = c.options.string("nrformats");
        boolean hex = nf.contains("x"), oct = nf.contains("o"), bin = nf.contains("b"), alpha = nf.contains("p");
        boolean unsigned = nf.contains("u"), blank = nf.contains("k");
        boolean blankUnsigned = false, negative = false, wasPositive = true, didChange = false;
        Pos saveCursor = c.cursor();
        String ptr = b.line(pos.line);
        int linelen = ptr.length();
        int col = pos.col;
        try {
            if (col >= linelen) return false;
            if (!visual) {
                if (bin) while (col > 0 && isBin(at(ptr, col))) col--;
                if (hex) while (col > 0 && isHex(at(ptr, col))) col--;
                if (bin && hex && !(col > 0 && (at(ptr, col) == 'X' || at(ptr, col) == 'x') && at(ptr, col - 1) == '0' && isHex(at(ptr, col + 1)))) {
                    col = pos.col;
                    while (col > 0 && isDigit(at(ptr, col))) col--;
                }
                if (hex && col > 0 && (at(ptr, col) == 'X' || at(ptr, col) == 'x') && at(ptr, col - 1) == '0' && isHex(at(ptr, col + 1))
                    || bin && col > 0 && (at(ptr, col) == 'B' || at(ptr, col) == 'b') && at(ptr, col - 1) == '0' && isBin(at(ptr, col + 1))) {
                    col--;
                } else {
                    col = pos.col;
                    while (col < linelen && !isDigit(at(ptr, col)) && !(alpha && isAlpha(at(ptr, col))))
                        col += Character.charCount(ptr.codePointAt(col));
                    while (col > 0 && isDigit(at(ptr, col - 1)) && !(alpha && isAlpha(at(ptr, col)))) col--;
                }
            }
            if (visual) {
                while (col < linelen && length > 0 && !isDigit(at(ptr, col)) && !(alpha && isAlpha(at(ptr, col)))) {
                    int n = Character.charCount(ptr.codePointAt(col));
                    col += n;
                    length -= n;
                }
                if (length <= 0) return false;
                if (col > pos.col && at(ptr, col - 1) == '-' && !unsigned) {
                    if (blank && col >= 2 && !Block.white(at(ptr, col - 2))) blankUnsigned = true;
                    else {
                        negative = true;
                        wasPositive = false;
                    }
                }
            }
            char firstdigit = at(ptr, col);
            if (!isDigit(firstdigit) && !(alpha && isAlpha(firstdigit))) return false;
            Marks marks = c.editor.marks();
            if (alpha && isAlpha(firstdigit)) {
                int ord = Character.toLowerCase(firstdigit) - 'a';
                if (sub) firstdigit = ord < amount ? (Character.isUpperCase(firstdigit) ? 'A' : 'a') : (char) (firstdigit - amount);
                else firstdigit = 26 - ord - 1 < amount ? (Character.isUpperCase(firstdigit) ? 'Z' : 'z') : (char) (firstdigit + amount);
                b.replace(b.offset(pos.line, col), b.offset(pos.line, col + 1), String.valueOf(firstdigit));
                marks.set('[', new Pos(pos.line, col));
                marks.set(']', new Pos(pos.line, col));
                c.editor.setCursor(new Pos(pos.line, col));
                didChange = true;
                return true;
            }
            if (col > 0 && at(ptr, col - 1) == '-' && !visual && !unsigned) {
                if (blank && col >= 2 && !Block.white(at(ptr, col - 2))) blankUnsigned = true;
                else {
                    col--;
                    negative = true;
                }
            }
            int maxlen = 0;
            if (visual && !linewise) maxlen = c.editor.marks().visualCurswant() == Visual.MAX ? linelen - col : length;
            Parsed parsed = str2nr(ptr, col, hex, oct, bin, maxlen);
            char pre = parsed.pre;
            length = parsed.length;
            long n = parsed.value;
            if (pre != 0 && negative) {
                col++;
                length--;
                negative = false;
            }
            boolean subtract = sub ^ negative;
            long oldn = n;
            if (!parsed.overflow) n = subtract ? n - amount : n + amount;
            if (pre == 0) {
                if (subtract) {
                    if (Long.compareUnsigned(n, oldn) > 0) {
                        n = 1 + ~n;
                        negative = !negative;
                    }
                } else if (Long.compareUnsigned(n, oldn) < 0) {
                    n = ~n;
                    negative = !negative;
                }
                if (n == 0) negative = false;
            }
            if ((unsigned || blankUnsigned) && negative) {
                n = subtract ? 0 : -1;
                negative = false;
            }
            if (visual && !wasPositive && !negative && col > 0) {
                col--;
                length++;
            }
            int todel = length;
            if (at(ptr, col) == '-') length--;
            for (int i = 0; i < todel && col + i < linelen; i++) {
                char ch = at(ptr, col + i);
                if (ch < 0x100 && Character.isLetter(ch)) c.vim.hexUpper(Character.isUpperCase(ch));
            }
            StringBuilder buf1 = new StringBuilder();
            if (negative && (!visual || wasPositive)) buf1.append('-');
            if (pre != 0) {
                buf1.append('0');
                length--;
            }
            if (pre == 'b' || pre == 'B' || pre == 'x' || pre == 'X') {
                buf1.append(pre);
                length--;
            }
            String buf2;
            if (pre == 'b' || pre == 'B') buf2 = n == 0 ? "" : Long.toBinaryString(n);
            else if (pre == 0) buf2 = Long.toUnsignedString(n);
            else if (pre == '0') buf2 = Long.toOctalString(n);
            else if (c.vim.hexUpper()) buf2 = Long.toHexString(n).toUpperCase(java.util.Locale.ROOT);
            else buf2 = Long.toHexString(n);
            length -= buf2.length();
            if (firstdigit == '0' && !(oct && pre == 0)) while (length-- > 0) buf1.append('0');
            buf1.append(buf2);
            int from = b.offset(pos.line, col);
            b.replace(from, from + Math.min(todel, linelen - col), buf1.toString());
            didChange = true;
            int endCol = col + buf1.length();
            marks.set('[', new Pos(pos.line, col));
            marks.set(']', new Pos(pos.line, Math.max(0, endCol - 1)));
            c.editor.setCursor(new Pos(pos.line, endCol > 0 ? endCol - 1 : 0));
            return true;
        } finally {
            if (visual) c.editor.setCursor(saveCursor, true);
            else if (didChange) c.editor.forgetColumn();
        }
    }

    private record Parsed(char pre, int length, long value, boolean overflow) { }

    /** vim_str2nr: the number at {@code start} (after an optional {@code -}); length counts the {@code -}. */
    private static Parsed str2nr(String s, int start, boolean hex, boolean oct, boolean bin, int maxlen) {
        int ptr = start;
        if (at(s, ptr) == '-') {
            ptr++;
            if (maxlen > 0) maxlen--;
        }
        char pre = 0;
        if (at(s, ptr) == '0' && at(s, ptr + 1) != '8' && at(s, ptr + 1) != '9' && (maxlen == 0 || maxlen > 1)) {
            pre = at(s, ptr + 1);
            if (hex && (pre == 'X' || pre == 'x') && isHex(at(s, ptr + 2)) && (maxlen == 0 || maxlen > 2)) ptr += 2;
            else if (bin && (pre == 'B' || pre == 'b') && isBin(at(s, ptr + 2)) && (maxlen == 0 || maxlen > 2)) ptr += 2;
            else {
                pre = 0;
                if (oct) {
                    for (int n = 1; n != maxlen && isDigit(at(s, ptr + n)); n++) {
                        if (at(s, ptr + n) > '7') {
                            pre = 0;
                            break;
                        }
                        pre = '0';
                    }
                }
            }
        }
        long un = 0;
        boolean overflow = false;
        int n = pre != 0 && pre != '0' ? 3 : 1;
        if (pre == 'B' || pre == 'b') {
            while (at(s, ptr) == '0' || at(s, ptr) == '1') {
                if (Long.compareUnsigned(un, Long.divideUnsigned(-1L, 2)) <= 0) un = 2 * un + (at(s, ptr) - '0');
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        } else if (pre == '0') {
            while (at(s, ptr) >= '0' && at(s, ptr) <= '7') {
                if (Long.compareUnsigned(un, Long.divideUnsigned(-1L, 8)) <= 0) un = 8 * un + (at(s, ptr) - '0');
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        } else if (pre != 0) {
            while (isHex(at(s, ptr))) {
                if (Long.compareUnsigned(un, Long.divideUnsigned(-1L, 16)) <= 0) un = 16 * un + Character.digit(at(s, ptr), 16);
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        } else {
            long max10 = Long.divideUnsigned(-1L, 10), rem = Long.remainderUnsigned(-1L, 10);
            while (isDigit(at(s, ptr))) {
                int digit = at(s, ptr) - '0';
                if (Long.compareUnsigned(un, max10) < 0 || un == max10 && digit <= rem) un = 10 * un + digit;
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        }
        return new Parsed(pre, ptr - start, un, overflow);
    }

    private static char at(String s, int i) { return i >= 0 && i < s.length() ? s.charAt(i) : 0; }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    private static boolean isHex(char c) { return isDigit(c) || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'; }

    private static boolean isBin(char c) { return c == '0' || c == '1'; }

    private static boolean isAlpha(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }
}
