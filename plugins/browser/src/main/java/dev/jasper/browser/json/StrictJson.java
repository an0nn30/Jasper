package dev.jasper.browser.json;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON from a page, from Chromium or from a search engine, as the JDK reads none: one object or one array, read whole
 * and strictly, no deeper than its reader allows. Anything else, such as a repeated name, text after the value, a
 * trailing comma, a raw control character or an escape other than JSON's, is nothing. Nesting is bounded, so no text
 * can exhaust the stack. It also writes the JSON Jasper puts into a page's scripts that is not its own, a string
 * ({@link #quote}), and the answers Jasper's own pages are given ({@link #write}).
 */
public final class StrictJson {
    /** What JSON's {@code null} reads as, and what {@link #write} writes as {@code null}. */
    public static final Object NULL = Marker.NULL;
    /** What a number reads as unless it is an integer of at most 18 digits, which reads as a {@link Long}. */
    public static final Object NUMBER = Marker.NUMBER;
    /** How many arrays and objects {@link #write} nests at most, the outermost the first. */
    static final int WRITE_DEPTH = 64;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private StrictJson() { }

    /**
     * {@code text} as a double-quoted string literal that JavaScript and JSON both read back as {@code text}: quotes,
     * backslashes, control characters, DEL, the line and paragraph separators and every surrogate are escaped, so none
     * can end the literal or the line it is on.
     *
     * @param text any text, from a page or the user
     * @return the literal
     */
    public static String quote(String text) {
        var out = new StringBuilder(text.length() + 16).append('"');
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7F || c == 0x2028 || c == 0x2029 || Character.isSurrogate(c))
                        out.append("\\u").append(HEX[c >> 12]).append(HEX[(c >> 8) & 0xF]).append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * {@code value} as compact JSON: a map as an object of its entries in the map's order, a list as an array, a string as
     * {@link #quote} writes it, a {@link Long} as a decimal integer, a {@link Boolean} as {@code true} or {@code false}, and
     * {@link #NULL} as {@code null}. Every string reads back, through JSON or JavaScript, as it was.
     *
     * @param value what to write, with maps and lists nested at most {@value #WRITE_DEPTH} deep
     * @return the JSON
     * @throws IllegalArgumentException for anything else: Java's null, another kind of number or object, a map key that
     *     is no string, or nesting deeper than the bound, as a list that holds itself is
     */
    public static String write(Object value) {
        var out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        switch (value) {
            case String text -> out.append(quote(text));
            case Long number -> out.append(number.longValue());
            case Boolean flag -> out.append(flag.booleanValue());
            case Marker marker when marker == Marker.NULL -> out.append("null");
            case Map<?, ?> members -> {
                enter(depth);
                out.append('{');
                boolean first = true;
                for (var member : members.entrySet()) {
                    if (!(member.getKey() instanceof String name))
                        throw new IllegalArgumentException("A JSON object's names are strings, not " + member.getKey());
                    if (!first) out.append(',');
                    first = false;
                    out.append(quote(name)).append(':');
                    write(member.getValue(), out, depth + 1);
                }
                out.append('}');
            }
            case List<?> values -> {
                enter(depth);
                out.append('[');
                for (int at = 0; at < values.size(); at++) {
                    if (at > 0) out.append(',');
                    write(values.get(at), out, depth + 1);
                }
                out.append(']');
            }
            case null, default -> throw new IllegalArgumentException("JSON holds no " + (value == null ? "Java null" : value.getClass().getName()));
        }
    }

    /** One more array or object, {@code depth} being those it is inside. */
    private static void enter(int depth) {
        if (depth >= WRITE_DEPTH) throw new IllegalArgumentException("JSON nested deeper than " + WRITE_DEPTH + " levels");
    }

    private enum Marker { NULL, NUMBER }

    /**
     * The members of {@code text}, one JSON object. An object reads as a map of its members in their order, an array as
     * a list, a string as a {@link String}, {@code true} and {@code false} as {@link Boolean}, {@code null} as
     * {@link #NULL}, an integer of at most 18 digits as a {@link Long} and any other number as {@link #NUMBER}.
     *
     * @param text the text, from a page or Chromium; the caller bounds its length
     * @param maxDepth how many arrays and objects may nest, the object itself the first: 1 takes a flat object
     * @return the members; empty for anything else, never an exception
     */
    public static Optional<Map<String, Object>> object(String text, int maxDepth) {
        if (text == null) return Optional.empty();
        try {
            var reader = new Reader(text, maxDepth);
            reader.space();
            if (!reader.next('{')) return Optional.empty();
            Map<String, Object> members = reader.object();
            reader.space();
            return reader.at == text.length() ? Optional.of(members) : Optional.empty();
        } catch (Malformed malformed) {
            return Optional.empty();
        }
    }

    /**
     * The values of {@code text}, one JSON array, such as a search engine's OpenSearch suggestions; each value reads as
     * {@link #object(String, int)} reads a member's.
     *
     * @param text the text, from the web; the caller bounds its length
     * @param maxDepth how many arrays and objects may nest, the array itself the first: 1 takes a flat array
     * @return the values; empty for anything else, never an exception
     */
    public static Optional<List<Object>> array(String text, int maxDepth) {
        if (text == null) return Optional.empty();
        try {
            var reader = new Reader(text, maxDepth);
            reader.space();
            if (!reader.next('[')) return Optional.empty();
            List<Object> values = reader.array();
            reader.space();
            return reader.at == text.length() ? Optional.of(values) : Optional.empty();
        } catch (Malformed malformed) {
            return Optional.empty();
        }
    }

    /** Not such an object or array. Carries no stack: it only ends the reading. */
    private static final class Malformed extends Exception {
        Malformed() {
            super(null, null, false, false);
        }
    }

    /** Reads one value at a time from the text. */
    private static final class Reader {
        private final String text;
        private final int maxDepth;
        private int at, depth;

        Reader(String text, int maxDepth) {
            this.text = text;
            this.maxDepth = maxDepth;
        }

        private Object value() throws Malformed {
            space();
            if (next('{')) return object();
            if (next('[')) return array();
            if (next('"')) return string();
            if (at < text.length() && (text.charAt(at) == '-' || digit(text.charAt(at)))) return number();
            if (word("true")) return Boolean.TRUE;
            if (word("false")) return Boolean.FALSE;
            if (word("null")) return NULL;
            throw new Malformed();
        }

        /** The object whose brace is next, one level deeper. */
        Map<String, Object> object() throws Malformed {
            enter();
            var members = new LinkedHashMap<String, Object>();
            space();
            if (!take('}')) {
                do {
                    space();
                    if (!next('"')) throw new Malformed();
                    String name = string();
                    space();
                    expect(':');
                    if (members.putIfAbsent(name, value()) != null) throw new Malformed();
                    space();
                } while (take(','));
                expect('}');
            }
            depth--;
            return members;
        }

        /** The array whose bracket is next, one level deeper. */
        List<Object> array() throws Malformed {
            enter();
            var values = new ArrayList<>();
            space();
            if (!take(']')) {
                do {
                    values.add(value());
                    space();
                } while (take(','));
                expect(']');
            }
            depth--;
            return values;
        }

        /** Takes the bracket or brace next, which opens a level deeper. */
        private void enter() throws Malformed {
            if (++depth > maxDepth) throw new Malformed();
            at++;
        }

        /** The string whose quote is next. */
        private String string() throws Malformed {
            at++;
            var out = new StringBuilder();
            while (true) {
                if (at >= text.length()) throw new Malformed();
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw new Malformed();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (at >= text.length()) throw new Malformed();
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(unit());
                    default -> throw new Malformed();
                }
            }
        }

        /** The four ASCII hexadecimal digits of a unicode escape, as one UTF-16 unit. */
        private char unit() throws Malformed {
            if (at + 4 > text.length()) throw new Malformed();
            int unit = 0;
            for (int digit = 0; digit < 4; digit++) {
                char c = text.charAt(at++);
                if (!HexFormat.isHexDigit(c)) throw new Malformed();
                unit = unit * 16 + HexFormat.fromHexDigit(c);
            }
            return (char) unit;
        }

        /** A number: an integer of at most 18 digits as a {@link Long}, any other as {@link #NUMBER}. */
        private Object number() throws Malformed {
            int start = at;
            take('-');
            if (!take('0') && digits() == 0) throw new Malformed();
            boolean integer = true;
            if (take('.')) {
                integer = false;
                if (digits() == 0) throw new Malformed();
            }
            if (take('e') || take('E')) {
                integer = false;
                if (!take('+')) take('-');
                if (digits() == 0) throw new Malformed();
            }
            String written = text.substring(start, at);
            int digits = written.length() - (written.charAt(0) == '-' ? 1 : 0);
            return integer && digits <= 18 ? Long.parseLong(written) : NUMBER;
        }

        /** Takes the decimal digits here, and says how many. */
        private int digits() {
            int start = at;
            while (at < text.length() && digit(text.charAt(at))) at++;
            return at - start;
        }

        private static boolean digit(char c) {
            return c >= '0' && c <= '9';
        }

        private boolean word(String word) {
            if (!text.startsWith(word, at)) return false;
            at += word.length();
            return true;
        }

        void space() {
            while (at < text.length() && " \t\n\r".indexOf(text.charAt(at)) >= 0) at++;
        }

        /** Whether {@code c} is next, which is left for the reading of what it opens. */
        boolean next(char c) {
            return at < text.length() && text.charAt(at) == c;
        }

        private boolean take(char c) {
            if (!next(c)) return false;
            at++;
            return true;
        }

        private void expect(char c) throws Malformed {
            if (!take(c)) throw new Malformed();
        }
    }
}
