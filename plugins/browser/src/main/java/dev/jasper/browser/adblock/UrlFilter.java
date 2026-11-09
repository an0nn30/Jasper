package dev.jasper.browser.adblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A rule's {@code url-filter}: the regular expression an address must match, in WebKit's grammar alone, as a list
 * that may be hostile gives it. That grammar is the printable ASCII characters the grammar gives no meaning to,
 * {@code .}, a character class ({@code [a-z]}, {@code [^/]}), a group, {@code *}, {@code +} and {@code ?} after any of
 * them, {@code ^} first and {@code $} last, and {@code \} before ASCII punctuation, which then stands for itself.
 * Anything else is refused: alternatives ({@code |}), counted repeats ({@code {2}}), {@code (?}, back-references and
 * escapes such as {@code \d}.
 *
 * <p>So that no match takes long on Chromium's IO and UI threads, which every request and every page load wait on, a
 * filter has at most 2,048 characters, 256 quantified atoms, 4 unbounded repeats ({@code *} and {@code +}) and 8
 * groups one inside another; a group repeated by an unbounded repeat holds no repeat of its own ({@code (a+)+}, on
 * which Java would backtrack without bound; a group after {@code ?} may); and no quantified group can match the empty
 * text ({@code (x?)?}), whose two ways through would each go on without reading a character. A match reads at most
 * the first 8 KiB of an address. Each read of a character is charged 1, and 1 more for each of the filter's atoms
 * quantified by {@code *} or {@code ?}, each of which may run a step of the match without a read; a match charged past
 * 100,000 gives up, which counts as no match, and the charge also counts against the budget of the call the match is
 * part of, which a {@link RuleSet} gives each request and page.
 * Character classes are written out as their member characters, so that a read costs the same whatever the class,
 * and a class of one member as that character.
 *
 * <p>Each filter has its literal, the longest run of characters every match holds, by which the rule set's index
 * finds the rules an address may match. A rule set keeps its filters' compiled expressions in a {@link PatternCache}
 * (ruling V48), which charges each compile to the call that needs it ({@link #compileCost()}) and keeps them within a
 * weight each filter's parse estimates ({@link #weight()}). A filter is immutable but for its record of an overflowed
 * stack, and safe to match from any thread.
 */
public final class UrlFilter {
    /** How many characters a filter has at most. */
    static final int MAX_LENGTH = 2_048;
    /** How many {@code *}, {@code +} and {@code ?} a filter has at most. */
    static final int MAX_QUANTIFIED = 256;
    /** How many {@code *} and {@code +} a filter has at most. */
    static final int MAX_UNBOUNDED = 4;
    /** How many groups a filter has one inside another at most. */
    static final int MAX_NESTING = 8;
    /** How many characters of an address a match reads, from its start. */
    static final int URL_LIMIT = 8 * 1024;
    /** How many times a match may read a character of the address. */
    static final int STEPS = 100_000;
    /**
     * What a compiled expression is estimated to keep for each of its nodes (V48): a character, {@code .}, a class, a
     * quantifier, an anchor, and each end of a group. Measured on Java 25: 48 bytes for a character Java keeps apart
     * from its neighbours, matched in either case, and at most 40 a node across a filter, for {@code a?b} repeated.
     */
    static final int NODE_BYTES = 48;
    /**
     * What it keeps for each character class besides its node: the 256-entry bitmap Java keeps it as, 288 bytes with
     * its object, and the predicate around it; measured at most 288 a class.
     */
    static final int CLASS_BYTES = 320;
    /** What it keeps whatever its nodes: the pattern, its first node and its place in the cache; measured at about 250. */
    static final int PATTERN_BYTES = 256;
    /**
     * What it keeps for the table Java builds for a case-sensitive expression that begins with a run of 4 characters
     * or more, besides 4 bytes for each character of the run.
     */
    static final int TABLE_BYTES = 576;
    /**
     * What a compile is charged for each character of what it reads (V48), its source parsed again or its expression,
     * the longer: calibrated on this Mac so that a call's 2,000,000 pays for about 10 ms of compiles, the slowest a
     * character measured being 37 ns, for classes of two members, case-sensitive.
     */
    static final int COMPILE_CHARGE = 8;
    /** What a compile is charged whatever its expression's length, for the pattern it makes: 53 to 340 ns measured. */
    static final int COMPILE_BASE = 50;
    /**
     * A compile of a case-sensitive expression that begins with a run of 4 characters or more is charged the square of
     * the run divided by this, as Java's table for the run takes time that grows with its square: up to 0.45 ns for
     * each, measured, before the JIT has made that code fast.
     */
    static final int RUN_SQUARE = 8;
    private static final String PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    // The characters that mean something in a filter, or are refused there; each stands for itself only escaped.
    private static final String SPECIAL = "\\.[]()*+?^$|{}";

    private final String source;
    private final boolean caseSensitive;
    private final String literal;
    private final boolean everything, web;
    // What one read of a character is charged: 1, and 1 for each atom quantified by * or ? in the expression.
    private final int cost;
    // What the compiled expression is estimated to keep, in bytes, and what its compile is charged (V48).
    private final int weight, compileCost;
    private volatile boolean overflowed;

    private UrlFilter(String source, boolean caseSensitive, String literal, boolean everything, boolean web, int cost, int weight,
                      int compileCost) {
        this.source = source;
        this.caseSensitive = caseSensitive;
        this.literal = literal;
        this.everything = everything;
        this.web = web;
        this.cost = cost;
        this.weight = weight;
        this.compileCost = compileCost;
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
            int length = parsed.expression().length(), run = caseSensitive && parsed.run() >= 4 ? parsed.run() : 0;
            int weight = PATTERN_BYTES + length + NODE_BYTES * parsed.nodes() + CLASS_BYTES * parsed.classes()
                + (run == 0 ? 0 : TABLE_BYTES + 4 * run);
            int compileCost = COMPILE_BASE + COMPILE_CHARGE * Math.max(source.length(), length) + run * run / RUN_SQUARE;
            return Optional.of(new UrlFilter(source, caseSensitive, caseSensitive ? parsed.literal() : fold(parsed.literal()),
                parsed.everything(), caseSensitive ? parsed.lowerCaseWeb() : parsed.web(), 1 + parsed.optional(), weight, compileCost));
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
     * Whether the filter is {@code ^https?://}, with or without a trailing {@code .*}: one that matches every http and
     * https address, whatever the case of its scheme unless the filter is case-sensitive, when it is written in small
     * letters and matches a scheme written so.
     */
    boolean matchesEveryWebAddress() {
        return web;
    }

    /** The Java expression a match finds. */
    String expression() {
        try {
            return new Parser(source).parse().expression();
        } catch (Refused refused) {
            throw new IllegalStateException("A filter refused after it was accepted: " + source);
        }
    }

    /** The filter's expression, compiled anew: what a {@link PatternCache} keeps. */
    Pattern compile() {
        return Pattern.compile(expression(), Pattern.DOTALL | (caseSensitive ? 0 : Pattern.CASE_INSENSITIVE));
    }

    /**
     * What the compiled expression is estimated to keep, in bytes (V48): its text, {@link #NODE_BYTES} for each node,
     * {@link #CLASS_BYTES} more for each character class, {@link #PATTERN_BYTES}, and, for a case-sensitive expression
     * that begins with a run of 4 characters or more, {@link #TABLE_BYTES} and 4 a character of the run.
     */
    int weight() {
        return weight;
    }

    /**
     * What its compile is charged (V48): {@link #COMPILE_BASE}, {@link #COMPILE_CHARGE} for each character of the
     * source, which the compile parses again, or of the expression, the longer, and, for a case-sensitive expression
     * that begins with a run of 4 characters or more, the square of the run divided by {@link #RUN_SQUARE}.
     */
    int compileCost() {
        return compileCost;
    }

    /**
     * Whether the filter matches somewhere in the address's first 8 KiB, in either case unless it is case-sensitive.
     * Its expression is compiled for the match. A match charged past 100,000 for its reads counts as no match. A
     * filter whose expression overflows the thread's stack, compiling or matching, matches nothing from then on.
     *
     * @param url the address
     * @return whether it matches
     */
    public boolean matches(String url) {
        return matches(url, new Reads(Integer.MAX_VALUE));
    }

    /**
     * {@link #matches(String)}, its reads charged to {@code reads}, and its compile not: one past them ends the call,
     * and a match past its own 100,000 or past the stack is recorded there.
     *
     * @throws Reads.Exhausted when the call's reads are spent
     */
    boolean matches(String url, Reads reads) {
        return match(url, reads, null, 0) == Outcome.MATCHED;
    }

    /**
     * What a match of the address comes to, its reads charged to {@code reads}, its expression the one
     * {@code patterns} keeps as filter {@code id}, which charges a compile there, or, {@code patterns} null, compiled
     * anew uncharged. A match past its own 100,000 or past the stack is recorded there and comes to
     * {@link Outcome#OVERRAN}, as every match does once the stack has overflowed.
     *
     * @throws Reads.Exhausted when the call's reads are spent, or cannot pay for the compile, which is then not started
     */
    Outcome match(String url, Reads reads, PatternCache patterns, int id) {
        Objects.requireNonNull(url, "url");
        if (everything) return Outcome.MATCHED;
        if (overflowed) return Outcome.OVERRAN;
        try {
            Pattern pattern = patterns == null ? compile() : patterns.pattern(id, reads);
            return pattern.matcher(new Budget(url, reads, cost)).find() ? Outcome.MATCHED : Outcome.UNMATCHED;
        } catch (Budget.Spent spent) {
            reads.overrun(this);
            return Outcome.OVERRAN;
        } catch (StackOverflowError | PatternSyntaxException overflow) {
            // Java's expressions recurse once a node, compiling and matching, and Java reports a stack it overflowed
            // compiling as a syntax error; on a thread with a small stack, a long filter can overflow it.
            overflowed = true;
            reads.overrun(this);
            return Outcome.OVERRAN;
        }
    }

    /** What a match comes to. */
    enum Outcome {
        /** The filter matches the address. */
        MATCHED,
        /** It does not. */
        UNMATCHED,
        /** The match ran past its own 100,000 or past the stack, so whether it matches is not known. */
        OVERRAN
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

    private static boolean capital(int c) {
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

    /**
     * What one call, a request's or a page's, may still be charged for the work of the rules it runs: each read of a
     * character at its filter's charge, and each check of a rule's literal for the address it reads. It records the
     * first filter that ran past its own 100,000 or past the stack. One call's, on one thread.
     */
    static final class Reads {
        private final int limit;
        private int left, characters;
        private UrlFilter overran;

        Reads(int limit) {
            this.limit = limit;
            left = limit;
        }

        /** How much the call was charged. */
        int used() {
            return limit - left;
        }

        /** How many characters the call's matches read. */
        int characters() {
            return characters;
        }

        /** The first filter that ran past its own reads or the stack in the call; empty when none did. */
        Optional<UrlFilter> overran() {
            return Optional.ofNullable(overran);
        }

        /**
         * Charges the call {@code amount}, all it has left when that is less.
         *
         * @throws Exhausted when the call has less than {@code amount} left
         */
        void charge(int amount) {
            if (left < amount) {
                left = 0;
                throw Exhausted.EXHAUSTED;
            }
            left -= amount;
        }

        private void read(int cost) {
            charge(cost);
            characters++;
        }

        private void overrun(UrlFilter filter) {
            if (overran == null) overran = filter;
        }

        /** The call's reads are spent. Carries no stack: it only ends the call. */
        static final class Exhausted extends RuntimeException {
            private static final Exhausted EXHAUSTED = new Exhausted();

            private Exhausted() {
                super(null, null, false, false);
            }
        }
    }

    /** Not a filter in WebKit's grammar or within the bounds. Carries no stack: it only ends the reading. */
    private static final class Refused extends Exception {
        private static final Refused REFUSED = new Refused();

        private Refused() {
            super(null, null, false, false);
        }
    }

    /**
     * What a filter reads as.
     *
     * @param literal its literal
     * @param expression the Java expression a match finds, with {@code .*} at either end left out, as {@code .}
     *     matches every character there
     * @param everything whether it matches every address
     * @param web whether it is {@code ^https?://}, its letters in either case, with nothing after but {@code .*}
     * @param lowerCaseWeb whether it is that with its letters in small letters
     * @param optional how many atoms the expression quantifies by {@code *} or {@code ?}, in groups too
     * @param nodes how many nodes the expression has: its characters, dots, classes, quantifiers and anchors, and two
     *     for each group, its ends
     * @param classes how many character classes it has
     * @param run how many characters it begins with that Java reads as one run
     */
    private record Parsed(String literal, String expression, boolean everything, boolean web, boolean lowerCaseWeb, int optional,
        int nodes, int classes, int run) { }

    /**
     * One top-level term of a filter, or of a group's.
     *
     * @param expression the term as Java's grammar writes it, each character but a letter or digit escaped
     * @param literal the character a plain or escaped atom stands for; -1 for {@code .}, a class or a group
     * @param dot whether the atom is {@code .}
     * @param holdsQuantifier whether the atom is a group that holds a {@code *}, {@code +} or {@code ?}
     * @param nullable whether the term can match the empty text: one quantified by {@code *} or {@code ?}, or a group
     *     whose every term can
     * @param optional how many atoms the term quantifies by {@code *} or {@code ?}, itself and those in its group
     * @param quantifier the {@code *}, {@code +} or {@code ?} after the atom; 0 when none
     * @param nodes how many nodes the term has, as {@link Parsed#nodes()} counts them
     * @param classes how many character classes it has
     */
    private record Piece(String expression, int literal, boolean dot, boolean holdsQuantifier, boolean nullable, int optional,
        char quantifier, int nodes, int classes) {
        Piece quantified(char by) {
            return new Piece(expression + by, literal, dot, holdsQuantifier, nullable || by != '+', optional + (by == '+' ? 0 : 1), by,
                nodes + 1, classes);
        }

        boolean anything() {
            return dot && quantifier == '*';
        }
    }

    /** Reads one filter, front to back, refusing anything outside WebKit's grammar or the bounds. */
    private static final class Parser {
        private static final String WEB = "https://";
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
            List<Piece> kept = pieces.subList(first, last);
            var expression = new StringBuilder(start ? "^" : "");
            int optional = 0, nodes = (start ? 1 : 0) + (end ? 1 : 0), classes = 0;
            for (Piece piece : kept) {
                expression.append(piece.expression());
                optional += piece.optional();
                nodes += piece.nodes();
                classes += piece.classes();
            }
            if (end) expression.append("\\z");
            boolean anchored = start && !end;
            return new Parsed(literal, expression.toString(), kept.isEmpty() && !(start && end), anchored && web(kept, false),
                anchored && web(kept, true), optional, nodes, classes, run(expression));
        }

        /**
         * How many characters the expression begins with that Java reads as one run: letters, digits and every other
         * printable character escaped, the space included (V58), up to the first other character or the one a
         * quantifier follows, which Java reads apart.
         */
        private static int run(CharSequence expression) {
            int at = 0, run = 0;
            while (at < expression.length()) {
                char c = expression.charAt(at);
                boolean escaped = c == '\\' && at + 1 < expression.length() && printable(expression.charAt(at + 1))
                    && !alphanumeric(expression.charAt(at + 1));
                if (!escaped && !alphanumeric(c)) break;
                int next = at + (escaped ? 2 : 1);
                if (next < expression.length() && "*+?".indexOf(expression.charAt(next)) >= 0) break;
                run++;
                at = next;
            }
            return run;
        }

        /** Whether the terms are {@code https?://}, its letters in either case or, {@code small}, in small ones. */
        private static boolean web(List<Piece> pieces, boolean small) {
            if (pieces.size() != WEB.length()) return false;
            for (int at = 0; at < WEB.length(); at++) {
                Piece piece = pieces.get(at);
                int c = piece.literal();
                if (piece.quantifier() != (at == 4 ? '?' : 0) || (small || !capital(c) ? c : c + ('a' - 'A')) != WEB.charAt(at)) return false;
            }
            return true;
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
                case '.' -> new Piece(".", -1, true, false, false, 0, (char) 0, 1, 0);
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
            return new Piece(escaped(c), c, false, false, false, 0, (char) 0, 1, 0);
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
            boolean nullable = true;
            int optional = 0, nodes = 2, classes = 0;
            for (Piece piece : inner) {
                expression.append(piece.expression());
                nullable &= piece.nullable();
                optional += piece.optional();
                nodes += piece.nodes();
                classes += piece.classes();
            }
            return new Piece(expression.append(')').toString(), -1, false, quantifiers > before, nullable, optional, (char) 0, nodes,
                classes);
        }

        /**
         * The class whose bracket was read, written out as its members, so that Java reads it in one step; one of one
         * member as that character, which Java keeps without a bitmap (V48). Either ends a run of the literal.
         */
        private Piece characterClass() throws Refused {
            boolean negated = at < source.length() && source.charAt(at) == '^';
            if (negated) at++;
            var members = new boolean[0x7F];
            for (int items = 0; ; items++) {
                if (at >= source.length()) throw Refused.REFUSED;
                if (source.charAt(at) == ']') {
                    if (items == 0) throw Refused.REFUSED;
                    at++;
                    var written = new StringBuilder();
                    int count = 0;
                    for (char c = 0x20; c < 0x7F; c++) {
                        if (!members[c]) continue;
                        written.append(escaped(c));
                        count++;
                    }
                    if (count == 1 && !negated) return new Piece(written.toString(), -1, false, false, false, 0, (char) 0, 1, 0);
                    return new Piece((negated ? "[^" : "[") + written + "]", -1, false, false, false, 0, (char) 0, 1, 1);
                }
                char low = classCharacter(), high = low;
                if (at + 1 < source.length() && source.charAt(at) == '-' && source.charAt(at + 1) != ']') {
                    at++;
                    high = classCharacter();
                    if (high < low) throw Refused.REFUSED;
                }
                for (char c = low; c <= high; c++) members[c] = true;
            }
        }

        /** A printable ASCII character of a class, escaped or not. */
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
            // A quantified group that can match nothing has two ways on without a read, which no budget counts.
            if (++quantifiers > MAX_QUANTIFIED || atom.nullable()) throw Refused.REFUSED;
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
            return alphanumeric(c) ? String.valueOf(c) : "\\" + c;
        }

        private static boolean alphanumeric(char c) {
            return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
        }

        private static boolean printable(char c) {
            return c >= 0x20 && c <= 0x7E;
        }
    }

    /**
     * The first 8 KiB of an address, each read of which is charged the filter's charge, to the match's 100,000 and to
     * its call's reads; the read past either ends the match.
     */
    private static final class Budget implements CharSequence {
        private final String text;
        private final int length, cost;
        private final Reads reads;
        private int steps;

        Budget(String text, Reads reads, int cost) {
            this.text = text;
            this.reads = reads;
            this.cost = cost;
            length = Math.min(text.length(), URL_LIMIT);
        }

        @Override public int length() {
            return length;
        }

        @Override public char charAt(int index) {
            if ((steps += cost) > STEPS) throw Spent.SPENT;
            reads.read(cost);
            return text.charAt(Objects.checkIndex(index, length));
        }

        @Override public CharSequence subSequence(int start, int end) {
            return toString().substring(start, end);
        }

        @Override public String toString() {
            return text.substring(0, length);
        }

        /** The match's budget is spent. Carries no stack: it only ends the match. */
        private static final class Spent extends RuntimeException {
            private static final Spent SPENT = new Spent();

            private Spent() {
                super(null, null, false, false);
            }
        }
    }
}
