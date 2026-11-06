package dev.jasper.browser.palette;

import dev.jasper.browser.store.BookmarkIndex;
import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The Bookmarks scope: the bookmarks whose title, address or tags contain every word typed, those whose title begins with
 * the text first, then the newest. With nothing typed it lists them all, newest first. Enter opens the bookmark in the
 * window's focused browser item, or in a new tab. UI thread.
 */
public final class BookmarksScope implements PaletteScope {
    /** The scope's id. */
    public static final String ID = "dev.jasper.browser.scope.bookmarks";
    static final PaletteVerb OPEN = new PaletteVerb("open", "Open");
    private static final String ROW = "bookmark:";
    private final BookmarkIndex index;
    private final BiConsumer<URI, WindowHandle> open;

    /**
     * A scope over the bookmarks.
     *
     * @param index the bookmarks
     * @param open opens an address in a window
     */
    public BookmarksScope(BookmarkIndex index, BiConsumer<URI, WindowHandle> open) {
        this.index = index;
        this.open = open;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Bookmarks", "Search bookmarks", List.of(OPEN))
            .withDescription("Open Bookmark in Browser").withIcon("bookmark").withItemNoun("bookmark");
    }

    @Override public void activated(PaletteQuery context) {
        index.load();
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        String[] words = Words.of(query);
        String typed = String.join(" ", words);
        int limit = Math.min(context.maxResults(), PaletteResults.MAX_ROWS);
        var prefixed = new ArrayList<Bookmark>();
        var others = new ArrayList<Bookmark>();
        for (Bookmark bookmark : index.all()) {
            if (!Words.allIn(words, text(bookmark))) continue;
            (!typed.isEmpty() && bookmark.title().toLowerCase(Locale.ROOT).startsWith(typed) ? prefixed : others).add(bookmark);
        }
        prefixed.addAll(others);
        var rows = new ArrayList<PaletteRow>();
        for (Bookmark bookmark : prefixed) {
            if (rows.size() == limit) break;
            rows.add(row(bookmark));
        }
        return new PaletteResults(rows, words.length == 0 ? Optional.of("Bookmarks") : Optional.empty(), Optional.empty());
    }

    /** The text the words are looked for in: the title, the address and the tags, in lower case. */
    private static String text(Bookmark bookmark) {
        return (bookmark.title() + "\n" + bookmark.url() + "\n" + String.join("\n", bookmark.tags())).toLowerCase(Locale.ROOT);
    }

    private static PaletteRow row(Bookmark bookmark) {
        String title = bookmark.title().isBlank() ? bookmark.url() : bookmark.title();
        return PaletteRow.of(ROW + bookmark.id(), title).withDetail(bookmark.url()).withToken(bookmark.url());
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
