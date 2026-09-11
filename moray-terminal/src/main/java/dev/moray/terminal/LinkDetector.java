package dev.moray.terminal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a URL written as plain text on a row. */
final class LinkDetector {
    private static final Pattern URL = Pattern.compile("(?:https?|ftp|file)://[^\\s<>\"'`]+");
    private static final String TRAILING = ".,;:!?)]}'\"";

    private LinkDetector() {
    }

    static Optional<String> urlAt(RowText row, int column) {
        String text = row.text();
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && TRAILING.indexOf(text.charAt(end - 1)) >= 0) {
                end--;
            }
            if (end == matcher.start()) {
                continue;
            }
            int firstColumn = row.columns()[matcher.start()];
            int lastColumn = row.lastColumns()[end - 1];
            if (column >= firstColumn && column <= lastColumn) {
                return Optional.of(text.substring(matcher.start(), end));
            }
        }
        return Optional.empty();
    }
}
