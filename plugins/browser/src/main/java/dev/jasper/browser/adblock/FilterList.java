package dev.jasper.browser.adblock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Ad blocking's filter list (spec section 12): a WebKit content-blocker list from the web, downloaded
 * ({@link ListDownload}), compiled into a {@link RuleSet} and cached in the plugin's data folder ({@link ListCache}).
 *
 * <p>Making one does no I/O and makes no HTTP client (spec section 19): the list reads its cache and checks for an
 * update only once it has {@link #start started}, which a page's first start or the user's turning ad blocking on
 * does, and only while it is {@link #enabled on}. A check downloads the list when an update is due: when 24 hours have
 * passed since the last update, when nothing is cached, or when the cache came from another address, so a new address
 * downloads at its next check. Once started and on, the list checks every hour, the first check at once, on a daemon
 * thread of its own, which it makes only once a check is scheduled and ends at its close (ruling V37). Its client,
 * which the given supplier makes on the background executor at the first download, is
 * {@link ListDownload#client(Optional)}'s, made again for the next download once it has changed ({@link #clientChanged}).
 * The list read ({@link BlockerJson#read}) and compiled ({@link ContentRules#compile}) is cached, then replaces the one
 * applied; anything that fails keeps the old list, and the failure is what {@link #status()} reports until the next
 * update succeeds. {@link #updateNow()} downloads at once, on or off.
 *
 * <p>Its work runs on the background executor given, one task at a time, so a list's compiling, which may take seconds
 * on a large list, never runs on Chromium's threads or the UI thread; the list applied is published through a volatile
 * field ({@link #active()}), which Chromium's IO and UI threads read. {@link #close()} ends the checks, the download
 * under way and the client. Any thread.
 */
public final class FilterList implements AutoCloseable {
    /** How often a started list checks whether an update is due. */
    static final Duration CHECK_EVERY = Duration.ofHours(1);
    /** How long after an update the next one is due. */
    static final Duration UPDATE_AFTER = Duration.ofHours(24);
    /** From how many whole days without an update the list counts as stale. */
    static final int STALE_DAYS = 7;
    /** The name of the thread a list's hourly checks run on. */
    static final String CHECK_THREAD = "jasper-browser-filter-list";
    /** How many failures' reasons are logged with their stack traces at most. */
    private static final int TRACED = 64;
    private static final System.Logger LOG = System.getLogger(FilterList.class.getName());

    /**
     * What the list is now, as Settings shows it.
     *
     * @param rules how many rules the list last loaded or downloaded kept; 0 before any
     * @param updated when the list cached was downloaded; empty when none is known
     * @param updating whether a download is under way
     * @param error why the last update failed; empty once one succeeds
     * @param read whether this run has read the list's cache, nothing cached included, or downloaded the list: before,
     *     neither the count nor the update is known (ruling V36)
     */
    public record Status(int rules, Optional<Instant> updated, boolean updating, Optional<String> error, boolean read) {
        /** Requires every part. */
        public Status {
            Objects.requireNonNull(updated, "updated");
            Objects.requireNonNull(error, "error");
        }
    }

    private final ListCache cache;
    // Where the hourly checks wait: the list's own, one daemon thread, made only once a check is scheduled (V37).
    private final ScheduledExecutorService scheduler;
    private final Executor background;
    private final Supplier<HttpClient> clients;
    private final Clock clock;
    private final Supplier<URI> url;
    private final Duration every, deadline;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    // The list's work, run one task at a time on the background executor; guarded by itself.
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private boolean draining;
    private volatile Optional<RuleSet> active = Optional.empty();
    // Guarded by this.
    private boolean on, started, closed;
    // The cache is in memory as the rule set applied: read since the list was last turned on, or downloaded while on.
    private boolean loaded;
    private int count;
    // Whether this run has read the cache or downloaded the list.
    private boolean read;
    private ListCache.Cached cached;
    private Optional<String> error = Optional.empty();
    private CompletableFuture<Status> downloading;
    private CompletableFuture<?> exchange;
    private ScheduledFuture<?> nextCheck;
    // The number of the hourly check scheduled last; a check of any other has been replaced or cancelled.
    private long checkNumber;
    // The reasons whose failures were logged with their stack traces.
    private final Set<String> traced = new HashSet<>();
    private HttpClient client;
    // Clients changed while a download was under way, which the download's end shuts down; and the number of the
    // client's changes, by which a client made while one came is made again.
    private final List<HttpClient> retired = new ArrayList<>();
    private long clientChanges;

    /**
     * The list of the plugin's data folder, which does nothing yet.
     *
     * @param dataDir the plugin's data folder, which holds the list's folder
     * @param restrict makes the data folder its owner's alone; run before the list's first read or write there
     * @param background where the list's work runs: the plugin's background executor
     * @param http makes the list's client, on the background executor at the first download and after it has changed:
     *     {@link ListDownload#client(Optional)}
     * @param clock the time of an update, and of a check
     * @param url the list's address, read at each check: the Filter List URL setting
     */
    public FilterList(Path dataDir, Runnable restrict, Executor background, Supplier<HttpClient> http, Clock clock, Supplier<URI> url) {
        this(dataDir, restrict, background, http, clock, url, CHECK_EVERY, ListDownload.DOWNLOAD_TIMEOUT);
    }

    /** As the public constructor, checking every {@code every} and giving each download {@code deadline} in all. */
    FilterList(Path dataDir, Runnable restrict, Executor background, Supplier<HttpClient> http, Clock clock, Supplier<URI> url,
               Duration every, Duration deadline) {
        this(dataDir, restrict, background, http, clock, url, every, deadline, scheduler());
    }

    /** As the public constructor, checking every {@code every} on {@code scheduler}, which the list owns from now on. */
    FilterList(Path dataDir, Runnable restrict, Executor background, Supplier<HttpClient> http, Clock clock, Supplier<URI> url,
               Duration every, Duration deadline, ScheduledExecutorService scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        cache = new ListCache(dataDir, restrict);
        this.background = Objects.requireNonNull(background, "background");
        clients = Objects.requireNonNull(http, "http");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.url = Objects.requireNonNull(url, "url");
        this.every = Objects.requireNonNull(every, "every");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    /**
     * A scheduler of hourly checks as the list owns one: one daemon thread, {@value #CHECK_THREAD}, made only once a
     * check is scheduled, which a cancel takes out of its queue at once.
     *
     * @return the scheduler
     */
    static ScheduledExecutorService scheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name(CHECK_THREAD).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * The rule set applied: the list's, while it is on and has one. Any thread; it reads a volatile field.
     *
     * @return the rule set; empty while off, before a list has loaded, and once closed
     */
    public Optional<RuleSet> active() {
        return active;
    }

    /**
     * Turns the list on or off. On, once it has started, it reads its cache in the background, then checks for an
     * update and goes on checking every hour; before it has started, it reads nothing. Off, it applies no rule set at
     * once and checks no more, and the rule set is let go. UI thread, or any other.
     *
     * @param on whether ad blocking is on
     */
    public void enabled(boolean on) {
        boolean check;
        synchronized (this) {
            if (closed) return;
            this.on = on;
            check = on && started;
            if (!on) {
                active = Optional.empty();
                loaded = false;
                stopChecks();
            }
        }
        if (check) checks();
        changed();
    }

    /**
     * Starts the list, once: from now on, while it is on, it reads its cache and checks for an update every hour, the
     * first check at once. A page's first start, and the user's turning ad blocking on, start it. Any thread.
     */
    public void start() {
        synchronized (this) {
            if (closed || started) return;
            started = true;
            if (!on) return;
        }
        checks();
    }

    /** Checks at once whether an update is due, as the hourly check does, when the list has started and is on. Any thread. */
    public void checkNow() {
        synchronized (this) {
            if (closed || !started || !on) return;
        }
        later(this::refresh);
    }

    /**
     * Downloads the list at once, due or not, on or off, and caches it; while a download is under way, it is that one.
     * Any thread.
     *
     * @return the list's status once the download has ended, which tells its failure; at once once closed
     */
    public CompletableFuture<Status> updateNow() {
        return download().copy();
    }

    /**
     * What the list is now. Any thread.
     *
     * @return the status
     */
    public synchronized Status status() {
        return new Status(count, Optional.ofNullable(cached).map(ListCache.Cached::updated), downloading != null, error, read);
    }

    /**
     * How many whole days the list cached has gone without an update, when it is stale: from the seventh day on.
     *
     * @param now the time now
     * @return the days; empty before the seventh day, and when no list is known
     */
    public synchronized OptionalInt staleDays(Instant now) {
        if (cached == null) return OptionalInt.empty();
        long days = Duration.between(cached.updated(), now).toDays();
        return days >= STALE_DAYS ? OptionalInt.of((int) Math.min(days, Integer.MAX_VALUE)) : OptionalInt.empty();
    }

    /**
     * The client the list's supplier makes has changed, as the HTTP proxy has (plan 6's F3): the next download makes a new
     * one. A download under way finishes on the old client, which is shut down once it has ended; with none under way,
     * the old client is shut down at once. Any thread.
     */
    public void clientChanged() {
        HttpClient old;
        synchronized (this) {
            clientChanges++;
            if (closed || client == null) return;
            old = client;
            client = null;
            if (downloading != null) {
                retired.add(old);
                return;
            }
        }
        old.shutdownNow();
    }

    /**
     * Has {@code listener} run whenever the status or the rule set applied changes, on the thread that changed it.
     *
     * @param listener what to run; it must return quickly and must not throw
     */
    public void onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Ends the list: no rule set is applied, the checks end, a download under way ends, telling its status, and the
     * client is shut down. Later calls do nothing. Any thread; it never blocks.
     */
    @Override public void close() {
        CompletableFuture<Status> ended;
        CompletableFuture<?> ending;
        HttpClient made;
        synchronized (this) {
            if (closed) return;
            closed = true;
            active = Optional.empty();
            loaded = false;
            stopChecks();
            ended = downloading;
            downloading = null;
            ending = exchange;
            exchange = null;
            made = client;
            client = null;
        }
        scheduler.shutdownNow();
        if (ending != null) ending.cancel(true);
        if (made != null) made.shutdownNow();
        shutDownRetired();
        // Its listeners, which end with the plugin, are told nothing more.
        if (ended != null) ended.complete(status());
    }

    /** A check now, then every hour, replacing the checks scheduled before. */
    private void checks() {
        synchronized (this) {
            if (closed || !on) return;
            stopChecks();
        }
        later(this::refresh);
        schedule();
    }

    /**
     * No hourly check is scheduled from now on: the one scheduled is cancelled, out of the scheduler's queue, and any of
     * an earlier number does nothing. Holds the lock.
     */
    private void stopChecks() {
        checkNumber++;
        if (nextCheck != null) nextCheck.cancel(false);
        nextCheck = null;
    }

    /**
     * The next hourly check, unless the list is closed or off. Each check knows its number before it is scheduled, so a
     * check the scheduler runs before {@code schedule} has returned its future is still the current one (V37).
     */
    private void schedule() {
        synchronized (this) {
            if (closed || !on || !started) return;
            long number = ++checkNumber;
            ScheduledFuture<?> scheduled;
            try {
                scheduled = scheduler.schedule(() -> tick(number), every.toNanos(), TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException stopped) {
                return;
            }
            // A check that ran at once has scheduled the next, whose future it recorded.
            if (checkNumber == number) nextCheck = scheduled;
        }
    }

    /** The hourly check {@code number} is due: it checks, and schedules the next, unless the checks have changed since. */
    private void tick(long number) {
        synchronized (this) {
            if (closed || !on || !started || checkNumber != number) return;
            nextCheck = null;
        }
        later(this::refresh);
        schedule();
    }

    /**
     * The check: when the list is on and started, its cache is read if it is not in memory, then the list is
     * downloaded if an update is due. Background executor.
     */
    private void refresh() {
        synchronized (this) {
            if (closed || !on || !started) return;
        }
        if (!isLoaded()) load();
        URI address = url.get();
        boolean due;
        synchronized (this) {
            if (closed || !on || downloading != null) return;
            due = due(clock.instant(), address);
        }
        if (due) download();
    }

    private synchronized boolean isLoaded() {
        return loaded;
    }

    /** Whether an update is due: 24 hours after the last, or none cached, or one cached from another address. */
    private boolean due(Instant now, URI address) {
        if (cached == null || !cached.url().equals(String.valueOf(address))) return true;
        return now.isBefore(cached.updated()) || !now.isBefore(cached.updated().plus(UPDATE_AFTER));
    }

    /** Reads the cache, and applies its rule set while the list is on; a cache that cannot be read is none. Background executor. */
    private void load() {
        ListCache.Cached state = null;
        RuleSet rules = null;
        int kept = 0;
        try {
            Optional<ListCache.Contents> read = cache.read();
            if (read.isPresent()) {
                ContentRules.Compiled compiled = ContentRules.compile(BlockerJson.read(read.get().rules()));
                rules = RuleSet.of(compiled.rules());
                kept = compiled.rules().size();
                state = read.get().state();
            }
        } catch (NoSuchFileException missing) {
            // Nothing cached.
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "The ad blocking filter list's cache could not be read, so the list downloads again",
                unreadable);
        }
        synchronized (this) {
            if (closed) return;
            cached = state;
            count = state == null ? count : kept;
            read = true;
            loaded = on;
            if (on) active = Optional.ofNullable(rules);
        }
        changed();
    }

    /** The download under way, or a new one, unless the list is closed. */
    private CompletableFuture<Status> download() {
        CompletableFuture<Status> result;
        synchronized (this) {
            if (closed) return CompletableFuture.completedFuture(status());
            if (downloading != null) return downloading;
            result = new CompletableFuture<>();
            downloading = result;
        }
        changed();
        later(() -> fetch(result));
        return result;
    }

    /**
     * Starts the download of the list, at an address the list may come from, and hands its answer to {@link #install}
     * on the background executor. An address the list may not come from asks for no client. Background executor.
     */
    private void fetch(CompletableFuture<Status> result) {
        URI address = url.get();
        if (!ListDownload.fetches(address)) {
            failed(result, address, new ListDownload.Refused(ListDownload.REFUSED_ADDRESS));
            return;
        }
        CompletableFuture<byte[]> answer;
        try {
            HttpClient http = madeClient();
            if (http == null) return; // closed: the close told the result
            answer = ListDownload.get(http, address, deadline);
        } catch (RuntimeException | Error refused) {
            failed(result, address, refused);
            // An error, such as a test's client that must not be asked for, is the executor's to report too.
            if (refused instanceof Error error) throw error;
            return;
        }
        synchronized (this) {
            if (closed) {
                answer.cancel(true);
                return;
            }
            exchange = answer;
        }
        answer.whenComplete((body, failure) -> later(() -> install(result, address, body, failure)));
    }

    /**
     * The client, made on the first download and after it has changed; one made while it changed is made again. Null
     * once closed. Background executor.
     */
    private HttpClient madeClient() {
        while (true) {
            long changes;
            synchronized (this) {
                if (closed || client != null) return client;
                changes = clientChanges;
            }
            HttpClient made = clients.get();
            synchronized (this) {
                if (!closed && clientChanges == changes) {
                    client = made;
                    return made;
                }
            }
            made.shutdownNow();
        }
    }

    /** Shuts down the clients changed while a download was under way, which has ended, or the list has closed. */
    private void shutDownRetired() {
        List<HttpClient> ending;
        synchronized (this) {
            ending = List.copyOf(retired);
            retired.clear();
        }
        for (HttpClient old : ending) old.shutdownNow();
    }

    /**
     * The download has ended: a list read, compiled and cached replaces the one applied, while the list is on; anything
     * else keeps it and records why. Background executor.
     */
    private void install(CompletableFuture<Status> result, URI address, byte[] body, Throwable failure) {
        synchronized (this) {
            if (closed || downloading != result) return;
            exchange = null;
        }
        try {
            if (failure != null) throw failure;
            ContentRules.Compiled compiled = ContentRules.compile(BlockerJson.read(new String(body, StandardCharsets.UTF_8)));
            RuleSet rules = RuleSet.of(compiled.rules());
            Instant updated = clock.instant();
            synchronized (this) {
                if (closed) return;
            }
            cache.write(address, updated, compiled);
            synchronized (this) {
                if (closed) return;
                cached = new ListCache.Cached(address.toString(), updated);
                count = compiled.rules().size();
                read = true;
                error = Optional.empty();
                downloading = null;
                loaded = on;
                if (on) active = Optional.of(rules);
            }
            shutDownRetired();
            changed();
            result.complete(status());
        } catch (Throwable thrown) {
            failed(result, address, thrown);
        }
    }

    /**
     * The download {@code result} of {@code address} failed: the old list stays, and the failure is recorded, logged and
     * told. The log has one line for each failure, and the stack trace the first time a reason is seen, so a machine that
     * is offline logs no trace every hour.
     */
    private void failed(CompletableFuture<Status> result, URI address, Throwable failure) {
        String reason = ListDownload.reason(failure, address, deadline);
        boolean trace;
        synchronized (this) {
            if (closed || downloading != result) return;
            downloading = null;
            exchange = null;
            error = Optional.of(reason);
            // A refusal's reason is all there is to it; another's trace is logged the first time its reason is seen.
            trace = !(failure instanceof ListDownload.Refused) && traced.size() < TRACED && traced.add(reason);
        }
        shutDownRetired();
        LOG.log(System.Logger.Level.WARNING, "The ad blocking filter list could not be updated: " + reason, trace ? failure : null);
        changed();
        result.complete(status());
    }

    /** Runs {@code task} on the background executor after the list's tasks before it; nothing once the executor refuses. */
    private void later(Runnable task) {
        synchronized (tasks) {
            tasks.add(task);
        }
        resume();
    }

    /** Has the background executor run the list's tasks, unless it does already or none is left. */
    private void resume() {
        synchronized (tasks) {
            if (draining || tasks.isEmpty()) return;
            draining = true;
        }
        try {
            background.execute(this::drain);
        } catch (RejectedExecutionException stopped) {
            synchronized (tasks) {
                tasks.clear();
                draining = false;
            }
        }
    }

    /** Runs the list's tasks, in order, until none is left; after an error, the rest run on another turn. Background executor. */
    private void drain() {
        boolean emptied = false;
        try {
            while (true) {
                Runnable next;
                synchronized (tasks) {
                    next = tasks.poll();
                    if (next == null) {
                        draining = false;
                        emptied = true;
                        return;
                    }
                }
                try {
                    next.run();
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "The ad blocking filter list's work failed", failure);
                }
            }
        } finally {
            if (!emptied) {
                synchronized (tasks) {
                    draining = false;
                }
                resume();
            }
        }
    }

    /** Tells each listener that the list changed. */
    private void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A listener of the ad blocking filter list failed", failure);
            }
        }
    }
}
