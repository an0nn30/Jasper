package dev.jasper.editor.vim.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A Vim pattern translated to {@link Pattern} (spec 3.7). The lexer follows Vim's regexp.c (peekchr, skipchr), so
 * the magic levels {@code \v \m \M \V}, the position rules for {@code ^ $ *} and the backslash toggles behave as in
 * Vim; the parser follows regexp.c's regbranch, regconcat, regpiece and regatom, writing Java syntax instead of a
 * program. Text is matched as one string whose lines end with {@code \n}: {@code ^} and {@code $} are line starts and
 * ends, and only the {@code \_x} forms, {@code \n} and {@code \_.} match a line break.
 *
 * <p>{@code \zs} and {@code \ze} become empty named groups; {@link #start} and {@link #end} narrow a match with them.
 * Items Jasper does not support fail with {@code E869}, naming the item.
 */
public final class VimRegex {
    /**
     * What a pattern needs from its editor.
     *
     * @param ignoreCase 'ignorecase'
     * @param smartCase 'smartcase': an upper-case letter in the pattern turns 'ignorecase' off
     * @param keyword a Java character-class body for 'iskeyword' ({@code \k}, {@code \<}, {@code \>})
     * @param lastSubstitute the last substitute string, for {@code ~}, or null
     */
    public record Settings(boolean ignoreCase, boolean smartCase, String keyword, String lastSubstitute) {
        /** Vim's defaults: case matters, and the default 'iskeyword'. */
        public static final Settings DEFAULT = new Settings(false, false, "0-9A-Za-z_\\u00c0-\\u00ff" + WIDE, null);
    }

    /** Characters above U+00FF that Vim counts as word characters (utf_class above 1): all but punctuation and blanks. */
    public static final String WIDE = "[\\x{100}-\\x{10ffff}&&[^\\p{P}\\p{Zs}\\u3000]]";

    private static final int MAGIC = 0x40000000;
    private static final int NONE = 1, OFF = 2, ON = 3, ALL = 4;
    private static final String META = "%&()*+.123456789<=>?@ACDFHIKLMOPSUVWXZ[_acdfhiklmnopsuvwxz{|~";
    private static final String ABBREVIATIONS = "rteb";

    private final Pattern pattern;
    private final boolean ignoreCase;
    private final boolean hasStart, hasEnd, multiline;
    private final int groups;

    private VimRegex(Pattern pattern, boolean ignoreCase, boolean hasStart, boolean hasEnd, boolean multiline, int groups) {
        this.pattern = pattern;
        this.ignoreCase = ignoreCase;
        this.hasStart = hasStart;
        this.hasEnd = hasEnd;
        this.multiline = multiline;
        this.groups = groups;
    }

    /**
     * @param vim a Vim pattern
     * @param settings the editor's options
     * @return the translated pattern
     * @throws IllegalArgumentException with Vim's error message for a bad or unsupported pattern
     */
    public static VimRegex compile(String vim, Settings settings) {
        Translator t = new Translator(vim, settings);
        String java = t.translate();
        boolean ic = t.forceIgnore || !t.forceMatch && settings.ignoreCase() && !(settings.smartCase() && hasUpper(vim));
        int flags = Pattern.MULTILINE | Pattern.UNIX_LINES;
        if (ic) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return new VimRegex(Pattern.compile(java, flags), ic, t.hasStart, t.hasEnd, t.multiline, t.groups);
        } catch (PatternSyntaxException bad) {
            throw new IllegalArgumentException("E486: Pattern not found: " + vim, bad);
        }
    }

    /** @return whether the pattern contains an upper-case letter that is not after a backslash (pat_has_uppercase) */
    static boolean hasUpper(String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(i + 1);
                if (next == '_' && i + 2 < pattern.length()) i += 2;
                else if (next == '%' && i + 2 < pattern.length()) i += 2;
                else i++;
            } else if (Character.isUpperCase(c)) return true;
        }
        return false;
    }

    /** @return the Java pattern */
    public Pattern pattern() { return pattern; }

    /** @return whether the pattern ignores case */
    public boolean ignoreCase() { return ignoreCase; }

    /** @return whether a match may span lines (Vim's re_multiline) */
    public boolean multiline() { return multiline; }

    /**
     * @param m a matcher after a successful find
     * @param n a group number: 0 for the whole match (narrowed by {@code \zs} and {@code \ze}), 1 to 9 for
     *     {@code \(\)}
     * @return where the group starts, or -1 when it took no part in the match
     */
    public int groupStart(Matcher m, int n) {
        if (n == 0) return start(m);
        return n <= groups ? m.start("g" + n) : -1;
    }

    /** @return where the group ends, or -1 when it took no part in the match */
    public int groupEnd(Matcher m, int n) {
        if (n == 0) return end(m);
        return n <= groups ? m.end("g" + n) : -1;
    }

    /** @return where the match starts, after {@code \zs} */
    public int start(Matcher m) {
        if (hasStart && m.start("zs") >= 0) return m.start("zs");
        return m.start();
    }

    /** @return where the match ends, at {@code \ze} */
    public int end(Matcher m) {
        if (hasEnd && m.start("ze") >= 0) return m.start("ze");
        return m.end();
    }

    /** The lexer and parser: one use. */
    private static final class Translator {
        private final String src;
        private final Settings settings;
        private int pos;
        private int magic = ON;
        private int curchr = -1, prevchr = -1, prevprevchr = -1, nextchr = -1;
        private boolean atStart = true, prevAtStart;
        private int afterSlash;
        private int prevchrLen;
        boolean forceIgnore, forceMatch, hasStart, hasEnd;
        /** Whether a match may span lines ({@code \n}, the {@code \_} items, {@code \%^}, {@code \%$}): Vim's RF_HASNL. */
        boolean multiline;
        private int groups;

        Translator(String src, Settings settings) {
            this.src = src;
            this.settings = settings;
        }

        String translate() {
            String out = alternation(false);
            if (peek() != 0) {
                if (peek() == magic(')')) throw error("E16: Invalid range");
                throw error("E486: Pattern not found: " + src);
            }
            return out;
        }

        // --- the lexer (regexp.c: peekchr, skipchr, getchr) ---

        private static int magic(int c) { return c | MAGIC; }

        private static int noMagic(int c) { return c & ~MAGIC; }

        private static boolean isMagic(int c) { return c >= 0 && (c & MAGIC) != 0; }

        private static int toggle(int c) { return isMagic(c) ? noMagic(c) : magic(c); }

        private char at(int i) { return i < src.length() ? src.charAt(i) : 0; }

        /** @return the next item: a character, or {@code magic(c)} for a special one; 0 at the end */
        int peek() {
            if (curchr != -1) return curchr;
            char c = at(pos);
            curchr = c;
            switch (c) {
                case '.', '[', '~' -> { if (magic >= ON) curchr = magic(c); }
                case '(', ')', '{', '%', '+', '=', '?', '@', '!', '&', '|', '<', '>', '#', '"', '\'', ',', '-', ':', ';', '`', '/' -> {
                    if (magic == ALL) curchr = magic(c);
                }
                case '*' -> {
                    if (magic >= ON && !atStart && !(prevAtStart && prevchr == magic('^'))
                        && (afterSlash > 0 || prevchr != magic('(') && prevchr != magic('&') && prevchr != magic('|')))
                        curchr = magic('*');
                }
                case '^' -> {
                    if (magic >= OFF && (atStart || magic == ALL || prevchr == magic('(') || prevchr == magic('|')
                        || prevchr == magic('&') || prevchr == magic('n') || noMagic(prevchr) == '(' && prevprevchr == magic('%'))) {
                        curchr = magic('^');
                        atStart = true;
                        prevAtStart = false;
                    }
                }
                case '$' -> {
                    if (magic >= OFF) {
                        int p = pos + 1;
                        boolean all = magic == ALL;
                        while (at(p) == '\\' && "cCmMvVZ".indexOf(at(p + 1)) >= 0 && at(p + 1) != 0) {
                            if (at(p + 1) == 'v') all = true;
                            else if ("mMV".indexOf(at(p + 1)) >= 0) all = false;
                            p += 2;
                        }
                        if (at(p) == 0 || at(p) == '\\' && "|&)n".indexOf(at(p + 1)) >= 0 && at(p + 1) != 0
                            || all && "|&)".indexOf(at(p)) >= 0 && at(p) != 0 || magic == ALL)
                            curchr = magic('$');
                    }
                }
                case '\\' -> {
                    char n = at(pos + 1);
                    if (n == 0) curchr = '\\';
                    else if (META.indexOf(n) >= 0) {
                        curchr = -1;
                        prevAtStart = atStart;
                        atStart = false;
                        pos++;
                        afterSlash++;
                        peek();
                        pos--;
                        afterSlash--;
                        curchr = toggle(curchr);
                    } else if (ABBREVIATIONS.indexOf(n) >= 0) {
                        curchr = switch (n) {
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            case 'e' -> 0x1b;
                            default -> '\b';
                        };
                    } else if (magic == NONE && (n == '$' || n == '^')) curchr = toggle(n);
                    else curchr = Character.codePointAt(src, pos + 1);
                }
                default -> {
                    if (c != 0) curchr = Character.codePointAt(src, pos);
                }
            }
            return curchr;
        }

        void skip() {
            prevchrLen = at(pos) == '\\' ? 1 : 0;
            if (pos + prevchrLen < src.length()) prevchrLen += Character.charCount(src.codePointAt(pos + prevchrLen));
            pos += prevchrLen;
            prevAtStart = atStart;
            atStart = false;
            prevprevchr = prevchr;
            prevchr = curchr;
            curchr = nextchr;
            nextchr = -1;
        }

        /** skipchr_keepstart: for \c and the magic switches, which do not end the pattern's start. */
        void skipKeepStart() {
            boolean as = prevAtStart;
            int pr = prevchr, prpr = prevprevchr;
            skip();
            atStart = as;
            prevchr = pr;
            prevprevchr = prpr;
        }

        int get() {
            int c = peek();
            skip();
            return c;
        }

        /** Forgets the lookahead, after reading raw characters at {@code pos}. */
        void resync() {
            curchr = -1;
            nextchr = -1;
        }

        IllegalArgumentException error(String message) { return new IllegalArgumentException(message); }

        IllegalArgumentException unsupported(String item) { return error("E869: Unsupported in Jasper's Vim regex: " + item); }

        // --- the parser ---

        /** reg: branches separated by \| (inside a group when {@code group}). */
        String alternation(boolean group) {
            StringBuilder out = new StringBuilder(branch());
            while (peek() == magic('|')) {
                skip();
                out.append('|').append(branch());
            }
            if (group && peek() != magic(')')) throw error("E54: Unmatched \\(");
            return out.toString();
        }

        /** regbranch: concats separated by \&; each but the last must match at the same position. */
        String branch() {
            String first = concat();
            if (peek() != magic('&')) return first;
            StringBuilder out = new StringBuilder("(?=").append(first).append(')');
            String last = null;
            while (peek() == magic('&')) {
                skip();
                if (last != null) out.append("(?=").append(last).append(')');
                last = concat();
            }
            return "(?:" + out + last + ")";
        }

        String concat() {
            StringBuilder out = new StringBuilder();
            while (true) {
                int c = peek();
                if (c == 0 || c == magic('|') || c == magic('&') || c == magic(')')) break;
                if (c == magic('Z')) skipKeepStart();
                else if (c == magic('c')) {
                    forceIgnore = true;
                    skipKeepStart();
                } else if (c == magic('C')) {
                    forceMatch = true;
                    skipKeepStart();
                } else if (c == magic('v') || c == magic('m') || c == magic('M') || c == magic('V')) {
                    magic = switch (noMagic(c)) {
                        case 'v' -> ALL;
                        case 'm' -> ON;
                        case 'M' -> OFF;
                        default -> NONE;
                    };
                    skipKeepStart();
                    curchr = -1;
                } else out.append(piece());
            }
            return out.toString();
        }

        /** regpiece: an atom and its multi. */
        String piece() {
            String atom = atom();
            int op = peek();
            if (!isMulti(op)) return atom;
            skip();
            String result;
            switch (noMagic(op)) {
                case '*' -> result = group(atom) + "*";
                case '+' -> result = group(atom) + "+";
                case '=', '?' -> result = group(atom) + "?";
                case '@' -> result = lookaround(atom);
                default -> result = group(atom) + brace();
            }
            if (isMulti(peek())) throw error("E61: Nested " + (noMagic(peek()) == '*' ? "*" : "\\" + (char) noMagic(peek())));
            return result;
        }

        private static boolean isMulti(int c) {
            return c == magic('*') || c == magic('+') || c == magic('=') || c == magic('?') || c == magic('{') || c == magic('@');
        }

        private static String group(String atom) { return "(?:" + atom + ")"; }

        /**
         * The {@code \@} multis, after the {@code @}: {@code \@=} and {@code \@!} look ahead, {@code \@<=} and
         * {@code \@<!} (with a byte limit, {@code \@12<=}, which Java does not need) look behind, {@code \@>} matches
         * the atom as a whole.
         */
        String lookaround(String atom) {
            while (Character.isDigit(at(pos))) pos++;
            char kind = at(pos);
            String java;
            if (kind == '=' || kind == '!' || kind == '>') {
                pos++;
                java = kind == '=' ? "(?=" : kind == '!' ? "(?!" : "(?>";
            } else if (kind == '<' && (at(pos + 1) == '=' || at(pos + 1) == '!')) {
                java = at(pos + 1) == '=' ? "(?<=" : "(?<!";
                pos += 2;
                // Java looks behind only as far as it can count: repeats inside get a bound.
                atom = bounded(atom);
            } else throw error("E59: Invalid character after \\@");
            resync();
            return java + atom + ")";
        }

        /** How far a repeat inside a look-behind may reach. */
        private static final int BEHIND = 1000;

        /** A Java pattern whose unbounded repeats ({@code *}, {@code +}, {@code {n,}}) outside classes reach {@link #BEHIND}. */
        static String bounded(String java) {
            StringBuilder out = new StringBuilder();
            int classes = 0;
            for (int i = 0; i < java.length(); i++) {
                char c = java.charAt(i);
                if (c == '\\' && i + 1 < java.length()) {
                    if (java.charAt(i + 1) == 'Q') {
                        int end = java.indexOf("\\E", i + 2);
                        end = end < 0 ? java.length() : end + 2;
                        out.append(java, i, end);
                        i = end - 1;
                    } else {
                        out.append(c).append(java.charAt(i + 1));
                        i++;
                    }
                } else if (c == '[') {
                    classes++;
                    out.append(c);
                } else if (c == ']' && classes > 0) {
                    classes--;
                    out.append(c);
                } else if (classes == 0 && (c == '*' || c == '+') && i > 0 && java.charAt(i - 1) != '(') {
                    out.append(c == '*' ? "{0," : "{1,").append(BEHIND).append('}');
                } else if (classes == 0 && c == '{') {
                    int close = java.indexOf('}', i);
                    String inside = close < 0 ? "" : java.substring(i + 1, close);
                    if (inside.matches("\\d+,")) {
                        out.append('{').append(inside).append(BEHIND).append('}');
                        i = close;
                    } else out.append(c);
                } else out.append(c);
            }
            return out.toString();
        }

        /** read_limits: {@code \{n,m}}, {@code \{-n,m}} and the rest, after the brace. */
        String brace() {
            boolean lazy = false;
            if (at(pos) == '-') {
                lazy = true;
                pos++;
            }
            int start = pos;
            while (Character.isDigit(at(pos))) pos++;
            String min = src.substring(start, pos);
            String max = min;
            boolean comma = false;
            if (at(pos) == ',') {
                comma = true;
                pos++;
                int s = pos;
                while (Character.isDigit(at(pos))) pos++;
                max = src.substring(s, pos);
            }
            if (at(pos) == '\\') pos++;
            if (at(pos) != '}') throw error("E554: Syntax error in \\{...}");
            pos++;
            resync();
            String java;
            if (min.isEmpty() && !comma) java = "*";
            else if (!comma) java = "{" + min + "}";
            else {
                int lo = min.isEmpty() ? 0 : Integer.parseInt(min);
                if (!max.isEmpty() && Integer.parseInt(max) < lo) {
                    // Vim swaps the limits: \{3,1} is \{1,3}.
                    java = "{" + max + "," + lo + "}";
                } else java = "{" + lo + "," + max + "}";
            }
            return lazy ? java + "?" : java;
        }

        /** regatom. */
        String atom() {
            int c = get();
            if (c == magic('^')) return "^";
            if (c == magic('$')) return "$";
            if (c == magic('<')) return "(?<![" + settings.keyword() + "])(?=[" + settings.keyword() + "])";
            if (c == magic('>')) return "(?<=[" + settings.keyword() + "])(?![" + settings.keyword() + "])";
            if (c == magic('_')) {
                multiline = true;
                char n = at(pos);
                if (n == '^') {
                    pos++;
                    resync();
                    return "^";
                }
                if (n == '$') {
                    pos++;
                    resync();
                    return "$";
                }
                if (n == '[') {
                    pos++;
                    resync();
                    return bracket(true);
                }
                if (n == '.') {
                    pos++;
                    resync();
                    return "[\\s\\S]";
                }
                String cls = classFor(n);
                if (cls == null) throw error("E63: Invalid use of \\_");
                pos++;
                resync();
                return "(?:\\n|" + cls + ")";
            }
            if (c == magic('.')) return ".";
            if (isMagic(c) && classFor(noMagic(c)) != null) return classFor(noMagic(c));
            if (c == magic('n')) {
                multiline = true;
                return "\\n";
            }
            if (c == magic('(')) {
                String name = "g" + ++groups;
                if (groups > 9) throw error("E51: Too many \\(");
                String inner = alternation(true);
                skip();
                return "(?<" + name + ">" + inner + ")";
            }
            if (c == 0 || c == magic('|') || c == magic('&') || c == magic(')')) throw error("E486: Pattern not found: " + src);
            if (c == magic('=') || c == magic('?') || c == magic('+') || c == magic('@') || c == magic('{') || c == magic('*'))
                throw error("E64: " + (noMagic(c) == '*' ? "*" : "\\" + (char) noMagic(c)) + " follows nothing");
            if (c == magic('~')) {
                String last = settings.lastSubstitute();
                return last == null || last.isEmpty() ? "" : Pattern.quote(last);
            }
            if (isMagic(c) && noMagic(c) >= '1' && noMagic(c) <= '9') {
                int n = noMagic(c) - '0';
                if (n > groups) throw error("E65: Illegal back reference");
                return "\\k<g" + n + ">";
            }
            if (c == magic('z')) {
                char n = at(pos);
                pos++;
                resync();
                if (n == 's') {
                    if (hasStart) throw unsupported("\\zs twice");
                    hasStart = true;
                    return "(?<zs>)";
                }
                if (n == 'e') {
                    if (hasEnd) throw unsupported("\\ze twice");
                    hasEnd = true;
                    return "(?<ze>)";
                }
                throw unsupported("\\z" + n);
            }
            if (c == magic('%')) return percent();
            if (c == magic('[')) return bracket(false);
            if (isMagic(c)) {
                int plain = noMagic(c);
                if (Character.isLetterOrDigit(plain) || plain == '_') throw unsupported("\\" + (char) plain);
                return literal(plain);
            }
            return literal(c);
        }

        /** The \% items. */
        String percent() {
            char n = at(pos);
            switch (n) {
                case '(' -> {
                    pos++;
                    resync();
                    prevchr = magic('(');
                    String inner = alternation(true);
                    skip();
                    return "(?:" + inner + ")";
                }
                case '^' -> {
                    pos++;
                    resync();
                    multiline = true;
                    return "\\A";
                }
                case '$' -> {
                    pos++;
                    resync();
                    multiline = true;
                    return "(?=\\n?\\z)";
                }
                case '[' -> {
                    pos++;
                    resync();
                    return optionalSequence();
                }
                case 'd', 'x', 'o', 'u', 'U' -> {
                    pos++;
                    int radix = n == 'd' ? 10 : n == 'o' ? 8 : 16;
                    int limit = n == 'x' ? 2 : n == 'u' ? 4 : n == 'U' ? 8 : Integer.MAX_VALUE;
                    int start = pos;
                    while (pos - start < limit && Character.digit(at(pos), radix) >= 0) pos++;
                    if (pos == start) throw error("E678: Invalid character after \\%[dxouU]");
                    resync();
                    return literal(Integer.parseInt(src.substring(start, pos), radix));
                }
                default -> {
                    String column = column();
                    if (column != null) return column;
                    int end = pos + 1;
                    while (end < src.length() && (Character.isDigit(src.charAt(end)) || "<>'.".indexOf(src.charAt(end)) >= 0)) end++;
                    throw unsupported("\\%" + src.substring(pos, Math.min(src.length(), Math.max(end, pos + 1) + (end > pos + 1 ? 1 : 0))));
                }
            }
        }

        /**
         * {@code \%23c}, {@code \%<23c} and {@code \%>23c}: the column, counted from 1, is 23, before it or after it.
         * Jasper counts characters where Vim counts bytes, which differs only in lines with characters beyond ASCII.
         *
         * @return the Java pattern, or null when this is not a column item
         */
        String column() {
            int p = pos;
            char cmp = at(p);
            if (cmp == '<' || cmp == '>') p++;
            int start = p;
            while (Character.isDigit(at(p))) p++;
            if (p == start || at(p) != 'c') return null;
            long n = Long.parseLong(src.substring(start, p));
            pos = p + 1;
            resync();
            if (cmp == '<') return n <= 1 ? "(?!)" : "(?<=^.{0," + (n - 2) + "})";
            if (cmp == '>') return n <= 0 ? "" : "(?<!^.{0," + (n - 1) + "})";
            return n <= 0 ? "(?!)" : "(?<=^.{" + (n - 1) + "})";
        }

        /** \%[abc]: each of the atoms matches in turn, as far as they go. */
        String optionalSequence() {
            java.util.List<String> atoms = new java.util.ArrayList<>();
            while (true) {
                if (at(pos) == ']') {
                    pos++;
                    resync();
                    break;
                }
                if (at(pos) == 0) throw error("E69: Missing ] after \\%[");
                atoms.add(atom());
                pos = pos - (curchr != -1 ? 0 : 0);
                resync();
            }
            if (atoms.isEmpty()) throw error("E70: Empty \\%[]");
            String out = "";
            for (int i = atoms.size() - 1; i >= 0; i--) out = "(?:" + atoms.get(i) + out + ")?";
            return out;
        }

        /** A bracket expression, from after the {@code [}; {@code [} alone is a literal when there is no {@code ]}. */
        String bracket(boolean newline) {
            int start = pos;
            int end = bracketEnd(pos);
            if (end < 0) {
                // No closing ]: a literal [ (Vim's rule, even for \_[).
                if (newline) throw error("E769: Missing ] after \\_[");
                return "\\[";
            }
            StringBuilder out = new StringBuilder("[");
            int i = start;
            if (at(i) == '^') {
                out.append('^');
                i++;
            }
            if (at(i) == ']' || at(i) == '-') {
                out.append('\\').append(at(i));
                i++;
            }
            while (i < end) {
                char c = src.charAt(i);
                if (c == '[' && at(i + 1) == ':') {
                    int close = src.indexOf(":]", i + 2);
                    String name = src.substring(i + 2, close);
                    out.append(posixClass(name));
                    i = close + 2;
                    continue;
                }
                if (c == '[' && (at(i + 1) == '=' || at(i + 1) == '.')) throw unsupported("[" + at(i + 1) + "…" + at(i + 1) + "]");
                if (c == '-' && i + 1 < end && i > start) {
                    out.append('-');
                    i++;
                    continue;
                }
                if (c == '\\' && i + 1 < end) {
                    char n = src.charAt(i + 1);
                    int[] consumed = new int[1];
                    int code = bracketEscape(i + 1, consumed);
                    if (code >= 0) {
                        out.append(classLiteral(code));
                        i += 1 + consumed[0];
                        continue;
                    }
                    if (n == '\\' || n == ']' || n == '^' || n == '-') {
                        out.append(classLiteral(n));
                        i += 2;
                        continue;
                    }
                    out.append(classLiteral('\\'));
                    i++;
                    continue;
                }
                int cp = src.codePointAt(i);
                out.append(classLiteral(cp));
                i += Character.charCount(cp);
            }
            out.append(']');
            pos = end + 1;
            resync();
            String cls = out.toString();
            if (cls.startsWith("[^")) cls = "[^\\n" + cls.substring(2);
            return newline ? "(?:\\n|" + cls + ")" : cls;
        }

        /** @return the index of the ] closing a bracket expression that starts at {@code i}, or -1 */
        private int bracketEnd(int i) {
            if (at(i) == '^') i++;
            if (at(i) == ']' || at(i) == '-') i++;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c == ']') return i;
                if (c == '[' && at(i + 1) == ':') {
                    int close = src.indexOf(":]", i + 2);
                    if (close > 0 && posixName(src.substring(i + 2, close))) {
                        i = close + 2;
                        continue;
                    }
                }
                if (c == '\\' && i + 1 < src.length()) i++;
                i++;
            }
            return -1;
        }

        /** \e \t \r \b \n \d123 \o40 \x20 € inside brackets; -1 for anything else. */
        private int bracketEscape(int i, int[] consumed) {
            char n = at(i);
            consumed[0] = 1;
            switch (n) {
                case 'e': return 0x1b;
                case 't': return '\t';
                case 'r': return '\r';
                case 'b': return '\b';
                case 'n': return '\n';
                case 'd': case 'o': case 'x': case 'u': case 'U': {
                    int radix = n == 'd' ? 10 : n == 'o' ? 8 : 16;
                    int limit = n == 'x' ? 2 : n == 'u' ? 4 : n == 'U' ? 8 : 10;
                    int j = i + 1;
                    while (j - i - 1 < limit && Character.digit(at(j), radix) >= 0) j++;
                    if (j == i + 1) return -1;
                    consumed[0] = j - i;
                    return Integer.parseInt(src.substring(i + 1, j), radix);
                }
                default: return -1;
            }
        }

        private static boolean posixName(String name) {
            return switch (name) {
                case "alnum", "alpha", "blank", "cntrl", "digit", "graph", "lower", "print", "punct", "space", "upper", "xdigit",
                     "return", "tab", "escape", "backspace", "ident", "keyword", "fname" -> true;
                default -> false;
            };
        }

        private String posixClass(String name) {
            return switch (name) {
                case "alnum" -> "0-9A-Za-z";
                case "alpha" -> "A-Za-z";
                case "blank" -> " \\t";
                case "cntrl" -> "\\x00-\\x1f\\x7f";
                case "digit" -> "0-9";
                case "graph" -> "!-~";
                case "lower" -> "\\p{javaLowerCase}";
                case "print" -> " -~";
                case "punct" -> "!-/:-@\\[-`{-~";
                case "space" -> "\\t\\n\\x0b\\f\\r ";
                case "upper" -> "\\p{javaUpperCase}";
                case "xdigit" -> "0-9A-Fa-f";
                case "return" -> "\\r";
                case "tab" -> "\\t";
                case "escape" -> "\\x1b";
                case "backspace" -> "\\x08";
                case "ident" -> "0-9A-Za-z_\\u00c0-\\u00ff";
                case "keyword" -> settings.keyword();
                case "fname" -> "0-9A-Za-z/.\\-_+,#$%~=\\u00c0-\\u00ff";
                default -> throw error("E486: Pattern not found: " + src);
            };
        }

        /** The character classes \s \d \w and the rest; null for another letter. */
        String classFor(int c) {
            String k = settings.keyword();
            return switch (c) {
                case 's' -> "[ \\t]";
                case 'S' -> "[^ \\t\\n]";
                case 'd' -> "[0-9]";
                case 'D' -> "[^0-9\\n]";
                case 'w' -> "[0-9A-Za-z_]";
                case 'W' -> "[^0-9A-Za-z_\\n]";
                case 'a' -> "[A-Za-z]";
                case 'A' -> "[^A-Za-z\\n]";
                case 'l' -> "[a-z]";
                case 'L' -> "[^a-z\\n]";
                case 'u' -> "[A-Z]";
                case 'U' -> "[^A-Z\\n]";
                case 'x' -> "[0-9A-Fa-f]";
                case 'X' -> "[^0-9A-Fa-f\\n]";
                case 'o' -> "[0-7]";
                case 'O' -> "[^0-7\\n]";
                case 'h' -> "[A-Za-z_]";
                case 'H' -> "[^A-Za-z_\\n]";
                case 'k' -> "[" + k + "]";
                case 'K' -> "[" + k + "&&[^0-9]]";
                case 'i' -> "[0-9A-Za-z_\\u00c0-\\u00ff]";
                case 'I' -> "[A-Za-z_\\u00c0-\\u00ff]";
                case 'f' -> "[0-9A-Za-z/.\\-_+,#$%~=\\u00c0-\\u00ff]";
                case 'F' -> "[A-Za-z/.\\-_+,#$%~=\\u00c0-\\u00ff]";
                case 'p' -> "[^\\x00-\\x1f\\x7f-\\xa0\\n]";
                case 'P' -> "[^\\x00-\\x1f\\x7f-\\xa0\\n0-9]";
                default -> null;
            };
        }

        static String literal(int c) {
            if (c < 0x20 || c == 0x7f) return String.format("\\x{%x}", c);
            if (Character.isLetterOrDigit(c) || c > 0x7f) return new String(Character.toChars(c));
            return "\\" + (char) c;
        }

        static String classLiteral(int c) {
            if (c < 0x20 || c == 0x7f) return String.format("\\x{%x}", c);
            if (Character.isLetterOrDigit(c) || c > 0x7f) return new String(Character.toChars(c));
            return "\\" + (char) c;
        }
    }
}
