package dev.jasper.browser.palette;

import java.util.Locale;

/** The palette scopes' reading of a query: lower-case words, each of which must occur in a row's text. */
final class Words {
    private Words() { }

    /** The words of {@code query}, lower case; none for a blank query. */
    static String[] of(String query) {
        String text = query.strip().toLowerCase(Locale.ROOT);
        return text.isEmpty() ? new String[0] : text.split("\\s+");
    }

    /** Whether every word occurs in {@code text}, which is lower case. */
    static boolean allIn(String[] words, String text) {
        for (String word : words) if (!text.contains(word)) return false;
        return true;
    }
}
