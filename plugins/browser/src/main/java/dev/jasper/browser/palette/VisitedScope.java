package dev.jasper.browser.palette;

import dev.jasper.browser.store.HistoryStore.Visit;
import dev.jasper.browser.store.VisitIndex;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The Browser History scope: the visited sites whose title or address contain every word typed, the most visited first,
 * then the most recent. With nothing typed it lists the {@value #RECENT} most recent. When what is typed is an https
 * address with a host, its first row opens that address. Enter opens a row in the window's focused browser item, or in a
 * new tab. It finds only the {@value VisitIndex#MAX} sites the index holds, the most recently visited. UI thread.
 */
public final class VisitedScope implements PaletteScope {
    /** The scope's id. */
    public static final String ID = "dev.jasper.browser.scope.history";
    /** The id of the row that opens the typed address. */
    public static final String OPEN_URL = "open-url";
    static final PaletteVerb OPEN = new PaletteVerb("open", "Open");
    static final int RECENT = 50;
    private static final String ROW = "visit:";
    private static final Comparator<Visit> MOST_VISITED = Comparator.comparingInt(Visit::count).reversed()
        .thenComparing(Comparator.comparingLong(Visit::lastVisit).reversed());
    private final VisitIndex index;
    private final BiConsumer<URI, WindowHandle> open;

    /**
     * A scope over the visited sites.
     *
     * @param index the visited sites
     * @param open opens an address in a window
     */
    public VisitedScope(VisitIndex index, BiConsumer<URI, WindowHandle> open) {
        this.index = index;
        this.open = open;
    }

    @Override public ScopeSpec spec() {
        // Searchable, but not offered before the user types: history is not something to show unasked, as shell history.
        return ScopeSpec.of(ID, "Browser History", "Search visited sites", List.of(OPEN))
            .withDescription("Open Visited Site in Browser").withIcon("history").withItemNoun("site").withInRecents(false);
    }

    @Override public void activated(PaletteQuery context) {
        index.load();
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        String[] words = Words.of(query);
        int limit = Math.min(context.maxResults(), PaletteResults.MAX_ROWS);
        var rows = new ArrayList<PaletteRow>();
        if (words.length == 0) {
            for (Visit visit : index.all()) {
                if (rows.size() == Math.min(limit, RECENT)) break;
                rows.add(row(visit));
            }
            return new PaletteResults(rows, Optional.of("Recent"), Optional.empty());
        }
        url(query).ifPresent(address -> rows.add(PaletteRow.of(OPEN_URL, "Open URL in Browser").withDetail(address).withToken(address)));
        var found = new ArrayList<Visit>();
        for (Visit visit : index.all()) if (Words.allIn(words, text(visit))) found.add(visit);
        found.sort(MOST_VISITED);
        for (Visit visit : found) {
            if (rows.size() >= limit) break;
            rows.add(row(visit));
        }
        return PaletteResults.of(rows);
    }

    /** The trimmed query when it is an https address with a host. */
    static Optional<String> url(String query) {
        String text = query.strip();
        if (!text.regionMatches(true, 0, "https://", 0, 8)) return Optional.empty();
        try {
            String host = new URI(text).getHost();
            return host == null || host.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (URISyntaxException notAnAddress) {
            return Optional.empty();
        }
    }

    private static String text(Visit visit) {
        return (visit.title() + "\n" + visit.url()).toLowerCase(Locale.ROOT);
    }

    private static PaletteRow row(Visit visit) {
        String title = visit.title().isBlank() ? visit.url() : visit.title();
        return PaletteRow.of(ROW + visit.id(), title).withDetail(visit.url()).withToken(visit.url());
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof String address)) return;
        try {
            open.accept(URI.create(address), context.window());
        } catch (IllegalArgumentException notAnAddress) {
            // A stored address that is no URI opens nothing.
        }
    }

    @Override public Subscription onChanged(Runnable listener) {
        return index.onChanged(listener);
    }
}
