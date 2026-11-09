package dev.jasper.browser.permissions;

import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.DatabaseClosedException;
import dev.jasper.sdk.Subscription;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * One data store's site permissions (spec section 12), in its database's {@code permissions} table: for each site and
 * permission, the decision the user stored. They are read from a cache, at once and on any thread, which
 * {@link #load()} fills with every row; each change goes into the cache at once, and its row is queued in the same step,
 * so the database takes the changes in the order the cache did, and writes them on its thread after. A change made
 * before the rows have loaded is made again over them as they arrive, so it stands. As its data store closes, the store
 * empties its cache ({@link #discard()}), writing nothing. Making the store does no I/O.
 *
 * <p>A write that fails is logged and told once, with why, to the store's {@code unsaved} listener on the UI thread,
 * which posts "Site permissions could not be saved"; the cache keeps the new value for the rest of the run. A write the
 * database refuses because it has closed ({@link DatabaseClosedException}), as a /dev/null group's does with its last
 * item, is dropped and logged for debugging only.
 */
public final class PermissionStore {
    /** What the notification of a write that failed says. */
    public static final String UNSAVED = "Site permissions could not be saved";
    private static final System.Logger LOG = System.getLogger(PermissionStore.class.getName());
    private static final Comparator<Grant> ORDER = Comparator.comparing(Grant::origin).thenComparing(Grant::permission);
    private static final String SET = "INSERT INTO permissions (origin, type, decision, created, updated) VALUES (?, ?, ?, ?, ?)"
        + " ON CONFLICT (origin, type) DO UPDATE SET decision = excluded.decision, updated = excluded.updated";
    private final BrowserDatabase database;
    private final Executor ui;
    private final Consumer<Throwable> unsaved;
    private final LongSupplier clock;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    // The decisions as the store holds them now; replaced whole, under the lock, once the rows have loaded.
    private volatile Map<Key, Grant> grants = new ConcurrentHashMap<>();
    // Under the lock: each change made before the rows loaded, to be made again over them; null once they have, or could not.
    private List<Change> early = new ArrayList<>();
    // Under the lock: the rows' load, once asked for.
    private CompletableFuture<Void> loaded;
    // Under the lock: whether the store's data store has closed, after which the store holds and takes nothing.
    private boolean discarded;

    private record Key(String origin, Permission permission) { }

    /** A change to the decisions: what it does to them, and the sites whose decisions it changed. */
    @FunctionalInterface private interface Change {
        Set<String> apply(Map<Key, Grant> grants);
    }

    /** A statement that writes the change it was made for. */
    @FunctionalInterface private interface Write {
        void run(Connection connection) throws SQLException;
    }

    /**
     * The site permissions in {@code database}, which another of its stores opens and closes.
     *
     * @param database the data store's database
     * @param ui runs a task on the UI thread, where the listeners are told
     * @param unsaved told, on the UI thread, why each write that failed was not saved
     */
    public PermissionStore(BrowserDatabase database, Executor ui, Consumer<Throwable> unsaved) {
        this(database, ui, unsaved, System::currentTimeMillis);
    }

    /** As the public constructor, the time of each change read from {@code clock}, in milliseconds since the epoch. */
    PermissionStore(BrowserDatabase database, Executor ui, Consumer<Throwable> unsaved, LongSupplier clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.unsaved = Objects.requireNonNull(unsaved, "unsaved");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Reads every row into the cache, the first time it is called, on the database's thread, which it opens. Any thread.
     *
     * @return the same future each time: it completes once the rows are in the cache, and fails, the failure logged,
     *     when they cannot be read; the cache then holds only what was set since
     */
    public CompletableFuture<Void> load() {
        CompletableFuture<Void> load;
        synchronized (lock) {
            if (loaded != null) return loaded;
            load = loaded = new CompletableFuture<>();
            if (discarded) {
                load.completeExceptionally(new DatabaseClosedException());
                return load;
            }
        }
        database.call(PermissionStore::rows).whenComplete((rows, failure) -> loaded(load, rows, failure));
        return load;
    }

    /**
     * The decision stored for {@code permission} of {@code origin}. Any thread.
     *
     * @param origin the site
     * @param permission the permission
     * @return the decision; empty when none is stored
     */
    public Optional<Decision> decision(String origin, Permission permission) {
        Grant grant = grants.get(new Key(Objects.requireNonNull(origin, "origin"), Objects.requireNonNull(permission, "permission")));
        return grant == null ? Optional.empty() : Optional.of(grant.decision());
    }

    /**
     * What {@code origin}'s stored audio playback decision says. Any thread.
     *
     * @param origin the site
     * @return {@link AudioMode#ALLOW} for Allowed, {@link AudioMode#BLOCK} for Blocked, and {@link AudioMode#ASK} when
     *     none is stored
     */
    public AudioMode audioMode(String origin) {
        return AudioMode.of(decision(origin, Permission.AUDIO_PLAYBACK));
    }

    /**
     * Every stored decision. Any thread.
     *
     * @return them, by site, then in {@link Permission}'s order
     */
    public List<Grant> all() {
        return grants.values().stream().sorted(ORDER).toList();
    }

    /**
     * The decisions stored for {@code origin}. Any thread.
     *
     * @param origin the site
     * @return them, in {@link Permission}'s order
     */
    public List<Grant> of(String origin) {
        Objects.requireNonNull(origin, "origin");
        Map<Key, Grant> now = grants;
        var site = new ArrayList<Grant>();
        for (Permission permission : Permission.values()) {
            Grant grant = now.get(new Key(origin, permission));
            if (grant != null) site.add(grant);
        }
        return List.copyOf(site);
    }

    /**
     * Stores {@code decision} for {@code permission} of {@code origin}: at once in the cache, then in its row. A site's
     * permission keeps when it was first stored, and its last change moves when it was updated. Any thread.
     *
     * @param origin the site
     * @param permission the permission
     * @param decision what the user chose
     * @return completes once the row is written; fails, the failure already logged and told, when it is not, and at once
     *     once the store's data store has closed
     */
    public CompletableFuture<Void> set(String origin, Permission permission, Decision decision) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(decision, "decision");
        var key = new Key(origin, permission);
        Instant now = Instant.ofEpochMilli(clock.getAsLong());
        return change(grants -> {
            grants.compute(key, (same, was) -> new Grant(origin, permission, decision, was == null ? now : was.created(), now));
            return Set.of(origin);
        }, connection -> {
            try (var insert = connection.prepareStatement(SET)) {
                insert.setString(1, origin);
                insert.setString(2, permission.token());
                insert.setString(3, decision.token());
                insert.setLong(4, now.toEpochMilli());
                insert.setLong(5, now.toEpochMilli());
                insert.executeUpdate();
            }
        }).written();
    }

    /**
     * Forgets the decision stored for {@code permission} of {@code origin}, so the site is asked again. Any thread.
     *
     * @param origin the site
     * @param permission the permission
     * @return the site, when the cache held a decision for it; else nothing. Before the rows have first loaded the cache
     *     knows only the changes made since, so this is empty then, while the row's deletion is still queued
     */
    public Set<String> revoke(String origin, Permission permission) {
        var key = new Key(Objects.requireNonNull(origin, "origin"), Objects.requireNonNull(permission, "permission"));
        return change(grants -> grants.remove(key) == null ? Set.of() : Set.of(origin), connection -> {
            try (var delete = connection.prepareStatement("DELETE FROM permissions WHERE origin = ? AND type = ?")) {
                delete.setString(1, origin);
                delete.setString(2, permission.token());
                delete.executeUpdate();
            }
        }).affected();
    }

    /**
     * Forgets every decision stored for {@code origin}. Any thread.
     *
     * @param origin the site
     * @return the site, when the cache held any decision for it; else nothing. Before the rows have first loaded the
     *     cache knows only the changes made since, so this is empty then, while the rows' deletion is still queued
     */
    public Set<String> clearSite(String origin) {
        Objects.requireNonNull(origin, "origin");
        return change(grants -> grants.keySet().removeIf(key -> key.origin().equals(origin)) ? Set.of(origin) : Set.of(), connection -> {
            try (var delete = connection.prepareStatement("DELETE FROM permissions WHERE origin = ?")) {
                delete.setString(1, origin);
                delete.executeUpdate();
            }
        }).affected();
    }

    /**
     * What {@link #clearAll()} did: the sites a decision was stored for, and the rows' deletion.
     *
     * @param sites the sites, which the cache held a decision for
     * @param written completes once the rows are deleted; fails, the failure already logged and told, when they are not,
     *     and at once once the store's data store has closed
     */
    public record Cleared(Set<String> sites, CompletableFuture<Void> written) {
        /** Requires both. */
        public Cleared {
            sites = Set.copyOf(sites);
            Objects.requireNonNull(written, "written");
        }
    }

    /**
     * Forgets every decision stored. Any thread.
     *
     * @return the sites a decision was stored for, and the rows' deletion
     */
    public Cleared clearAll() {
        Changed changed = change(grants -> {
            Set<String> origins = grants.keySet().stream().map(Key::origin).collect(Collectors.toUnmodifiableSet());
            grants.clear();
            return origins;
        }, connection -> {
            try (var delete = connection.createStatement()) {
                delete.executeUpdate("DELETE FROM permissions");
            }
        });
        return new Cleared(changed.affected(), changed.written());
    }

    /**
     * The store's data store has closed: the cache empties, writing nothing, so a query that found the store just before
     * finds no decision, and the store takes no change after, nor rows from a load that comes later. Its listeners are
     * not told, as the items that listen are closing too. Any thread.
     */
    public void discard() {
        synchronized (lock) {
            discarded = true;
            early = null;
            grants = new ConcurrentHashMap<>();
        }
    }

    /**
     * Tells {@code listener}, on the UI thread, after each change that changed a decision, and once the rows have loaded.
     *
     * @param listener the listener
     * @return ends the listening
     */
    public Subscription onChanged(Runnable listener) {
        // A wrapper of its own, so that ending one of two subscriptions of the same listener ends that one.
        Runnable entry = Objects.requireNonNull(listener, "listener")::run;
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    /** The sites a change affected, and its row's write. */
    private record Changed(Set<String> affected, CompletableFuture<Void> written) { }

    /**
     * Makes {@code change} in the cache, and again over the rows when they have not loaded yet, and queues its row's
     * {@code write} in the same step, so no other change comes between them and the database takes the changes in the
     * cache's order: queuing only hands the write to the database's thread, which never waits for this lock. Once the
     * store is discarded, nothing changes.
     */
    private Changed change(Change change, Write write) {
        Changed changed;
        synchronized (lock) {
            if (discarded) {
                LOG.log(System.Logger.Level.DEBUG, "A site permission was not changed: its store has closed");
                return new Changed(Set.of(), CompletableFuture.failedFuture(new DatabaseClosedException()));
            }
            Set<String> affected = change.apply(grants);
            if (early != null) early.add(change);
            changed = new Changed(affected, write(write));
        }
        if (!changed.affected().isEmpty()) told();
        return changed;
    }

    /** The rows have been read, or could not be: the cache holds them, with the changes made since over them. */
    private void loaded(CompletableFuture<Void> load, List<Grant> rows, Throwable failure) {
        Throwable unread = failure;
        synchronized (lock) {
            // Rows that arrive once the store is discarded are not the cache's: its data store has closed.
            if (discarded && unread == null) unread = new DatabaseClosedException();
            if (unread == null) {
                var read = new ConcurrentHashMap<Key, Grant>();
                for (Grant grant : rows) read.put(new Key(grant.origin(), grant.permission()), grant);
                for (Change change : early) change.apply(read);
                grants = read;
            }
            early = null;
        }
        if (unread == null) {
            told();
            load.complete(null);
            return;
        }
        Throwable cause = cause(unread);
        if (cause instanceof DatabaseClosedException)
            LOG.log(System.Logger.Level.DEBUG, "Site permissions were not read: their store has closed", cause);
        else LOG.log(System.Logger.Level.WARNING, "Site permissions could not be read", cause);
        load.completeExceptionally(cause);
    }

    /** Queues a change's row for the database's thread; one that fails is logged and told, unless the database has closed. */
    private CompletableFuture<Void> write(Write write) {
        return database.<Void>call(connection -> {
            write.run(connection);
            return null;
        }).whenComplete((written, failure) -> {
            if (failure != null) unwritten(cause(failure));
        });
    }

    private void unwritten(Throwable cause) {
        // The database's own refusal as it closes (F33); an IllegalStateException the driver throws is a failed write.
        if (cause instanceof DatabaseClosedException) {
            LOG.log(System.Logger.Level.DEBUG, "A site permission was not saved: its store has closed", cause);
            return;
        }
        LOG.log(System.Logger.Level.WARNING, UNSAVED, cause);
        ui.execute(() -> unsaved.accept(cause));
    }

    /** Tells every listener, on the UI thread. One that throws is logged; the others still hear of it. */
    private void told() {
        if (listeners.isEmpty()) return;
        ui.execute(() -> {
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "A listener of the site permissions failed", failure);
                }
            }
        });
    }

    private static Throwable cause(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    /** Every row the table holds; a row of a permission or decision this Jasper does not know is left out. */
    private static List<Grant> rows(Connection connection) throws SQLException {
        var rows = new ArrayList<Grant>();
        try (var select = connection.prepareStatement("SELECT origin, type, decision, created, updated FROM permissions");
             var read = select.executeQuery()) {
            while (read.next()) {
                Optional<Permission> permission = Permission.ofToken(read.getString(2));
                Optional<Decision> decision = Decision.ofToken(read.getString(3));
                if (permission.isEmpty() || decision.isEmpty()) continue;
                rows.add(new Grant(read.getString(1), permission.get(), decision.get(), Instant.ofEpochMilli(read.getLong(4)),
                    Instant.ofEpochMilli(read.getLong(5))));
            }
        }
        return rows;
    }
}
