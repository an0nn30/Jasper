package dev.jasper.browser.adblock;

import dev.jasper.browser.json.StrictJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A WebKit content-blocker list as text: one JSON array of objects, whose values are strings, booleans, numbers,
 * arrays and objects. The list comes from the web and may be hostile, so it is read through the {@code json}
 * package's {@link StrictJson}, which reads untrusted JSON whole and strictly, with these bounds added: at most 32 MiB
 * of text and 8 levels of arrays and objects, the list itself the first.
 */
public final class BlockerJson {
    /** How many characters a list has at most. */
    static final int MAX_LENGTH = 32 * 1024 * 1024;
    /** How many arrays and objects nest at most, the list itself the first. */
    static final int MAX_DEPTH = 8;
    private static final String REFUSED = "Not a content blocker list: ";

    private BlockerJson() { }

    /**
     * The list's rules, each as JSON's object: a map of its members in their order, an array as a list, a string as a
     * {@link String}, {@code true} and {@code false} as {@link Boolean}, and a number as {@link StrictJson} reads one.
     *
     * @param text the list's text
     * @return its rules, in their order
     * @throws IllegalArgumentException with a message that begins {@code Not a content blocker list: } and says why,
     *     for text longer than 32 MiB, anything but one array of objects, JSON that is not well-formed or nests deeper
     *     than 8 levels, and a {@code null} anywhere
     */
    public static List<Map<String, Object>> read(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_LENGTH) throw refused("longer than 32 MiB");
        int start = 0;
        while (start < text.length() && " \t\n\r".indexOf(text.charAt(start)) >= 0) start++;
        if (!text.startsWith("[", start)) throw refused("not a JSON array");
        List<Object> items = StrictJson.array(text, MAX_DEPTH)
            .orElseThrow(() -> refused("not well-formed JSON, or nested deeper than " + MAX_DEPTH + " levels"));
        var rules = new ArrayList<Map<String, Object>>(items.size());
        for (int at = 0; at < items.size(); at++) {
            if (!(items.get(at) instanceof Map<?, ?> item)) throw refused("item " + (at + 1) + " is not an object");
            if (holdsNull(item)) throw refused("item " + (at + 1) + " holds a null");
            @SuppressWarnings("unchecked") Map<String, Object> rule = (Map<String, Object>) item;
            rules.add(rule);
        }
        return rules;
    }

    /**
     * {@code rules} as a list's text, which {@link #read} reads back as they are.
     *
     * @param rules the rules, whose values are strings, {@link Long}s, {@link Boolean}s, lists and maps, as
     *     {@link ContentRules#encode} makes them
     * @return the text
     * @throws IllegalArgumentException for any other value
     */
    public static String write(List<Map<String, Object>> rules) {
        return StrictJson.write(rules);
    }

    /** Whether {@code value}, or anything in it, is JSON's {@code null}; its nesting is bounded by the reading. */
    private static boolean holdsNull(Object value) {
        return switch (value) {
            case Map<?, ?> members -> members.values().stream().anyMatch(BlockerJson::holdsNull);
            case List<?> values -> values.stream().anyMatch(BlockerJson::holdsNull);
            default -> value == StrictJson.NULL;
        };
    }

    private static IllegalArgumentException refused(String reason) {
        return new IllegalArgumentException(REFUSED + reason);
    }
}
