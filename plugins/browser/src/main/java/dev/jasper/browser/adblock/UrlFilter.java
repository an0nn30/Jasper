package dev.jasper.browser.adblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A rule's {@code url-filter}: the regular expression an address must match, in WebKit's grammar alone, as a list
 * that may be hostile gives it. That grammar is the printable ASCII characters the grammar gives no meaning to,
 * {@code .}, a character class ({@code [a-z]}, {@code [^/]}), a group, {@code *}, {@code +} and {@code ?} after any of
 * them, {@code ^} first and {@code $} last, and {@code \} before ASCII punctuation, which then stands for itself.
 * Anything else is refused: alternatives ({@code |}), counted repeats ({@code {2}}), {@code (?}, back-references and
 * escapes such as {@code \d}. So that no match takes long on Chromium's IO thread, which every request waits on, a
 * filter has at most 2,048 characters, 4 unbounded repeats ({@code *} and {@code +}) and 8 groups one inside another,
 * a group repeated by an unbounded repeat holds no repeat of its own ({@code (a+)+}, on which Java would backtrack
 * without bound; a group after {@code ?} may), a match reads at most the first 8 KiB of an address, and it gives up
 * after 100,000 reads of a character: a run over that budget counts as no match, and is logged once per filter.
 *
 * <p>Each filter has its literal, the longest run of characters every match holds, by which the rule set's index
 * finds the rules an address may match. The expression is compiled on a filter's first match, and is safe to match
 * from any thread.
 */
public final class UrlFilter {
    /** How many characters a filter has at most. */
    static final int MAX_LENGTH = 2_048;
    /** How many {@code *} and {@code +} a filter has at most. */
    static final int MAX_UNBOUNDED = 4;
    /** How many groups a filter has one inside another at most. */
    static final int MAX_NESTING = 8;
    /** How many characters of an address a match reads, from its start. */
    static final int URL_LIMIT = 8 * 1024;
    /** How many times a match may read a character of the address. */
    static final int STEPS = 100_000;
    private static final System.Logger LOG = System.getLogger(UrlFilter.class.getName());
    private static final String PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    // The characters that mean something in a filter, or are refused there; each stands for itself only escaped.
    private static final String SPECIAL = "\\.[]()*+?^$|{}";

    private final String source;
    private final boolean caseSensitive;
    private final String literal;
    private final boolean everything;
    private final AtomicBoolean logged = new AtomicBoolean();
    private volatile Pattern pattern;
    private volatile boolean overflowed;

    private UrlFilter(String source, boolean caseSensitive, String literal, boolean everything) {
        this.source = source;
        this.caseSensitive = caseSensitive;
        this.literal = literal;
        this.everything = everything;
    }

    /**
     * The filter {@code source} is, if it keeps to WebKit's grammar and to the bounds above.
     *
     * @param source a rule's {@code url-filter}
     * @param caseSensitive whether the rule's {@code url-filter-is-case-sensitive} is true; otherwise letters match
     *     either case
     * @return the filter; empty for anything else, and for an empty filter
     */
    public static Optional<UrlFilter> of(String source, boolean caseSensitive) {
        if (source == null || source.isEmpty() || source.length() > MAX_LENGTH) return Optional.empty();
        try {
            Parsed parsed = new Parser(source).parse();
            return Optional.of(new UrlFilter(source, caseSensitive, caseSensitive ? parsed.literal() : fold(parsed.literal()),
                parsed.everything()));
        } catch (Refused refused) {
            return Optional.empty();
        }
    }

    /**
     * The filter as its list wrote it.
     *
     * @return the source
     */
    public String source() {
        return source;
    }

    /**
     * Whether letters match only in their own case.
     *
     * @return whether the rule said so
     */
    public boolean caseSensitive() {
        return caseSensitive;
    }

    /**
     * The longest run of the filter's top-level characters that every match holds: plain and escaped characters, the
     * one before a {@code +} ending its run and staying in it, the one before a {@code *} or {@code ?} ending its run
     * and left out, and {@code .}, a class, a group, {@code ^} and {@code $} ending runs. In lower case unless the
     * filter is case-sensitive.
     *
     * @return the run, such as {@code doubleclick.net}; empty when the filter has none
     */
    public Optional<String> literal() {
        return literal.isEmpty() ? Optional.empty() : Optional.of(literal);
    }

    /** {@link #literal()}, or the empty text when there is none. */
    String literalText() {
        return literal;
    }

    /** Whether every address matches, as {@code .*} does, so a match needs no expression. */
    boolean matchesEverything() {
        return everything;
    }

    /**
     * Whether the filter matches somewhere in the address's first 8 KiB, in either case unless it is case-sensitive.
     * A match over its budget of 100,000 reads counts as no match. A filter whose expression overflows the thread's
     * stack, compiling or matching, matches nothing from then on.
     *
     * @param url the address
     * @return whether it matches
     */
    public boolean matches(String url) {
        Objects.requireNonNull(url, "url");
        if (everything) return true;
        if (overflowed) return false;
        try {
            return compiled().matcher(new Budget(url)).find();
        } catch (Budget.Spent spent) {
            log("ran out of steps matching an address, which counts as no match");
            return false;
        } catch (StackOverflowError | PatternSyntaxException overflow) {
            // Java's expressions recurse once a node, compiling and matching, and Java reports a stack it overflowed
            // compiling as a syntax error; on a thread with a small stack, a long filter can overflow it.
            overflowed = true;
            log("overflowed the stack, and matches nothing from now on");
            return false;
        }
    }

    private void log(String what) {
        if (logged.compareAndSet(false, true))
            LOG.log(System.Logger.Level.WARNING, "A content blocker rule " + what + "; it is not logged again: url-filter " + source);
    }

    // Compiled on first use; two threads may each compile it once, and either expression serves.
    private Pattern compiled() {
        Pattern compiled = pattern;
        if (compiled == null) {
            String expression;
            try {
                expression = new Parser(source).parse().expression();
            } catch (Refused refused) {
                throw new IllegalStateException("A filter refused after it was accepted: " + source);
            }
            pattern = compiled = Pattern.compile(expression, Pattern.DOTALL | (caseSensitive ? 0 : Pattern.CASE_INSENSITIVE));
        }
        return compiled;
    }

    /** {@code text} with its ASCII capital letters made small, as a filter's letters match in either case. */
    static String fold(String text) {
        int at = 0;
        while (at < text.length() && !capital(text.charAt(at))) at++;
        if (at == text.length()) return text;
        char[] chars = text.toCharArray();
        for (; at < chars.length; at++) if (capital(chars[at])) chars[at] += 'a' - 'A';
        return new String(chars);
    }

    private static boolean capital(char c) {
        return c >= 'A' && c <= 'Z';
    }

    @Override public boolean equals(Object other) {
        return other instanceof UrlFilter filter && filter.source.equals(source) && filter.caseSensitive == caseSensitive;
    }

    @Override public int hashCode() {
        return source.hashCode() * 31 + Boolean.hashCode(caseSensitive);
    }

    @Override public String toString() {
        return caseSensitive ? source + " (case-sensitive)" : source;
    }

    /** Not a filter in WebKit's grammar or within the bounds. Carries no stack: it only ends the reading. */
    private static final class Refused extends Exception {
        private static final Refused REFUSED = new Refused();

        private Refused() {
            super(null, null, false, false);
        }
    }

    /**
     * What a filter reads as: its literal, the Java expression a match finds, with {@code .*} at either end left out,
     * as {@code .} matches every character there, and whether it matches every address.
     */
    private record Parsed(String literal, String expression, boolean everything) { }

    /**
     * One top-level term of a filter, or of a group's.
     *
     * @param expression the term as Java's grammar writes it, each character but a letter or digit escaped
     * @param literal the character a plain or escaped atom stands for; -1 for {@code .}, a class or a group
     * @param dot whether the atom is {@code .}
     * @param holdsQuantifier whether the atom is a group that holds a {@code *}, {@code +} or {@code ?}
     * @param quantifier the {@code *}, {@code +} or {@code ?} after the atom; 0 when none
     */
    private record Piece(String expression, int literal, boolean dot, boolean holdsQuantifier, char quantifier) {
        Piece quantified(char by) {
            return new Piece(expression + by, literal, dot, holdsQuantifier, by);
        }

        boolean anything() {
            return dot && quantifier == '*';
        }
    }

    /** Reads one filter, front to back, refusing anything outside WebKit's grammar or the bounds. */
    private static final class Parser {
        private final String source;
        private int at, depth, quantifiers, unbounded;

        Parser(String source) {
            this.source = source;
        }

        Parsed parse() throws Refused {
            boolean start = source.charAt(0) == '^';
            if (start) at++;
            List<Piece> pieces = sequence(false);
            // The sequence stops short of the end only at a $ that is the filter's last character.
            boolean end = at < source.length();
            String literal = literal(pieces);
            int first = 0, last = pieces.size();
            for (; first < last && pieces.get(first).anything(); first++) start = false;
            for (; last > first && pieces.get(last - 1).anything(); last--) end = false;
            var expression = new StringBuilder(start ? "^" : "");
            for (Piece piece : pieces.subList(first, last)) expression.append(piece.expression());
            if (end) expression.append("\\z");
            return new Parsed(literal, expression.toString(), first == last && !(start && end));
        }

        /** The terms up to the end of the filter, a top-level {@code $}, or the {@code )} that ends the group. */
        private List<Piece> sequence(boolean group) throws Refused {
            var pieces = new ArrayList<Piece>();
            while (at < source.length()) {
                char c = source.charAt(at);
                if (c == ')') {
                    if (!group) throw Refused.REFUSED;
                    return pieces;
                }
                if (c == '$') {
                    if (group || at != source.length() - 1) throw Refused.REFUSED;
                    return pieces;
                }
                pieces.add(quantified(atom()));
            }
            if (group) throw Refused.REFUSED;
            return pieces;
        }

        private Piece atom() throws Refused {
            char c = source.charAt(at++);
            return switch (c) {
                case '.' -> new Piece(".", -1, true, false, (char) 0);
                case '[' -> characterClass();
                case '(' -> group();
                case '\\' -> character(escape());
                default -> {
                    if (c < 0x20 || c > 0x7E || SPECIAL.indexOf(c) >= 0) throw Refused.REFUSED;
                    yield character(c);
                }
            };
        }

        private static Piece character(char c) {
            return new Piece(escaped(c), c, false, false, (char) 0);
        }

        /** The ASCII punctuation after a backslash. */
        private char escape() throws Refused {
            if (at >= source.length()) throw Refused.REFUSED;
            char c = source.charAt(at++);
            if (PUNCTUATION.indexOf(c) < 0) throw Refused.REFUSED;
            return c;
        }

        private Piece group() throws Refused {
            if (at < source.length() && source.charAt(at) == '?' || ++depth > MAX_NESTING) throw Refused.REFUSED;
            int before = quantifiers;
            List<Piece> inner = sequence(true);
            at++;
            depth--;
            if (inner.isEmpty()) throw Refused.REFUSED;
            var expression = new StringBuilder("(?:");
            for (Piece piece : inner) expression.append(piece.expression());
            return new Piece(expression.append(')').toString(), -1, false, quantifiers > before, (char) 0);
        }

        private Piece characterClass() throws Refused {
            var expression = new StringBuilder("[");
            if (at < source.length() && source.charAt(at) == '^') {
                at++;
                expression.append('^');
            }
            for (int items = 0; ; items++) {
                if (at >= source.length()) throw Refused.REFUSED;
                if (source.charAt(at) == ']') {
                    if (items == 0) throw Refused.REFUSED;
                    at++;
                    return new Piece(expression.append(']').toString(), -1, false, false, (char) 0);
                }
                char low = classCharacter();
                if (at + 1 < source.length() && source.charAt(at) == '-' && source.charAt(at + 1) != ']') {
                    at++;
                    char high = classCharacter();
                    if (high < low) throw Refused.REFUSED;
                    expression.append(escaped(low)).append('-').append(escaped(high));
                } else expression.append(escaped(low));
            }
        }

        private char classCharacter() throws Refused {
            char c = source.charAt(at++);
            if (c == '\\') return escape();
            if (c < 0x20 || c > 0x7E) throw Refused.REFUSED;
            return c;
        }

        /** The atom with the quantifier after it, if any, within the bounds on repeats. */
        private Piece quantified(Piece atom) throws Refused {
            if (at >= source.length()) return atom;
            char by = source.charAt(at);
            if (by != '*' && by != '+' && by != '?') return atom;
            at++;
            quantifiers++;
            if (by != '?' && (++unbounded > MAX_UNBOUNDED || atom.holdsQuantifier())) throw Refused.REFUSED;
            return atom.quantified(by);
        }

        /** The longest run of top-level literal atoms, as {@link UrlFilter#literal()} describes it. */
        private static String literal(List<Piece> pieces) {
            var run = new StringBuilder();
            String best = "";
            for (Piece piece : pieces) {
                if (piece.literal() >= 0 && piece.quantifier() != '*' && piece.quantifier() != '?') {
                    run.append((char) piece.literal());
                    if (piece.quantifier() == 0) continue;
                }
                if (run.length() > best.length()) best = run.toString();
                run.setLength(0);
            }
            return run.length() > best.length() ? run.toString() : best;
        }

        /** {@code c} as Java's grammar takes it for itself, in a class or out: escaped unless a letter or digit. */
        private static String escaped(char c) {
            boolean plain = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
            return plain ? String.valueOf(c) : "\\" + c;
        }
    }

    /**
     * The first 8 KiB of an address, which a match may read 100,000 times at most; the next read ends the match.
     */
    private static final class Budget implements CharSequence {
        private final String text;
        private final int length;
        private int steps;

        Budget(String text) {
            this.text = text;
            length = Math.min(text.length(), URL_LIMIT);
        }

        @Override public int length() {
            return length;
        }

        @Override public char charAt(int index) {
            if (++steps > STEPS) throw Spent.SPENT;
            return text.charAt(Objects.checkIndex(index, length));
        }

        @Override public CharSequence subSequence(int start, int end) {
            return toString().substring(start, end);
        }

        @Override public String toString() {
            return text.substring(0, length);
        }

        /** The budget is spent. Carries no stack: it only ends the match. */
        private static final class Spent extends RuntimeException {
            private static final Spent SPENT = new Spent();

            private Spent() {
                super(null, null, false, false);
            }
        }
    }
}
