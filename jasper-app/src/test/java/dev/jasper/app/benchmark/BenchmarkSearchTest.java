package dev.jasper.app.benchmark;

import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BenchmarkSearchTest {
    private static final SearchQuery QUERY = new SearchQuery("jasper", false, true);

    @Test void aSearchAWidthReflowSupersededIsAskedAgainUntilItAnswers() throws Exception {
        TerminalSession session = IdleTerminalSession.start();
        try {
            TerminalView view = onEdt(() -> {
                var created = new TerminalView(session, TerminalOptions.defaults());
                created.setSize(800, 400);
                created.setFontSize(17); // fits the session to the view
                return created;
            });
            int before = onEdt(session::columns);
            // The interactive cycle's case: a reflow lands after the search was admitted, so its callback never runs.
            BenchmarkSearch search = onEdt(() -> {
                var started = BenchmarkSearch.start(view, session, QUERY);
                view.setFontSize(19);
                return started;
            });
            assertThat(onEdt(session::columns)).as("the font change reflowed a new width").isNotEqualTo(before);
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!onEdt(() -> BenchmarkSearch.answered(List.of(search)))) {
                assertThat(System.nanoTime()).as("the superseded search is answered").isLessThan(deadline);
                Thread.sleep(10);
            }
            assertThat(onEdt(search::asked)).isEqualTo(2);
        } finally {
            session.close();
        }
    }

    @Test void aSearchNoReflowSupersededIsAskedOnce() throws Exception {
        TerminalSession session = IdleTerminalSession.start();
        try {
            TerminalView view = onEdt(() -> {
                var created = new TerminalView(session, TerminalOptions.defaults());
                created.setSize(800, 400);
                return created;
            });
            BenchmarkSearch search = onEdt(() -> BenchmarkSearch.start(view, session, QUERY));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!onEdt(() -> BenchmarkSearch.answered(List.of(search)))) {
                assertThat(System.nanoTime()).as("the search is answered").isLessThan(deadline);
                Thread.sleep(10);
            }
            assertThat(onEdt(search::asked)).isEqualTo(1);
        } finally {
            session.close();
        }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(action.call()); } catch (Exception thrown) { failure.set(thrown); }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }
}
