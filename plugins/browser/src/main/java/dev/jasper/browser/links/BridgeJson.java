package dev.jasper.browser.links;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The little JSON the opener bridge speaks, as the JDK reads none. A page's request is one flat object whose values are
 * strings or {@code true} or {@code false}, read strictly: anything else, a repeated name or text after the object is no
 * request. What the bridge writes into a page's script is a string literal that JavaScript and JSON read alike, with
 * every character that could end it or its line escaped.
 */
final class BridgeJson {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private BridgeJson() { }

    /**
     * The members of {@code text}, a flat JSON object of string and boolean values.
     *
     * @param text what a page sent
     * @return the members, in their order; empty for anything else, never an exception
     */
    static Optional<Map<String, Object>> object(String text) {
        if (text == null) return Optional.empty();
        try {
            return Optional.of(new Reader(text).object());
        } catch (Malformed malformed) {
            return Optional.empty();
        }
    }

    /**
     * {@code text} as a double-quoted string literal that JavaScript and JSON both read back as {@code text}: quotes,
     * backslashes, control characters, DEL, the line and paragraph separators and every surrogate are escaped, so none
     * can end the literal or the line it is on.
     *
     * @param text any text, from a page or not
     * @return the literal
     */
    static String quote(String text) {
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

    /** Not a flat object of strings and booleans. Carries no stack: it only ends the reading. */
    private static final class Malformed extends Exception {
        Malformed() {
            super(null, null, false, false);
        }
    }

    /** Reads one flat object, the whole text. */
    private static final class Reader {
        private final String text;
        private int at;

        Reader(String text) {
            this.text = text;
        }

        Map<String, Object> object() throws Malformed {
            var members = new LinkedHashMap<String, Object>();
            space();
            expect('{');
            space();
            if (!take('}')) {
                do {
                    space();
                    String name = string();
                    space();
                    expect(':');
                    space();
                    if (members.putIfAbsent(name, value()) != null) throw new Malformed();
                    space();
                } while (take(','));
                expect('}');
            }
            space();
            if (at != text.length()) throw new Malformed();
            return members;
        }

        private Object value() throws Malformed {
            if (at < text.length() && text.charAt(at) == '"') return string();
            if (text.startsWith("true", at)) {
                at += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", at)) {
                at += 5;
                return Boolean.FALSE;
            }
            throw new Malformed();
        }

        private String string() throws Malformed {
            expect('"');
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

        /** The four ASCII hexadecimal digits of a JSON unicode escape, as one UTF-16 unit. */
        private char unit() throws Malformed {
            if (at + 4 > text.length()) throw new Malformed();
            int unit = 0;
            for (int digit = 0; digit < 4; digit++) {
                char c = text.charAt(at++);
                int value = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                if (value < 0) throw new Malformed();
                unit = unit * 16 + value;
            }
            return (char) unit;
        }

        private void space() {
            while (at < text.length() && " \t\n\r".indexOf(text.charAt(at)) >= 0) at++;
        }

        private boolean take(char c) {
            if (at < text.length() && text.charAt(at) == c) {
                at++;
                return true;
            }
            return false;
        }

        private void expect(char c) throws Malformed {
            if (!take(c)) throw new Malformed();
        }
    }
}
