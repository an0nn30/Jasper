package dev.jasper.terminal.internal.text;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a URL written as plain text on a row: one for the browser, or a {@code file://} URL for the file rules. A URL
 * ends where a path does and loses the same trailing punctuation: {@code PathDetector.ENDS} and
 * {@code PathDetector.TRAILING} are the one pair both detectors use.
 */
public final class LinkDetector {
    private static final String URL_BODY = "://[^\\s" + PathDetector.ENDS + "]+";
    private static final Pattern URL = Pattern.compile("(?:https?|ftp)" + URL_BODY);
    private static final Pattern FILE_URL = Pattern.compile("file" + URL_BODY);

    private LinkDetector() {
    }

    /** The {@code http}, {@code https} or {@code ftp} URL covering {@code column}, without trailing punctuation. */
    public static Optional<String> urlAt(RowText row, int column) {
        return at(URL, row, column);
    }

    /** The {@code file://} URL covering {@code column}; whether its host is this machine is the caller's decision. */
    public static Optional<String> fileUrlAt(RowText row, int column) {
        return at(FILE_URL, row, column);
    }

    private static Optional<String> at(Pattern pattern, RowText row, int column) {
        String text = row.text();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && PathDetector.TRAILING.indexOf(text.charAt(end - 1)) >= 0) {
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
