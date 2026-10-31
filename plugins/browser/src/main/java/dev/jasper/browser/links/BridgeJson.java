package dev.jasper.browser.links;

import dev.jasper.browser.json.StrictJson;
import java.util.Map;
import java.util.Optional;

/**
 * The little JSON the opener bridge speaks. A page's request is one flat object whose values are strings or
 * {@code true} or {@code false}, read strictly with {@link StrictJson}: anything else, a repeated name or text after the
 * object is no request. What the bridge writes into a page's script is a string literal that JavaScript and JSON read alike, with
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
        return StrictJson.object(text, 1)
            .filter(members -> members.values().stream().allMatch(value -> value instanceof String || value instanceof Boolean));
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
}
