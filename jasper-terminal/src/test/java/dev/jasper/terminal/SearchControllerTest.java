package dev.jasper.terminal;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
class SearchControllerTest {
@Test void clearRejectsACompletionAlreadyQueuedForTheEdt() throws Exception {
    AtomicInteger callbacks = new AtomicInteger();
    AtomicReference<SearchController> reference = new AtomicReference<>();
    CountDownLatch searched = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(() -> {
        SearchController controller = new SearchController(query -> {
            searched.countDown();
            return List.of(new TerminalSearch.Match(0,0,0));
        }, row -> {}, () -> {});
        reference.set(controller);
        controller.findAsync(new SearchQuery("a",false,false), result -> callbacks.incrementAndGet());
        try {
            assertThat(searched.await(5,TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) { throw new AssertionError(e); }
        controller.clear();
    });
    SwingUtilities.invokeAndWait(() -> {
        assertThat(reference.get().matches()).isEmpty();
        assertThat(callbacks).hasValue(0);
        reference.get().cancelPending();
    });
}

    @Test void onlyTheLatestQueuedQueryCanPublish() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var published = new CountDownLatch(1);
        var callbacks = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var reference = new AtomicReference<SearchController>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                var controller = new SearchController(query -> {
                    if (query.text().equals("first")) {
                        entered.countDown();
                        boolean done = false;
                        while (!done) {
                            try { done = release.await(5, TimeUnit.SECONDS); }
                            catch (InterruptedException ignored) { /* A regex need not honor cancellation. */ }
                        }
                    }
                    return List.of(new TerminalSearch.Match(3, 0, 2));
                }, row -> {}, () -> {});
                reference.set(controller);
                controller.findAsync(new SearchQuery("first", false, false), result -> callbacks.add("first"));
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> {
                var controller = reference.get();
                controller.findAsync(new SearchQuery("second", false, false), result -> callbacks.add("second"));
                controller.findAsync(new SearchQuery("third", false, false), result -> {
                    callbacks.add("third"); published.countDown();
                });
                try {
                    var executor = (java.util.concurrent.ThreadPoolExecutor) SessionInspection.field(controller, "searchExecutor");
                    assertThat(executor.getQueue()).hasSize(1);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            release.countDown();
            assertThat(published.await(5, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> assertThat(callbacks).containsExactly("third"));
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(() -> { if (reference.get() != null) reference.get().cancelPending(); });
        }
    }
}
