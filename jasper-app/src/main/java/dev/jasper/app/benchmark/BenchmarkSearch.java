package dev.jasper.app.benchmark;

import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.util.List;

/**
 * One pane's search in the interactive phase. A width reflow that lands after a search was admitted supersedes it, and
 * {@link TerminalView#findAsync} then never calls back: with 100,000 history lines the search is still running when the
 * previous cycle's window resize settles into its reflow. The fixture never switches screens or clears its history and
 * the panes stay showing, so here only a width change supersedes a search, and a superseded search is asked again. EDT.
 */
final class BenchmarkSearch {
    private final TerminalView view;
    private final TerminalSession session;
    private final SearchQuery query;
    private boolean answered;
    private int columns;
    private int asked;

    private BenchmarkSearch(TerminalView view, TerminalSession session, SearchQuery query) {
        this.view = view; this.session = session; this.query = query;
    }

    /** Asks {@code view}, whose session is {@code session}, for {@code query}. */
    static BenchmarkSearch start(TerminalView view, TerminalSession session, SearchQuery query) {
        var search = new BenchmarkSearch(view, session, query);
        search.ask();
        return search;
    }

    /** Whether every search called back; each one a width change superseded since it was asked is asked again first. */
    static boolean answered(List<BenchmarkSearch> searches) {
        boolean all = true;
        for (BenchmarkSearch search : searches) {
            if (!search.answered && search.session.columns() != search.columns) search.ask();
            all &= search.answered;
        }
        return all;
    }

    /** How many times this search was asked. */
    int asked() { return asked; }

    private void ask() {
        columns = session.columns();
        asked++;
        view.findAsync(query, result -> answered = true);
    }
}
