package dev.jasper.browser.adblock;

import dev.jasper.browser.store.CapturedLog;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Ad blocking's filter list (spec section 12, plan 6 Task 10): downloaded from a local HTTP server, compiled, cached
 * in the data folder and loaded from there without the network, kept when an update fails, updated once 24 hours
 * have passed or the address changed, and stale from the seventh day. The download's own cases are
 * {@link ListDownloadTest}'s and the cache's files {@link ListCacheTest}'s. Every list here is written by hand (F36),
 * and no test reaches beyond this computer (F9): the server listens on the loopback address alone, and a list that
 * must not be downloaded gets a client that fails the test if it is asked for.
 */
class FilterListTest {
    private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");
    private static final Duration DEADLINE = Duration.ofMillis(700);
    @TempDir Path data;
    private final MutableClock clock = new MutableClock(START);
    private final AtomicReference<URI> address = new AtomicReference<>();
    private final List<HttpClient> made = new CopyOnWriteArrayList<>();
    private ListServer server;
    private FilterList filters;

    @BeforeEach void serve() throws IOException {
        server = new ListServer();
        address.set(at("/list.json"));
    }

    @AfterEach void stop() {
        server.close();
        if (filters != null) filters.close();
        for (HttpClient client : made) client.shutdownNow();
    }

    @Test void theListIsDownloadedCompiledAndCachedWithItsCount() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        FilterList.Status status = filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(status).isEqualTo(new FilterList.Status(3, Optional.of(START), false, Optional.empty(), true));
        assertThat(filters.status()).isEqualTo(status);
        assertThat(hits("/list.json")).as("one download, which Update Now joined").isEqualTo(1);
        RuleSet rules = filters.active().orElseThrow();
        assertThat(rules.size()).isEqualTo(3);
        assertThat(rules.blocks("https://ads.example/banner.js", Set.of(ResourceKind.SCRIPT), "https://news.example/")).isTrue();
        assertThat(rules.stylesheet("https://news.example/")).isEqualTo(".banner{display:none!important}\n");
        assertThat(new ListCache(data, () -> { }).read().orElseThrow().state()).as("cached, as ListCacheTest checks the files")
            .isEqualTo(new ListCache.Cached(at("/list.json").toString(), START));
    }

    @Test void aNewListLoadsTheCacheWithoutTheNetwork() throws Exception {
        filters = list(this::client);
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(filters.active()).as("turned off, a downloaded list is not applied").isEmpty();
        filters.close();

        var asked = new AtomicInteger();
        clock.advance(Duration.ofHours(23));
        filters = list(() -> {
            asked.incrementAndGet();
            throw new AssertionError("The cache is loaded without the network");
        });
        assertThat(filters.status()).as("nothing is read before it starts").isEqualTo(new FilterList.Status(0, Optional.empty(), false, Optional.empty(), false));
        filters.enabled(true);
        assertThat(filters.active()).as("turned on before it starts, it reads nothing (F8)").isEmpty();
        assertThat(filters.status().read()).as("V36: nothing read yet").isFalse();
        filters.start();
        assertThat(filters.active()).isPresent().get().extracting(RuleSet::size).isEqualTo(3);
        assertThat(filters.status()).isEqualTo(new FilterList.Status(3, Optional.of(START), false, Optional.empty(), true));
        assertThat(asked).hasValue(0);
        assertThat(hits("/list.json")).isEqualTo(1);
    }

    @Test void aFailedUpdateKeepsTheOldListAndRecordsWhy() throws Exception {
        filters = list(this::client, Duration.ofHours(1), DEADLINE);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        RuleSet kept = filters.active().orElseThrow();
        String state = Files.readString(data.resolve("adblock/state.json"));
        // A failed download, whose reasons ListDownloadTest checks, and a list that does not read.
        Map<String, String> failures = new java.util.LinkedHashMap<>();
        failures.put("/error", "the server answered HTTP 500");
        failures.put("/hold", "the download took longer than 0.7 seconds");
        failures.put("/text", "Not a content blocker list: not a JSON array");
        for (var failure : failures.entrySet()) {
            address.set(at(failure.getKey()));
            FilterList.Status status = filters.updateNow().get(20, TimeUnit.SECONDS);
            assertThat(status.error()).as(failure.getKey()).contains(failure.getValue());
            assertThat(status.rules()).as(failure.getKey()).isEqualTo(3);
            assertThat(status.updated()).as(failure.getKey()).contains(START);
            assertThat(status.updating()).isFalse();
            assertThat(filters.active()).as(failure.getKey()).containsSame(kept);
            assertThat(Files.readString(data.resolve("adblock/state.json"))).as("the cache is the old list's").isEqualTo(state);
        }
        address.set(at("/other.json"));
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS)).as("a success forgets the failure")
            .isEqualTo(new FilterList.Status(2, Optional.of(START), false, Optional.empty(), true));
        assertThat(filters.active().orElseThrow().size()).isEqualTo(2);
    }

    /** F10: the list comes over https, or over http only from this computer; anything else asks for no client. */
    @Test void anAddressThatIsNotHttpsOrThisComputersIsRefusedWithoutAClient() throws Exception {
        var asked = new AtomicInteger();
        Supplier<HttpClient> refused = () -> {
            asked.incrementAndGet();
            throw new AssertionError("No client for a refused address");
        };
        for (String refusedAddress : List.of("http://lists.example/list.json", "ftp://lists.example/list.json", "https://user@lists.example/list.json")) {
            address.set(URI.create(refusedAddress));
            filters = list(refused);
            assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).error()).as(refusedAddress)
                .contains("the filter list's address is not an https address, or an http one on this computer");
            filters.close();
        }
        assertThat(asked).hasValue(0);
    }

    /**
     * Ruling V38: a loopback list redirected to an address the list may not come from ({@code localhost.}) sends no
     * request there and caches nothing.
     */
    @Test void aListRedirectedWhereItMayNotComeFromIsNeverAskedAndCachesNothing() throws Exception {
        server.handle("/to-dot", exchange -> ListServer.redirect(exchange, "http://localhost.:" + server.port() + "/list.json"));
        address.set(at("/to-dot"));
        filters = list(this::client);
        filters.enabled(true);
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).error()).contains("the filter list was redirected to an address that is not https");
        assertThat(hits("/list.json")).isZero();
        assertThat(data.resolve("adblock").resolve("rules.json")).doesNotExist();
        assertThat(filters.active()).isEmpty();
    }

    /**
     * A failed check logs one line, with its stack trace only the first time its reason is seen, so an offline machine
     * logs no trace every hour; a refusal, whose reason is all there is to it, logs none.
     */
    @Test void aFailedCheckLogsItsTraceOncePerReason() throws Exception {
        filters = list(this::client);
        try (var log = new CapturedLog(FilterList.class.getName())) {
            for (String path : List.of("/text", "/text", "/hold", "/text", "/hold", "/error", "/error")) {
                address.set(at(path));
                assertThat(filters.updateNow().get(20, TimeUnit.SECONDS).error()).as(path).isPresent();
            }
            assertThat(log.records()).extracting(record -> record.getMessage().replace("The ad blocking filter list could not be updated: ", ""),
                    record -> record.getThrown() != null)
                .containsExactly(tuple("Not a content blocker list: not a JSON array", true), tuple("Not a content blocker list: not a JSON array", false),
                    tuple("the download took longer than 0.7 seconds", true), tuple("Not a content blocker list: not a JSON array", false),
                    tuple("the download took longer than 0.7 seconds", false), tuple("the server answered HTTP 500", false),
                    tuple("the server answered HTTP 500", false));
        }
    }

    @Test void anUpdateIsDueAfter24HoursOrFromAnotherAddress() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(hits("/list.json")).isEqualTo(1);
        clock.advance(Duration.ofHours(23));
        filters.checkNow();
        assertThat(filters.status().updating()).as("not due at 23 hours").isFalse();
        assertThat(hits("/list.json")).isEqualTo(1);
        clock.advance(Duration.ofHours(1));
        filters.checkNow();
        assertThat(filters.status().updating()).as("due at 24 hours").isTrue();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).updated()).contains(START.plus(Duration.ofHours(24)));
        assertThat(hits("/list.json")).isEqualTo(2);
        address.set(at("/other.json"));
        filters.checkNow();
        assertThat(filters.status().updating()).as("due at once from another address").isTrue();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).rules()).isEqualTo(2);
        assertThat(hits("/other.json")).isEqualTo(1);
    }

    @Test void nothingIsCheckedBeforeItStartsOrWhileTurnedOff() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.checkNow();
        assertThat(filters.status().updating()).as("before it starts").isFalse();
        filters.enabled(false);
        filters.start();
        filters.checkNow();
        assertThat(filters.status().updating()).as("turned off").isFalse();
        assertThat(hits("/list.json")).isZero();
        assertThat(made).isEmpty();
        filters.enabled(true);
        assertThat(filters.status().updating()).as("turned on once started: nothing is cached, so it downloads").isTrue();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(hits("/list.json")).isEqualTo(1);
    }

    @Test void theHourlyCheckRunsByItself() throws Exception {
        filters = list(this::client, Duration.ofMillis(20), DEADLINE);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        clock.advance(Duration.ofHours(25));
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (hits("/list.json") < 2 && System.nanoTime() < until) Thread.sleep(10);
        assertThat(hits("/list.json")).as("a check after 24 hours downloads by itself").isEqualTo(2);
    }

    /**
     * Ruling V37: the hourly check runs on a thread the list owns, one named daemon thread, made only once a check is
     * scheduled, which its close ends; a stopped list leaves nothing scheduled to hold it.
     */
    @Test void theChecksRunOnTheListsOwnDaemonThreadWhichTheCloseEnds() throws Exception {
        awaitNoCheckThread();
        filters = list(this::client);
        filters.enabled(true);
        assertThat(checkThreads()).as("none before a check is scheduled").isEmpty();
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(checkThreads()).singleElement().satisfies(thread -> assertThat(thread.isDaemon()).isTrue());
        filters.close();
        awaitNoCheckThread();
        assertThat(checkThreads()).as("the close ends it").isEmpty();
    }

    /** Ruling V37: turning off and closing leave no check scheduled, and the close ends the list's scheduler. */
    @Test void aCloseLeavesNoCheckScheduled() throws Exception {
        var scheduler = (ScheduledThreadPoolExecutor) FilterList.scheduler();
        filters = list(this::client, Duration.ofHours(1), DEADLINE, scheduler);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(scheduler.getQueue()).as("the next check").hasSize(1);
        filters.enabled(false);
        assertThat(scheduler.getQueue()).as("turned off, nothing is scheduled").isEmpty();
        filters.enabled(true);
        assertThat(scheduler.getQueue()).hasSize(1);
        filters.close();
        assertThat(scheduler.awaitTermination(10, TimeUnit.SECONDS)).as("the scheduler ends with the list").isTrue();
        assertThat(scheduler.getQueue()).isEmpty();
    }

    /**
     * Ruling V37: a check that runs before its scheduling has returned, as a scheduler may run one, is still the
     * current one, so it schedules the next and the chain goes on.
     */
    @Test void aCheckThatRunsBeforeItsSchedulingReturnsStillSchedulesTheNext() throws Exception {
        var scheduled = new java.util.concurrent.atomic.AtomicInteger();
        var scheduler = new ScheduledThreadPoolExecutor(1) {
            @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                if (scheduled.incrementAndGet() > 1) return super.schedule(command, delay, unit);
                // The first check runs at once, on this thread, before its future is returned, which is then spent.
                command.run();
                ScheduledFuture<?> spent = super.schedule(() -> { }, delay, unit);
                spent.cancel(false);
                return spent;
            }
        };
        scheduler.setRemoveOnCancelPolicy(true);
        filters = list(this::client, Duration.ofHours(1), DEADLINE, scheduler);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(scheduled.get()).as("the early check scheduled the next").isGreaterThanOrEqualTo(2);
        assertThat(scheduler.getQueue()).as("one check, the current one, is pending").hasSize(1);
        filters.enabled(false);
        assertThat(scheduler.getQueue()).as("which turning off cancels").isEmpty();
        scheduler.shutdownNow();
    }

    @Test void staleDaysCountFromTheSeventhDay() throws Exception {
        filters = list(this::client);
        assertThat(filters.staleDays(START.plus(Duration.ofDays(30)))).as("never updated").isEmpty();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(filters.staleDays(START.plus(Duration.ofDays(6)))).isEmpty();
        assertThat(filters.staleDays(START.plus(Duration.ofDays(7)).minusSeconds(1))).isEmpty();
        assertThat(filters.staleDays(START.plus(Duration.ofDays(7)))).isEqualTo(OptionalInt.of(7));
        assertThat(filters.staleDays(START.plus(Duration.ofDays(9).plusHours(12)))).isEqualTo(OptionalInt.of(9));
    }

    @Test void turnedOffTheListIsAppliedNoMore() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(filters.active()).isPresent();
        var told = new AtomicInteger();
        filters.onChanged(told::incrementAndGet);
        filters.enabled(false);
        assertThat(filters.active()).isEmpty();
        assertThat(filters.status().rules()).as("the cached list's count stays").isEqualTo(3);
        assertThat(told.get()).as("the change is told").isPositive();
        filters.enabled(true);
        assertThat(filters.active()).as("loaded again from its cache").isPresent();
        assertThat(hits("/list.json")).isEqualTo(1);

        address.set(at("/late"));
        clock.advance(Duration.ofDays(1));
        filters.checkNow();
        assertThat(filters.status().updating()).isTrue();
        filters.enabled(false);
        server.late.countDown();
        assertThat(filters.updateNow().get(20, TimeUnit.SECONDS).updated()).as("it is cached").contains(START.plus(Duration.ofDays(1)));
        assertThat(filters.active()).as("a download that ends while turned off applies nothing").isEmpty();
    }

    @Test void closingEndsTheChecksTheDownloadAndTheClient() throws Exception {
        address.set(at("/hold"));
        filters = list(this::client, Duration.ofMillis(20), Duration.ofSeconds(30));
        filters.enabled(true);
        filters.start();
        var download = filters.updateNow();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (hits("/hold") == 0 && System.nanoTime() < until) Thread.sleep(10);
        filters.close();
        assertThat(download.get(10, TimeUnit.SECONDS).updating()).as("the download ends with the close").isFalse();
        assertThat(filters.active()).isEmpty();
        assertThat(made).singleElement().satisfies(client -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!client.isTerminated() && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(client.isTerminated()).as("the client is shut down").isTrue();
        });
        address.set(at("/list.json"));
        clock.advance(Duration.ofDays(2));
        filters.enabled(true);
        filters.start();
        filters.checkNow();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).updating()).isFalse();
        Thread.sleep(100);
        assertThat(hits("/list.json")).as("a closed list downloads nothing, hourly or asked").isZero();
    }

    /**
     * Plan 6 Task 11 and ruling F3: once its client has changed, as the proxy has, the list makes a new one for its next
     * download. A download under way finishes on the old client, the redirect it follows by hand too, and the old client
     * is then shut down; with none under way, it is shut down at once.
     */
    @Test void aChangedClientIsMadeAgainAndADownloadUnderWayFinishesOnTheOldOne() throws Exception {
        server.handle("/late-moved", exchange -> {
            try {
                server.late.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            ListServer.redirect(exchange, "/list.json");
        });
        address.set(at("/late-moved"));
        filters = list(this::client, Duration.ofHours(1), Duration.ofSeconds(30));
        var download = filters.updateNow();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (hits("/late-moved") == 0 && System.nanoTime() < until) Thread.sleep(10);
        filters.clientChanged();
        server.late.countDown();
        assertThat(download.get(20, TimeUnit.SECONDS)).as("finished on the old client, its redirect included")
            .isEqualTo(new FilterList.Status(3, Optional.of(START), false, Optional.empty(), true));
        assertThat(made).hasSize(1);
        assertThat(made.getFirst().awaitTermination(Duration.ofSeconds(10))).as("then shut down").isTrue();

        address.set(at("/list.json"));
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(made).as("the next download makes a new client").hasSize(2);
        filters.clientChanged();
        assertThat(made.get(1).awaitTermination(Duration.ofSeconds(10))).as("none under way: shut down at once").isTrue();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).error()).isEmpty();
        assertThat(made).hasSize(3);
        filters.close();
        filters.clientChanged();
        assertThat(made.get(2).awaitTermination(Duration.ofSeconds(10))).as("closed with the list").isTrue();
    }

    /** Ruling V36: a list is read once its cache has been read, nothing cached included, or it has been downloaded. */
    @Test void theListIsReadOnceItsCacheIsReadOrItIsDownloaded() throws Exception {
        filters = list(this::client);
        address.set(at("/error"));
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS)).as("a failed download reads nothing")
            .isEqualTo(new FilterList.Status(0, Optional.empty(), false, Optional.of("the server answered HTTP 500"), false));
        address.set(at("/list.json"));
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).read()).as("downloaded, off").isTrue();
        filters.close();
        filters = list(() -> { throw new AssertionError("nothing is due"); });
        filters.enabled(true);
        filters.start();
        assertThat(filters.status()).as("its cache read").isEqualTo(new FilterList.Status(3, Optional.of(START), false, Optional.empty(), true));
        filters.close();
        java.nio.file.Files.walk(data).sorted(java.util.Comparator.reverseOrder()).filter(path -> !path.equals(data)).forEach(path -> path.toFile().delete());
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        assertThat(filters.status().read()).as("nothing cached is read too").isTrue();
        filters.updateNow().get(10, TimeUnit.SECONDS);
    }

    /** A cache that cannot be read is no cache: the list downloads again. */
    @Test void aCacheThatCannotBeReadIsDownloadedAgain() throws Exception {
        Files.createDirectories(data.resolve("adblock"));
        Files.writeString(data.resolve("adblock/state.json"), "{\"url\": 1}");
        Files.writeString(data.resolve("adblock/rules.json"), "[");
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        assertThat(filters.status().updating()).isTrue();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).rules()).isEqualTo(3);
    }

    private FilterList list(Supplier<HttpClient> clients) {
        return list(clients, Duration.ofHours(1), DEADLINE);
    }

    private FilterList list(Supplier<HttpClient> clients, Duration every, Duration deadline) {
        return new FilterList(data, () -> { }, Runnable::run, clients, clock, address::get, every, deadline);
    }

    private FilterList list(Supplier<HttpClient> clients, Duration every, Duration deadline, ScheduledExecutorService scheduler) {
        return new FilterList(data, () -> { }, Runnable::run, clients, clock, address::get, every, deadline, scheduler);
    }

    /** Waits until no list's check thread lives, as another test's closed list's may still be ending. */
    private static void awaitNoCheckThread() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!checkThreads().isEmpty() && System.nanoTime() < until) Thread.sleep(10);
    }

    /** The live threads named as the list's check thread. */
    private static List<Thread> checkThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.isAlive() && thread.getName().equals(FilterList.CHECK_THREAD)).toList();
    }

    /** The list's client, as the plugin makes it, kept to be shut down. */
    private HttpClient client() {
        HttpClient client = ListDownload.client(Optional.empty());
        made.add(client);
        return client;
    }

    private int hits(String path) {
        return server.hits(path);
    }

    private URI at(String path) {
        return server.at(path);
    }

    /** A clock the test moves. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }
}
