package dev.jasper.indexer.search;

import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.ProjectIndex;
import dev.jasper.indexer.index.ProjectKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;

/** Ranks indexed files against a query: by tier, then the current project, then shorter paths, then alphabetically. */
public final class FileMatcher {
    /** The name equals the word. */
    public static final int EXACT = 1;
    /** The name starts with the word. */
    public static final int PREFIX = 2;
    /** The word follows the name's humps and word starts. */
    public static final int INITIALS = 3;
    /** The name contains the word. */
    public static final int SUBSTRING = 4;
    /** The path contains the word, or a {@code /} word's parts match segments in order. */
    public static final int PATH = 5;
    private static final int NO_MATCH = 0;
    private static final int CHECK_EVERY = 1024;
    private static final int LONGEST_HUMP_NAME = 256;

    /**
     * One matching file.
     *
     * @param index its snapshot
     * @param file its position there
     * @param tier how well it matched; lower is better
     */
    public record Hit(ProjectIndex index, int file, int tier) {
        /** @return the file */
        public FileRef ref() { return new FileRef(index.key(), index.file(file)); }
    }

    private record Word(String text, List<String> parts) { }

    private FileMatcher() { }

    /**
     * @param query what the user typed
     * @param indexes the snapshots to search
     * @param current the project the user is in, if any
     * @param max the most hits to return
     * @param budget how long to scan before returning what was found
     * @param nanoClock a nanosecond clock, such as {@code System::nanoTime}
     * @return the best hits, best first
     */
    public static List<Hit> search(String query, List<ProjectIndex> indexes, Optional<ProjectKey> current, int max,
                                   Duration budget, LongSupplier nanoClock) {
        List<Word> words = words(query);
        if (words.isEmpty() || max <= 0) return List.of();
        Comparator<Hit> order = Comparator.comparingInt(Hit::tier)
            .thenComparingInt(hit -> isCurrent(hit.index(), current) ? 0 : 1)
            .thenComparingInt(hit -> hit.index().file(hit.file()).length())
            .thenComparing(hit -> hit.index().file(hit.file()));
        var best = new PriorityQueue<Hit>(max + 1, order.reversed());
        var ordered = new ArrayList<>(indexes);
        ordered.sort(Comparator.comparingInt(index -> isCurrent(index, current) ? 0 : 1));
        long deadline = nanoClock.getAsLong() + budget.toNanos();
        int scanned = 0;
        scan:
        for (ProjectIndex index : ordered) {
            for (int i = 0; i < index.size(); i++) {
                if (++scanned % CHECK_EVERY == 0 && nanoClock.getAsLong() > deadline) break scan;
                int tier = tier(words, index.name(i), index.lowerName(i), index.lowerPath(i));
                if (tier == NO_MATCH) continue;
                best.add(new Hit(index, i, tier));
                if (best.size() > max) best.poll();
            }
        }
        var result = new ArrayList<>(best);
        result.sort(order);
        return result;
    }

    private static boolean isCurrent(ProjectIndex index, Optional<ProjectKey> current) {
        return current.isPresent() && index.key().equals(current.get());
    }

    private static List<Word> words(String query) {
        var result = new ArrayList<Word>();
        for (String token : query.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
            List<String> parts = Arrays.stream(token.split("/")).filter(part -> !part.isEmpty()).toList();
            if (parts.isEmpty()) continue;
            result.add(new Word(parts.size() == 1 ? parts.get(0) : token, parts));
        }
        return result;
    }

    private static int tier(List<Word> words, String name, String lowerName, String lowerPath) {
        int worst = NO_MATCH;
        for (Word word : words) {
            int tier = tier(word, name, lowerName, lowerPath);
            if (tier == NO_MATCH) return NO_MATCH;
            worst = Math.max(worst, tier);
        }
        return worst;
    }

    private static int tier(Word word, String name, String lowerName, String lowerPath) {
        if (word.parts().size() > 1) return segmentsInOrder(word.parts(), lowerPath) ? PATH : NO_MATCH;
        String text = word.text();
        if (lowerName.equals(text)) return EXACT;
        if (lowerName.startsWith(text)) return PREFIX;
        if (text.length() > 1 && name.length() <= LONGEST_HUMP_NAME && subsequence(text, lowerName) && initials(text, name)) return INITIALS;
        if (lowerName.contains(text)) return SUBSTRING;
        if (lowerPath.contains(text)) return PATH;
        return NO_MATCH;
    }

    private static boolean segmentsInOrder(List<String> parts, String lowerPath) {
        String[] segments = lowerPath.split("/");
        int at = 0;
        for (String part : parts) {
            while (at < segments.length && !segments[at].contains(part)) at++;
            if (at == segments.length) return false;
            at++;
        }
        return true;
    }

    private static boolean subsequence(String text, String lowerName) {
        int at = 0;
        for (int i = 0; i < text.length(); i++) {
            at = lowerName.indexOf(text.charAt(i), at);
            if (at < 0) return false;
            at++;
        }
        return true;
    }

    /**
     * Whether a lower-case query follows a name's humps: each character either continues the hump being matched or
     * starts a later hump (a capital after a lower-case letter or digit, or a letter or digit after {@code _ - .} or a
     * space). The first character must start a hump.
     */
    static boolean initials(String lowerQuery, String name) { return humps(lowerQuery, 0, name, -1); }

    private static boolean humps(String query, int next, String name, int last) {
        if (next == query.length()) return true;
        char want = query.charAt(next);
        if (last >= 0 && last + 1 < name.length() && Character.toLowerCase(name.charAt(last + 1)) == want
            && humps(query, next + 1, name, last + 1)) return true;
        for (int at = last + 1; at < name.length(); at++)
            if (humpStart(name, at) && Character.toLowerCase(name.charAt(at)) == want && humps(query, next + 1, name, at)) return true;
        return false;
    }

    private static boolean humpStart(String name, int at) {
        if (at == 0) return true;
        char c = name.charAt(at);
        char previous = name.charAt(at - 1);
        if (Character.isUpperCase(c) && (Character.isLowerCase(previous) || Character.isDigit(previous))) return true;
        return Character.isLetterOrDigit(c) && (previous == '_' || previous == '-' || previous == '.' || previous == ' ');
    }
}
