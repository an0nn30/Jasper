package dev.moray.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class CommandSearch {
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    record Entry(Command command, String title, List<String> words, String keywords) {}
    private record Match(Command command, int tier, int gaps, int recent, String title) {}

    private CommandSearch() {}

    static String normalize(String text) {
        return SPACE.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    static Entry entry(Command command) {
        String title = normalize(command.title());
        return new Entry(command, title, List.of(WORD.split(title)),
            normalize(String.join(" ", command.keywords())));
    }

    static List<Command> find(List<Entry> entries, String query, List<String> recent) {
        String q = normalize(query);
        if (q.isEmpty()) return List.of();
        List<Match> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.command().action().isEnabled()) continue;
            int tier = 0;
            int gaps = 0;
            if (entry.title().equals(q)) {
                tier = 0;
            } else if (entry.title().startsWith(q)) {
                tier = 1;
            } else {
                boolean matched = true;
                for (String token : q.split(" ")) {
                    int tokenTier;
                    if (entry.words().stream().anyMatch(word -> word.startsWith(token))) tokenTier = 2;
                    else if (entry.title().contains(token)) tokenTier = 3;
                    else if (entry.keywords().contains(token)) tokenTier = 4;
                    else {
                        int distance = fuzzyGaps(entry.title(), token);
                        if (distance < 0) {
                            matched = false;
                            break;
                        }
                        tokenTier = 5;
                        gaps += distance;
                    }
                    tier = Math.max(tier, tokenTier);
                }
                if (!matched) continue;
            }
            int r = recent.indexOf(entry.command().id());
            matches.add(new Match(entry.command(), tier, gaps,
                r < 0 ? Integer.MAX_VALUE : r, entry.title()));
        }
        matches.sort(Comparator.comparingInt(Match::tier).thenComparingInt(Match::gaps)
            .thenComparingInt(Match::recent).thenComparing(Match::title)
            .thenComparing(m -> m.command().id()));
        return matches.stream().limit(5).map(Match::command).toList();
    }

    private static int fuzzyGaps(String title, String token) {
        int previous = -1;
        int gaps = 0;
        for (int offset = 0; offset < token.length();) {
            int cp = token.codePointAt(offset);
            int next = title.indexOf(cp, previous + 1);
            if (next < 0) return -1;
            if (previous >= 0) gaps += next - previous - 1;
            previous = next;
            offset += Character.charCount(cp);
        }
        return gaps;
    }
}
