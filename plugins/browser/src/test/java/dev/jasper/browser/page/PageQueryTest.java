package dev.jasper.browser.page;

import dev.jasper.browser.store.CapturedLog;
import java.awt.EventQueue;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A page's request answered later (plan 8's Task 4a): it is answered once, and never with JSON once the page's main
 * frame shows another origin than the one that asked; nothing is sent after its cancel, which may come on any thread
 * and is told on the UI thread; its activation is asked once; and a page hands each query to its password manager, or
 * refuses it when it has none or has closed, and its close cancels those its manager has not answered. The router's
 * reply is a recording stand-in, and the page has no browser.
 */
class PageQueryTest {
    private static final Optional<String> ASKER = Optional.of("https://login.example");
    private static final Map<String, Object> PICK = Map.of("op", "login.pick", "field", "password");
    private final List<String> replies = new CopyOnWriteArrayList<>();
    // The origin the page's main frame shows now, as the bridge would read it.
    private final AtomicReference<Optional<String>> shown = new AtomicReference<>(ASKER);

    @Test void itIsAnsweredOnce() throws Exception {
        onUiThread(() -> {
            PageQuery query = query(page(), ASKER, () -> true);
            assertThat(query.answer("{\"password\":\"pw\"}")).isTrue();
            assertThat(query.answer("{}")).as("a second answer").isFalse();
            assertThat(query.fail(PageQuery.BUSY, "Busy.")).as("a failure after it").isFalse();
            query.cancelled();
            assertThat(replies).containsExactly("0 {\"password\":\"pw\"}");
        });
    }

    @Test void itFailsOnceAndIsAnsweredNothingAfter() throws Exception {
        onUiThread(() -> {
            PageQuery query = query(page(), ASKER, () -> true);
            assertThat(query.fail(PageQuery.REFUSED, "Refused.")).isTrue();
            assertThat(query.fail(PageQuery.BUSY, "Busy.")).isFalse();
            assertThat(query.answer("{\"password\":\"pw\"}")).isFalse();
            assertThat(replies).containsExactly("2 Refused.");
        });
    }

    /** The answer was meant for the document that asked: another origin, or an opaque one, is told it has gone. */
    @Test void anOriginThatChangedIsToldGoneAndSentNoJson() throws Exception {
        onUiThread(() -> {
            Page page = page();
            PageQuery moved = query(page, ASKER, () -> true);
            shown.set(Optional.of("https://other.example"));
            assertThat(moved.answer("{\"password\":\"pw\"}")).as("the page was sent no JSON").isFalse();
            assertThat(moved.answer("{\"password\":\"pw\"}")).isFalse();
            shown.set(Optional.empty());
            assertThat(query(page, ASKER, () -> true).answer("{\"password\":\"pw\"}")).as("now opaque").isFalse();
            assertThat(query(page, Optional.empty(), () -> true).answer("{\"password\":\"pw\"}"))
                .as("an opaque origin equals no other, not even an opaque one").isFalse();
            assertThat(replies).hasSize(3).allSatisfy(reply -> assertThat(reply).startsWith(PageQuery.GONE + " ").doesNotContain("pw"));
        });
    }

    @Test void nothingIsSentAfterTheCancel() throws Exception {
        onUiThread(() -> {
            PageQuery query = query(page(), ASKER, () -> true);
            var told = new AtomicInteger();
            query.onCancelled(told::incrementAndGet);
            query.cancelled();
            query.cancelled();
            assertThat(told).as("told once").hasValue(1);
            assertThat(query.answer("{\"password\":\"pw\"}")).isFalse();
            assertThat(query.fail(PageQuery.REFUSED, "Refused.")).isFalse();
            assertThat(replies).isEmpty();
        });
    }

    /** An answer that comes after it is cancelled, or a cancel after it was answered, changes nothing. */
    @Test void aCancelIsNotToldOnceTheQueryWasAnswered() throws Exception {
        onUiThread(() -> {
            PageQuery query = query(page(), ASKER, () -> true);
            var told = new AtomicInteger();
            var registration = query.onCancelled(told::incrementAndGet);
            assertThat(query.answer("{}")).isTrue();
            query.cancelled();
            assertThat(told).hasValue(0);
            registration.close();
            PageQuery ended = query(page(), ASKER, () -> true);
            ended.onCancelled(told::incrementAndGet).close();
            ended.cancelled();
            assertThat(told).as("a registration that ended hears nothing").hasValue(0);
        });
    }

    /** F14: an answer on one thread and Chromium's cancel on another: at most one is sent, and exactly one wins. */
    @Test void aCancelRacingAnAnswerSendsAtMostOne() throws Exception {
        var made = new AtomicReference<Page>();
        onUiThread(() -> made.set(page()));
        for (int round = 0; round < 200; round++) {
            replies.clear();
            PageQuery query = query(made.get(), ASKER, () -> true);
            var cancels = new AtomicInteger();
            onUiThread(() -> query.onCancelled(cancels::incrementAndGet));
            var start = new CountDownLatch(1);
            var answered = new AtomicBoolean();
            Thread answering = Thread.ofPlatform().start(() -> {
                await(start);
                answered.set(query.answer("{\"password\":\"pw\"}"));
            });
            Thread cancelling = Thread.ofPlatform().start(() -> {
                await(start);
                query.cancelled();
            });
            start.countDown();
            answering.join(10_000);
            cancelling.join(10_000);
            onUiThread(() -> { }); // the cancel's listeners are told on the UI thread
            assertThat(replies.size()).as("round %d", round).isLessThanOrEqualTo(1);
            assertThat(replies.size() + cancels.get()).as("round %d: the answer or the cancel", round).isEqualTo(1);
            assertThat(answered.get()).as("round %d", round).isEqualTo(replies.size() == 1);
        }
    }

    @Test void itsActivationIsAskedOnce() throws Exception {
        onUiThread(() -> {
            var asked = new AtomicInteger();
            PageQuery query = query(page(), ASKER, () -> asked.incrementAndGet() == 1);
            assertThat(query.consumeActivation()).isTrue();
            assertThat(query.consumeActivation()).as("spent").isFalse();
            assertThat(asked).hasValue(1);
            PageQuery refused = query(page(), ASKER, () -> asked.incrementAndGet() < 0);
            assertThat(refused.consumeActivation()).isFalse();
            assertThat(refused.consumeActivation()).isFalse();
            assertThat(asked).as("asked once more").hasValue(2);
        });
    }

    /** F14: Chromium cancels on its own thread; the query's listeners hear it on the UI thread. */
    @Test void aCancelOnAnotherThreadIsToldOnTheUiThread() throws Exception {
        var made = new AtomicReference<Page>();
        onUiThread(() -> made.set(page()));
        PageQuery query = query(made.get(), ASKER, () -> true);
        var told = new CountDownLatch(1);
        var onUiThread = new AtomicBoolean();
        onUiThread(() -> query.onCancelled(() -> {
            onUiThread.set(EventQueue.isDispatchThread());
            told.countDown();
        }));
        assertThat(EventQueue.isDispatchThread()).isFalse();
        query.cancelled();
        assertThat(told.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(onUiThread).isTrue();
        assertThat(query.answer("{}")).isFalse();
        assertThat(replies).isEmpty();
    }

    /** A listener given once the cancel was told hears it at once, as the picker may open after the cancel came. */
    @Test void aListenerGivenAfterTheCancelIsToldAtOnce() throws Exception {
        onUiThread(() -> {
            PageQuery query = query(page(), ASKER, () -> true);
            query.cancelled();
            var told = new AtomicInteger();
            query.onCancelled(told::incrementAndGet);
            assertThat(told).hasValue(1);
        });
    }

    /** Review round 1: a page origin that cannot be read is no origin that asked: the page is told it has gone. */
    @Test void anOriginThatCannotBeReadIsToldGone() throws Exception {
        onUiThread(() -> {
            var query = new PageQuery(page(), ASKER, PICK, () -> true, () -> { throw new IllegalStateException("no document"); },
                (code, text) -> replies.add(code + " " + text));
            try (var log = new CapturedLog(PageQuery.class.getName())) {
                assertThat(query.answer("{\"password\":\"pw\"}")).isFalse();
                assertThat(log.records()).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("no document"));
            }
            assertThat(replies).singleElement().asString().startsWith(PageQuery.GONE + " ").doesNotContain("pw");
            assertThat(query.answer("{}")).isFalse();
            assertThat(replies).hasSize(1);
        });
    }

    /**
     * Review round 1: a cancel listener that throws is logged with its exception, and stops neither the other listeners
     * nor the page's close.
     */
    @Test void aThrowingCancelListenerStopsNeitherTheOthersNorThePagesClose() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.handleLogins("login();", query -> { });
            PageQuery first = query(page, ASKER, () -> true), second = query(page, ASKER, () -> true);
            page.loginQuery(first);
            page.loginQuery(second);
            var told = new ArrayList<String>();
            first.onCancelled(() -> { throw new IllegalStateException("the picker broke"); });
            first.onCancelled(() -> told.add("first"));
            second.onCancelled(() -> told.add("second"));
            page.onClose(() -> told.add("closed"));
            try (var log = new CapturedLog(PageQuery.class.getName())) {
                page.close();
                assertThat(log.records()).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("the picker broke"));
            }
            assertThat(told).containsExactly("first", "second", "closed");
            assertThat(replies).isEmpty();
        });
    }

    @Test void itKeepsWhoAskedAndWhat() throws Exception {
        onUiThread(() -> {
            Page page = page();
            PageQuery query = query(page, ASKER, () -> true);
            assertThat(query.page()).isSameAs(page);
            assertThat(query.origin()).isEqualTo(ASKER);
            assertThat(query.request()).isEqualTo(PICK);
            assertThatThrownBy(() -> query.request().put("field", "username")).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> query.fail(0, "Not a failure.")).isInstanceOf(IllegalArgumentException.class);
            assertThat(replies).isEmpty();
        });
    }

    /** F18: a page hands each of its queries to its password manager, which its item gives once. */
    @Test void aPageHandsEachQueryToItsPasswordManager() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var held = new ArrayList<PageQuery>();
            page.handleLogins("login();", held::add);
            PageQuery query = query(page, ASKER, () -> true);
            page.loginQuery(query);
            assertThat(held).containsExactly(query);
            assertThat(replies).isEmpty();
            assertThatThrownBy(() -> page.handleLogins("again();", held::add)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> page.loginQuery(query(page(), ASKER, () -> true))).as("another page's")
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(held).hasSize(1);
        });
    }

    @Test void aPageWithoutAPasswordManagerRefusesTheQuery() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.loginQuery(query(page, ASKER, () -> true));
            assertThat(replies).containsExactly(PageQuery.REFUSED + " This page has no password manager.");
        });
    }

    /**
     * F18: the page's close ends its password manager: the queries it has not answered are cancelled, and are sent
     * nothing; one the page gets afterwards is refused, and its documents run the login script no more.
     */
    @Test void aClosedPageCancelsTheQueriesItsManagerHeldAndRefusesTheNext() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var held = new ArrayList<PageQuery>();
            page.handleLogins("login();", held::add);
            PageQuery open = query(page, ASKER, () -> true), answered = query(page, ASKER, () -> true);
            page.loginQuery(open);
            page.loginQuery(answered);
            assertThat(answered.answer("{}")).isTrue();
            var cancels = new AtomicInteger();
            open.onCancelled(cancels::incrementAndGet);
            answered.onCancelled(cancels::incrementAndGet);
            assertThat(page.loginScript("https://login.example/sign-in")).hasValue("login();");
            page.close();
            assertThat(cancels).as("the open one").hasValue(1);
            assertThat(open.answer("{\"password\":\"pw\"}")).isFalse();
            page.loginQuery(query(page, ASKER, () -> true));
            assertThat(held).as("no handler after the close").hasSize(2);
            assertThat(replies).containsExactly("0 {}", PageQuery.REFUSED + " This page has no password manager.");
            assertThat(page.loginScript("https://login.example/sign-in")).isEmpty();
        });
    }

    /** The login script is the page's only once its item gave one, and runs only where its rule allows. */
    @Test void theLoginScriptIsThePagesOnceItsItemGaveOne() throws Exception {
        onUiThread(() -> {
            Page page = page();
            assertThat(page.loginScript("https://login.example/sign-in")).isEmpty();
            page.handleLogins("login();", query -> { });
            assertThat(page.loginScript("https://login.example/sign-in")).hasValue("login();");
            assertThat(page.loginScript("http://intranet.example/")).hasValue("login();");
            assertThat(page.loginScript("file:///tmp/sign-in.html")).isEmpty();
            assertThat(page.loginScript("jasper-about:welcome")).isEmpty();
        });
    }

    /** A query of {@code page}, from {@code origin}, whose activation {@code activation} answers. */
    private PageQuery query(Page page, Optional<String> origin, BooleanSupplier activation) {
        return new PageQuery(page, origin, PICK, activation, shown::get, (code, text) -> replies.add(code + " " + text));
    }

    private static Page page() {
        return new Page(URI.create("https://login.example/sign-in"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.persistent());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
