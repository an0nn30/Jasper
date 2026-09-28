package dev.jasper.editor.vim;

/**
 * Word characters from 'iskeyword' and Vim's character classes (mbyte.c's utf_class): 0 for blanks, 1 for
 * punctuation, 2 for word characters, and higher classes for emoji and CJK scripts, so {@code w} stops between them.
 */
final class Keywords {
    private final boolean[] table = new boolean[256];

    /**
     * @param spec an 'iskeyword' value such as {@code @,48-57,_,192-255}
     * @return its word characters
     * @throws IllegalArgumentException for a malformed value (E474)
     */
    static Keywords parse(String spec) {
        var keywords = new Keywords();
        int i = 0;
        while (i < spec.length()) {
            boolean exclude = spec.charAt(i) == '^' && i + 1 < spec.length();
            if (exclude) i++;
            int from, to;
            if (spec.startsWith("@-@", i)) {
                from = to = '@';
                i += 3;
            } else if (spec.charAt(i) == '@' && (i + 1 == spec.length() || spec.charAt(i + 1) == ',')) {
                for (int c = 'A'; c <= 'Z'; c++) keywords.table[c] = !exclude;
                for (int c = 'a'; c <= 'z'; c++) keywords.table[c] = !exclude;
                i++;
                i = skipComma(spec, i);
                continue;
            } else {
                int[] first = item(spec, i);
                from = first[0];
                i = first[1];
                to = from;
                if (i < spec.length() && spec.charAt(i) == '-' && i + 1 < spec.length()) {
                    int[] second = item(spec, i + 1);
                    to = second[0];
                    i = second[1];
                }
            }
            if (from < 0 || to > 255 || from > to) throw new IllegalArgumentException("E474: Invalid argument: iskeyword=" + spec);
            for (int c = from; c <= to; c++) keywords.table[c] = !exclude;
            i = skipComma(spec, i);
        }
        return keywords;
    }

    /** @return a Java character-class body matching the same characters, for patterns ({@code \k}, {@code \<}) */
    String javaClass() {
        StringBuilder out = new StringBuilder();
        for (int c = 0; c < 256; c++) {
            if (!table[c]) continue;
            int end = c;
            while (end + 1 < 256 && table[end + 1]) end++;
            out.append(String.format("\\x{%x}", c));
            if (end > c) out.append(String.format("-\\x{%x}", end));
            c = end;
        }
        return out + dev.jasper.editor.vim.regex.VimRegex.WIDE;
    }

    /** @return whether Vim treats the character as a keyword character */
    boolean isWord(int c) { return c < 256 ? table[c] : charClass(c) >= 2; }

    /** @return Vim's class for the character: 0 blank, 1 punctuation, 2 word, 3 emoji, larger for CJK scripts */
    int charClass(int c) {
        if (c == ' ' || c == '\t' || c == 0 || c == 0xa0 || c == 0x3000) return 0;
        if (c < 0x100) return table[c] ? 2 : 1;
        if (c >= 0x1f000 && c <= 0x1faff || c >= 0x2600 && c <= 0x27bf) return 3;
        if (c >= 0x3040 && c <= 0x309f) return 0x3040;
        if (c >= 0x30a0 && c <= 0x30ff) return 0x30a0;
        if (c >= 0x4e00 && c <= 0x9fff || c >= 0x3400 && c <= 0x4dbf || c >= 0x20000 && c <= 0x2ffff) return 0x4e00;
        if (c >= 0xac00 && c <= 0xd7a3) return 0xac00;
        if (Character.isWhitespace(c)) return 0;
        return Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK ? 2 : 1;
    }

    private static int skipComma(String spec, int i) {
        if (i < spec.length() && spec.charAt(i) == ',') return i + 1;
        if (i < spec.length()) throw new IllegalArgumentException("E474: Invalid argument: iskeyword=" + spec);
        return i;
    }

    /** @return {value, index after it}: a decimal number or one character */
    private static int[] item(String spec, int i) {
        if (Character.isDigit(spec.charAt(i))) {
            int end = i;
            while (end < spec.length() && Character.isDigit(spec.charAt(end))) end++;
            return new int[] {Integer.parseInt(spec.substring(i, end)), end};
        }
        return new int[] {spec.charAt(i), i + 1};
    }
}
