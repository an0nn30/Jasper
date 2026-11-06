package dev.jasper.browser.links;

import dev.jasper.browser.links.PrivacyQueries.Answer;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.page.PagePrivacy;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import dev.jasper.browser.store.CapturedLog;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page scripts' privacy channel (spec section 12, plan 6 Task 6). Every request is untrusted (R38): each is a flat
 * JSON object of at most 65,536 characters, from a page's top frame alone; anything else fails, malformed with 1 and
 * refused with 2, and nothing a page sends makes it throw. A notification shows only when the page's item's store
 * holds Allowed for its site, whatever the script claims; a permission request is answered from the store without a
 * prompt, or else asks the item, and a dismissed prompt answers "default". Each page is a recording stand-in whose
 * privacy hooks record what reaches its item; the router meets Chromium's browsers, frames and callbacks as proxies.
 * Pages run on the UI thread, as Jasper's do. No Chromium.
 */
class PrivacyQueriesTest {
    private static final String SITE = "https://chat.example", ROOM = SITE + "/room";
    private static final String REQUEST = "{\"op\":\"notification.request\"}";
    private final PrivacyQueries queries = new PrivacyQueries();

    @Test void aNotificationShowsOnlyWithAStoredAllowForThePagesSiteWhateverTheScriptClaims() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        assertThat(ask(site, true, ROOM, show("1", "Hi", "There"))).extracting(Answer::code).containsExactly(PrivacyQueries.REFUSED);
        site.stored.put(SITE, Decision.BLOCKED);
        assertThat(ask(site, true, ROOM, show("1", "Hi", "There"))).extracting(Answer::code).containsExactly(PrivacyQueries.REFUSED);
        site.stored.put("https://other.example", Decision.ALLOWED);
        assertThat(ask(site, true, ROOM, show("1", "Hi", "There"))).as("another site's Allow").extracting(Answer::code).containsExactly(PrivacyQueries.REFUSED);
        assertThat(ask(site, true, ROOM, "{\"op\":\"notification.show\",\"id\":\"1\",\"title\":\"Hi\",\"body\":\"There\",\"granted\":true}"))
            .as("what the script claims").containsExactly(malformed());
        assertThat(site.shown).isEmpty();

        site.stored.put(SITE, Decision.ALLOWED);
        assertThat(ask(site, true, "https://Chat.Example:443/room?x=1#top", show("1", "Hi", "There"))).containsExactly(PrivacyQueries.DONE);
        assertThat(site.shown).containsExactly(new PagePrivacy.Notice(SITE, "1", "Hi", "There"));
        site.shows = false;
        assertThat(ask(site, true, ROOM, show("2", "Too many", ""))).as("the item did not show it").extracting(Answer::code).containsExactly(PrivacyQueries.REFUSED);
        for (String noSite : List.of("about:blank", "data:text/html,hi", "jasper-about:welcome", ""))
            assertThat(ask(site, true, noSite, show("3", "Hi", ""))).as(noSite).extracting(Answer::code).containsExactly(PrivacyQueries.REFUSED);
        assertThat(site.shown).hasSize(1);
        assertThat(site.asked).as("no prompt").isEmpty();
    }

    /** Spec section 12: the polyfill runs in the top frame only, so a sub-frame's request is a page's own script's. */
    @Test void anyNotificationOpFromASubFrameIsRefused() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        site.stored.put(SITE, Decision.ALLOWED);
        for (String request : List.of(REQUEST, show("1", "Hi", ""), "{\"op\":\"notification.close\",\"id\":\"1\"}"))
            assertThat(ask(site, false, ROOM, request)).as(request).extracting(Answer::code).containsExactly(PrivacyQueries.REFUSED);
        assertThat(site.asked).isEmpty();
        assertThat(site.shown).isEmpty();
        assertThat(site.closed).isEmpty();
    }

    @Test void aMalformedRequestFailsWithOneAndNothingAPageSendsThrows() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        site.stored.put(SITE, Decision.ALLOWED);
        for (String request : Arrays.asList(null, "", "not json", "[]", "{}", "{\"op\":1}", "{\"op\":\"notification.unknown\"}",
                "{\"op\":\"open\",\"url\":\"https://x.example/\"}", "{\"op\":\"notification.show\"}",
                "{\"op\":\"notification.show\",\"id\":\"1\",\"title\":\"a\"}",
                "{\"op\":\"notification.show\",\"id\":\"1\",\"title\":\"a\",\"body\":\"b\",\"icon\":\"https://x.example/i.png\"}",
                "{\"op\":\"notification.show\",\"id\":1,\"title\":\"a\",\"body\":\"b\"}",
                "{\"op\":\"notification.show\",\"id\":\"\",\"title\":\"a\",\"body\":\"b\"}",
                "{\"op\":\"notification.show\",\"id\":\"" + "1".repeat(65) + "\",\"title\":\"a\",\"body\":\"b\"}",
                "{\"op\":\"notification.show\",\"id\":\"1\",\"title\":true,\"body\":\"b\"}",
                "{\"op\":\"notification.show\",\"id\":\"1\",\"title\":{\"a\":1},\"body\":\"b\"}",
                "{\"op\":\"notification.show\",\"id\":\"1\",\"id\":\"2\",\"title\":\"a\",\"body\":\"b\"}",
                "{\"op\":\"notification.close\"}", "{\"op\":\"notification.close\",\"id\":\"1\",\"all\":true}",
                "{\"op\":\"notification.request\",\"x\":1}", REQUEST + " trailing",
                show("1", "a", "b".repeat(PrivacyQueries.LONGEST_REQUEST)))) {
            assertThat(ask(site, true, ROOM, request)).as(String.valueOf(request)).containsExactly(malformed());
        }
        assertThat(site.asked).isEmpty();
        assertThat(site.shown).isEmpty();
        assertThat(site.closed).isEmpty();
        // A page whose item's handler throws is refused, not thrown at; the failure is logged.
        var breaking = onUi(() -> new Site(ROOM));
        breaking.stored.put(SITE, Decision.ALLOWED);
        breaking.breaking = new IllegalStateException("the item broke");
        try (var log = new CapturedLog(PagePrivacy.class.getName())) {
            assertThat(ask(breaking, true, ROOM, show("1", "a", "b"))).extracting(Answer::code).containsExactly(PrivacyQueries.REFUSED);
            assertThat(log.records()).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("the item broke"));
        }
    }

    @Test void aRequestWithAStoredDecisionAnswersAtOnceWithoutAPrompt() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        site.stored.put(SITE, Decision.BLOCKED);
        assertThat(ask(site, true, ROOM, REQUEST)).containsExactly(success("denied"));
        site.stored.put(SITE, Decision.ALLOWED);
        assertThat(ask(site, true, ROOM, REQUEST)).containsExactly(success("granted"));
        for (String noSite : List.of("about:blank", "data:text/html,hi", "jasper-about:welcome"))
            assertThat(ask(site, true, noSite, REQUEST)).as(noSite + " has no site to allow").containsExactly(success("denied"));
        assertThat(site.asked).as("no prompt").isEmpty();
    }

    /** Ruling F25: the request goes through the item's prompt; Allow grants, Block denies, and a dismissal is "default". */
    @Test void aDismissedPromptAnswersDefaultAndAllowAndBlockAnswerGrantedAndDenied() throws Exception {
        var site = onUi(() -> new Site("https://Chat.Example/room"));
        List<Answer> dismissed = ask(site, true, "https://Chat.Example/room", REQUEST);
        assertThat(dismissed).as("waiting for the user").isEmpty();
        assertThat(site.asked).containsExactly(new PermissionRequest(SITE, Permission.NOTIFICATIONS));
        onUi(() -> {
            site.answers.removeFirst().accept(false);
            return null;
        });
        assertThat(dismissed).containsExactly(success("default"));

        List<Answer> blocked = ask(site, true, ROOM, REQUEST);
        onUi(() -> {
            site.stored.put(SITE, Decision.BLOCKED);
            site.answers.removeFirst().accept(false);
            return null;
        });
        assertThat(blocked).containsExactly(success("denied"));

        site.stored.clear();
        List<Answer> allowed = ask(site, true, ROOM, REQUEST);
        onUi(() -> {
            site.stored.put(SITE, Decision.ALLOWED);
            site.answers.removeFirst().accept(true);
            return null;
        });
        assertThat(allowed).containsExactly(success("granted"));
        assertThat(site.asked).hasSize(3);

        var closed = onUi(() -> {
            var page = new Site(ROOM);
            page.page.close();
            return page;
        });
        assertThat(ask(closed, true, ROOM, REQUEST)).as("a closed page asks no one").containsExactly(success("default"));
    }

    @Test void closeClosesThePagesNotification() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        assertThat(ask(site, true, ROOM, "{\"op\":\"notification.close\",\"id\":\"7\"}")).containsExactly(PrivacyQueries.DONE);
        assertThat(site.closed).containsExactly("7");
    }

    /**
     * On Chromium's thread the router refuses at once a query that stays open for more answers, one too long, one from
     * a sub-frame, from a browser that is no page's or from no frame; nothing of them reaches the UI thread. A request of
     * exactly 65,536 characters goes on.
     */
    @Test void theRouterRefusesAtOnceAPersistentOrTooLongQueryAndOneFromASubFrameOrNoPage() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        site.stored.put(SITE, Decision.ALLOWED);
        CefBrowser browser = browser(), other = browser();
        var ui = new ArrayDeque<Runnable>();
        var router = new PrivacyQueries.Router(queries, shown -> shown == browser ? site.page : null, ui::add, () -> false);
        var replies = new Replies();
        router.onQuery(browser, frame(ROOM, true), 1, REQUEST, true, replies);
        router.onQuery(browser, frame(ROOM, true), 2, "x".repeat(PrivacyQueries.LONGEST_REQUEST + 1), false, replies);
        router.onQuery(browser, frame(ROOM, true), 3, null, false, replies);
        router.onQuery(browser, frame(ROOM, false), 4, REQUEST, false, replies);
        router.onQuery(other, frame(ROOM, true), 5, REQUEST, false, replies);
        router.onQuery(null, frame(ROOM, true), 6, REQUEST, false, replies);
        router.onQuery(browser, null, 7, REQUEST, false, replies);
        assertThat(replies.calls).as("each refused there and then").containsExactly("failure 2", "failure 1", "failure 1", "failure 2",
            "failure 2", "failure 2", "failure 2");
        assertThat(ui).as("nothing reaches the UI thread").isEmpty();

        String longest = show("1", "Hi", "");
        longest = show("1", "Hi", "b".repeat(PrivacyQueries.LONGEST_REQUEST - longest.length()));
        assertThat(longest).hasSize(PrivacyQueries.LONGEST_REQUEST);
        var shown = new Replies();
        assertThat(router.onQuery(browser, frame(ROOM, true), 8, longest, false, shown)).isTrue();
        assertThat(shown.calls).as("answered on the UI thread").isEmpty();
        runOnUi(ui);
        assertThat(shown.calls).containsExactly("success ");
        assertThat(site.shown).hasSize(1);
        assertThat(site.shown.getFirst().body()).hasSize(PrivacyQueries.LONGEST_REQUEST - show("1", "Hi", "").length());
    }

    /** No page holds more than a few requests waiting, so a loop of them reaches neither the UI thread nor the user. */
    @Test void theRouterRefusesAPagesRequestsBeyondThoseWaiting() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        var another = onUi(() -> new Site(ROOM));
        CefBrowser browser = browser(), second = browser();
        var ui = new ArrayDeque<Runnable>();
        var router = new PrivacyQueries.Router(queries, shown -> shown == browser ? site.page : shown == second ? another.page : null,
            ui::add, () -> false);
        var waiting = new Replies();
        for (int id = 1; id <= PrivacyQueries.WAITING_LIMIT; id++) router.onQuery(browser, frame(ROOM, true), id, REQUEST, false, waiting);
        var beyond = new Replies();
        router.onQuery(browser, frame(ROOM, true), 99, REQUEST, false, beyond);
        assertThat(beyond.calls).containsExactly("failure 2");
        assertThat(ui).hasSize(PrivacyQueries.WAITING_LIMIT);
        var elsewhere = new Replies();
        router.onQuery(second, frame(ROOM, true), 100, REQUEST, false, elsewhere);
        assertThat(ui).as("another page's request still goes on").hasSize(PrivacyQueries.WAITING_LIMIT + 1);
        runOnUi(ui);
        assertThat(site.asked).hasSize(PrivacyQueries.WAITING_LIMIT);
        onUi(() -> {
            site.answers.removeFirst().accept(false);
            return null;
        });
        assertThat(waiting.calls).containsExactly("success default");
        var next = new Replies();
        router.onQuery(browser, frame(ROOM, true), 101, REQUEST, false, next);
        assertThat(next.calls).as("one answered makes room for one").isEmpty();
        router.onQueryCanceled(browser, frame(ROOM, true), 2);
        var afterCancel = new Replies();
        router.onQuery(browser, frame(ROOM, true), 102, REQUEST, false, afterCancel);
        assertThat(afterCancel.calls).as("a cancelled one makes room too").isEmpty();
        var full = new Replies();
        router.onQuery(browser, frame(ROOM, true), 103, REQUEST, false, full);
        assertThat(full.calls).containsExactly("failure 2");
    }

    /**
     * A query the page cancels, or that ends as its document goes, is never answered; nor is any once Chromium has
     * stopped (ruling F43).
     */
    @Test void aCancelledQueryAndOneAnsweredAfterChromiumStoppedAreNeverAnswered() throws Exception {
        var site = onUi(() -> new Site(ROOM));
        CefBrowser browser = browser();
        var ui = new ArrayDeque<Runnable>();
        var stopped = new AtomicBoolean();
        var router = new PrivacyQueries.Router(queries, shown -> shown == browser ? site.page : null, ui::add, stopped::get);
        var cancelled = new Replies();
        router.onQuery(browser, frame(ROOM, true), 1, REQUEST, false, cancelled);
        runOnUi(ui);
        router.onQueryCanceled(browser, frame(ROOM, true), 1);
        onUi(() -> {
            site.answers.removeFirst().accept(true);
            return null;
        });
        assertThat(cancelled.calls).as("never answered").isEmpty();
        var early = new Replies();
        router.onQuery(browser, frame(ROOM, true), 2, "{\"op\":\"notification.close\",\"id\":\"1\"}", false, early);
        router.onQueryCanceled(browser, null, 2);
        runOnUi(ui);
        assertThat(early.calls).isEmpty();
        router.onQueryCanceled(browser, null, 99); // one never asked: nothing to cancel

        var late = new Replies();
        router.onQuery(browser, frame(ROOM, true), 3, REQUEST, false, late);
        runOnUi(ui);
        stopped.set(true);
        onUi(() -> {
            site.answers.removeFirst().accept(true);
            return null;
        });
        assertThat(late.calls).as("Chromium has stopped").isEmpty();
        router.onQuery(browser, frame(ROOM, true), 4, null, false, late);
        assertThat(late.calls).as("not even a refusal").isEmpty();
    }

    /** A request that the UI thread would not take is refused, not thrown. */
    @Test void aRequestTheUiThreadCannotTakeIsRefused() throws Exception {
        Page page = onUi(PrivacyQueriesTest::page);
        var router = new PrivacyQueries.Router(queries, shown -> page, task -> {
            throw new IllegalStateException("no UI thread");
        }, () -> false);
        var replies = new Replies();
        assertThat(router.onQuery(browser(), frame(ROOM, true), 1, REQUEST, false, replies)).isTrue();
        assertThat(replies.calls).containsExactly("failure 2");
    }

    /** {@code site}'s frame, the main one or not, at {@code address}, sends {@code request}: the answers it gets. */
    private List<Answer> ask(Site site, boolean main, String address, String request) throws Exception {
        var answers = new CopyOnWriteArrayList<Answer>();
        onUi(() -> {
            queries.request(site.page, main, address, request, answers::add);
            return null;
        });
        return answers;
    }

    private static String show(String id, String title, String body) {
        return "{\"op\":\"notification.show\",\"id\":\"" + id + "\",\"title\":\"" + title + "\",\"body\":\"" + body + "\"}";
    }

    private static Answer success(String text) {
        return new Answer(0, text);
    }

    private static Answer malformed() {
        return new Answer(PrivacyQueries.MALFORMED, PrivacyQueries.MALFORMED_REQUEST.text());
    }

    private static Page page() {
        return new Page(URI.create(ROOM), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
    }

    /** A page whose item's privacy hooks record what reaches them. UI thread. */
    private static final class Site {
        final Page page;
        final Map<String, Decision> stored = new java.util.concurrent.ConcurrentHashMap<>();
        final List<PermissionRequest> asked = new CopyOnWriteArrayList<>();
        final List<Consumer<Boolean>> answers = new CopyOnWriteArrayList<>();
        final List<PagePrivacy.Notice> shown = new CopyOnWriteArrayList<>();
        final List<String> closed = new CopyOnWriteArrayList<>();
        volatile boolean shows = true;
        volatile RuntimeException breaking;

        Site(String address) {
            page = new Page(URI.create(address), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
            page.privacy().onStoredDecision((origin, permission) ->
                permission == Permission.NOTIFICATIONS ? Optional.ofNullable(stored.get(origin)) : Optional.empty());
            page.privacy().onPermissionRequest((request, answer) -> {
                asked.add(request);
                answers.add(answer);
            });
            page.privacy().onNotifications(notice -> {
                if (breaking != null) throw breaking;
                if (shows) shown.add(notice);
                return shows;
            }, closed::add);
        }
    }

    /** A browser Chromium names. */
    private static CefBrowser browser() {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** A frame showing {@code url}: its browser's main frame or one of its sub-frames. */
    private static CefFrame frame(String url, boolean main) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getURL" -> url;
                case "isMain" -> main;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** A query's callback, recording {@code success <answer>} or {@code failure <code>}. */
    private static final class Replies implements CefQueryCallback {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override public void success(String response) {
            calls.add("success " + response);
        }

        @Override public void failure(int code, String message) {
            calls.add("failure " + code);
        }
    }

    /** Runs what was handed to the UI thread, in order, there, and then what that posted. */
    private static void runOnUi(Queue<Runnable> tasks) throws Exception {
        onUi(() -> {
            for (Runnable task; (task = tasks.poll()) != null; ) task.run();
            return null;
        });
    }

    private static <T> T onUi(java.util.concurrent.Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        // What the task posted, such as a permission request handed to the item, runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }
}
