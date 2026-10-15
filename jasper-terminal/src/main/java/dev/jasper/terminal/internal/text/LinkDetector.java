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

    /**
     * The {@code file://} URL covering {@code column}, with the {@code :LINE[:COLUMN]} that may end it; whether its host
     * is this machine is the caller's decision.
     */
    public static Optional<String> fileUrlAt(RowText row, int column) {
        return at(FILE_URL, row, column);
    }

    /**
     * A {@code file://} URL without the position that ended it.
     *
     * @param url the URL up to the position, or all of it when none ended it
     * @param line the 1-based line, or 0
     * @param column the 1-based column, or 0
     */
    public record UrlPosition(String url, int line, int column) { }

    /**
     * Splits the {@code :LINE} or {@code :LINE:COLUMN} off a plain-text {@code file://} URL's path, as Kotlin prints
     * {@code file:///src/Foo.kt:12:5}, by the rule {@link PathDetector} reads a path's position with. A colon in the
     * host or a drive ({@code file:///C:/x}) is no position.
     */
    public static UrlPosition splitPosition(String fileUrl) {
        int authority = fileUrl.indexOf("://");
        int path = authority < 0 ? 0 : fileUrl.indexOf('/', authority + 3);
        for (int colon = path < 0 ? -1 : fileUrl.indexOf(':', path); colon >= 0; colon = fileUrl.indexOf(':', colon + 1)) {
            int[] position = PathDetector.lineAfterColon(fileUrl, colon, fileUrl.length());
            if (position != null) return new UrlPosition(fileUrl.substring(0, colon), position[0], position[1]);
        }
        return new UrlPosition(fileUrl, 0, 0);
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
