package dev.jasper.ui.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Shared descriptor validation, independent of UI and theme. */
final class ModelChecks {
    private ModelChecks() {}
    static String id(String value) { return label(value, "id"); }
    static String label(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
    static void button(String label, String icon, String tooltip) {
        Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(tooltip);
        if (label.isBlank()) { label(icon, "icon"); label(tooltip, "icon-only tooltip"); }
    }
    static <T> void unique(Collection<T> values, Function<T, String> id) {
        var seen = new HashSet<String>();
        for (T value : values) if (!seen.add(id.apply(value))) throw new IllegalArgumentException("Duplicate id: " + id.apply(value));
    }
    /** The most tokens a token field holds, and the most characters a token holds. */
    static final int MAX_TOKENS = 100, MAX_TOKEN = 64;
    /**
     * The token rule: each token trimmed, kept only when it is nonblank, at most 64 characters and free of control
     * characters, and when it does not match an earlier one ignoring the case of ASCII letters; the first 100, in order.
     * The SDK's {@code Nodes.TokenField.kept} and the Browser's {@code BookmarkStore.keptTags} keep the same rule, and the
     * three must agree.
     */
    static List<String> keptTokens(List<String> given) {
        var seen = new HashSet<String>();
        var kept = new ArrayList<String>();
        for (String each : given) {
            String token = each.strip();
            if (kept.size() < MAX_TOKENS && !token.isEmpty() && token.length() <= MAX_TOKEN && token.chars().noneMatch(Character::isISOControl)
                    && seen.add(folded(token)))
                kept.add(token);
        }
        return List.copyOf(kept);
    }
    /** {@code tokens} copied, when the token rule keeps each of them as it is; otherwise refused, saying why. */
    static List<String> tokens(List<String> tokens) {
        var copy = List.copyOf(tokens);
        if (copy.size() > MAX_TOKENS) throw new IllegalArgumentException("A token field holds at most " + MAX_TOKENS + " tokens, not " + copy.size());
        var seen = new HashSet<String>();
        for (String token : copy) {
            if (token.isBlank()) throw new IllegalArgumentException("A token must not be blank");
            if (!token.equals(token.strip())) throw new IllegalArgumentException("A token has no leading or trailing white space: \"" + token + "\"");
            if (token.length() > MAX_TOKEN) throw new IllegalArgumentException("A token holds at most " + MAX_TOKEN + " characters, not " + token.length());
            if (token.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("A token holds no control characters");
            if (!seen.add(folded(token))) throw new IllegalArgumentException("Duplicate token: " + token);
        }
        return copy;
    }
    /** {@code token} with its ASCII letters in lower case: the case the token rule ignores, as SQLite's {@code NOCASE} does. */
    private static String folded(String token) {
        char[] chars = token.toCharArray();
        for (int i = 0; i < chars.length; i++)
            if (chars[i] >= 'A' && chars[i] <= 'Z') chars[i] = (char) (chars[i] + ('a' - 'A'));
        return new String(chars);
    }
}
