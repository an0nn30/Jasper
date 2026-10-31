package dev.jasper.browser.links;

import dev.jasper.browser.json.StrictJson;
import java.util.Map;
import java.util.Optional;

/**
 * The little JSON the opener bridge reads. A page's request is one flat object whose values are strings, {@code true}
 * or {@code false}, or integers of at most 18 digits, read strictly with {@link StrictJson}: anything else, a repeated
 * name or text after the object is no request. What the bridge writes into a page's script is a string literal that
 * JavaScript and JSON read alike ({@link StrictJson#quote}).
 */
final class BridgeJson {
    private BridgeJson() { }

    /**
     * The members of {@code text}, a flat JSON object of string, boolean and integer values; an integer reads as a
     * {@link Long}.
     *
     * @param text what a page sent
     * @return the members, in their order; empty for anything else, never an exception
     */
    static Optional<Map<String, Object>> object(String text) {
        return StrictJson.object(text, 1).filter(members -> members.values().stream()
            .allMatch(value -> value instanceof String || value instanceof Boolean || value instanceof Long));
    }
}
