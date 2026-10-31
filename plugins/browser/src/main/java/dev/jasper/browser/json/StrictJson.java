package dev.jasper.browser.json;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON from a page or from Chromium, as the JDK reads none: one object, read whole and strictly, no deeper than its
 * reader allows. Anything else, such as a repeated name, text after the object, a trailing comma, a raw control
 * character or an escape other than JSON's, is nothing. Nesting is bounded, so no text can exhaust the stack.
 */
public final class StrictJson {
    /** What JSON's {@code null} reads as. */
    public static final Object NULL = Marker.NULL;
    /** What a number reads as unless it is an integer of at most 18 digits, which reads as a {@link Long}. */
    public static final Object NUMBER = Marker.NUMBER;

    private StrictJson() { }

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

    /** Not such an object. Carries no stack: it only ends the reading. */
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

        private List<Object> array() throws Malformed {
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
