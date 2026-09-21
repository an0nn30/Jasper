package dev.jasper.terminal.internal.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds text row by row; a match does not span a soft wrap. */
public final class TerminalSearch {

    /** A match on an absolute row; columns inclusive.
     * @param row absolute match row
     * @param startColumn first selected cell, inclusive
     * @param endColumn last selected cell, inclusive
     */
    public record Match(long row, int startColumn, int endColumn) {
    }

    private TerminalSearch() {
    }

    /** @throws java.util.regex.PatternSyntaxException when {@code regex} is true and the query is not a valid regex */
    public static Pattern pattern(String query, boolean regex, boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(regex ? query : Pattern.quote(query), flags);
    }

    public static List<Match> find(Pattern pattern, long firstRow, List<TerminalRow> lines, int width) {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            RowText row = RowText.of(lines.get(i), width);
            Matcher matcher = pattern.matcher(row.text());
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    matches.add(new Match(firstRow + i,
                        row.columns()[matcher.start()], row.lastColumns()[matcher.end() - 1]));
                }
            }
        }
        return matches;
    }
}
