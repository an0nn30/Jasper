package dev.jasper.app.settingsui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The Settings search rule: every word of the query occurs in the text, ignoring case. */
public final class SearchText {
    private SearchText() { }

    /** The query's words, lower-cased. */
    public static List<String> words(String query) {
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
    }

    /** Whether every word of {@code query} occurs in {@code text}; false for a blank query. */
    public static boolean matches(String text, String query) {
        List<String> words = words(query);
        if (words.isEmpty()) return false;
        String haystack = text.toLowerCase(Locale.ROOT);
        return words.stream().allMatch(haystack::contains);
    }
}
