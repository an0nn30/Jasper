package dev.jasper.browser.item;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.permissions.PermissionStore;
import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.Stores;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settings › Tools › Browser's Clear Cookies and Clear Website Data, for the persistent store (spec section 12); the
 * /dev/null groups' stores are untouched, as each goes with its group. Clear Website Data first gathers the sites to
 * clear live, through {@link Origins#of}: the newest 1,000 sites of the history and the sites of the persistent store's
 * open items (plan 6's F20), web origins only, as Chromium adds its cookies' own and the next start's wipe covers the
 * rest, as it does a history that cannot be read. Then, so a stop or quit while Chromium clears leaves no row (ruling
 * V27), the persistent store's history, bookmarks and site permissions are emptied, the pages of a site whose permission
 * went load again, as the permissions page has them, and the database is compacted, an erase that failed included, and
 * its write-ahead log checkpointed and truncated, so no page of a deleted row stays in its file or its log (F17). Then
 * Chromium clears ({@link Chromium#clearWebsiteData}), whether the database's part failed or not (V26). A database that
 * runs in memory, as its file could not be used, cannot erase its file (ruling V25), and one whose erase did not
 * complete has not: Chromium marks the file for deletion at the next start, before the database opens, and the clearing
 * fails. A failure is told once, with
 * its cause: "Website data could not be cleared", or for Clear Cookies "Cookies could not be cleared". Names no Chromium
 * type. UI thread, where the open items are read; the clearing goes on other threads.
 */
public final class WebsiteData {
    /** What the notification of a Clear Website Data that failed says. */
    public static final String NOT_CLEARED = "Website data could not be cleared";
    /** What the notification of a Clear Cookies that failed says. */
    public static final String COOKIES_NOT_CLEARED = "Cookies could not be cleared";
    /** Why a Clear Website Data whose database runs in memory fails (ruling V25). */
    public static final String IN_MEMORY = "The browser's database file could not be used, so it was not cleared; Jasper deletes it the "
        + "next time it starts";
    /** The most history sites cleared live (F20). */
    static final int LIVE_HISTORY = 1_000;
    private static final System.Logger LOG = System.getLogger(WebsiteData.class.getName());
    private final Supplier<CompletableFuture<Void>> clearCookies;
    private final BiFunction<Set<String>, Boolean, CompletableFuture<Void>> clearChromium;
    private final DataStore persistent;
    private final Supplier<List<URI>> open;
    private final Consumer<Set<String>> reload;
    private final Executor ui;
    private final BiConsumer<String, Throwable> failed;

    /**
     * The clearing of {@code persistent} through {@code chromium}.
     *
     * @param chromium the plugin's Chromium
     * @param persistent the persistent data store
     * @param open the addresses of the persistent store's open items, read on the UI thread
     * @param reload has the persistent store's items that show one of the sites whose permissions went load again; told
     *     on the UI thread
     * @param ui runs a task on the UI thread
     * @param failed told, on the UI thread, what failed, its notification's text, and why
     */
    public WebsiteData(Chromium chromium, DataStore persistent, Supplier<List<URI>> open, Consumer<Set<String>> reload, Executor ui,
                       BiConsumer<String, Throwable> failed) {
        this(Objects.requireNonNull(chromium, "chromium")::clearCookies, chromium::clearWebsiteData, persistent, open, reload, ui, failed);
    }

    /** As the public constructor, with Chromium's two clearings given: a test's stand-ins. */
    WebsiteData(Supplier<CompletableFuture<Void>> clearCookies, BiFunction<Set<String>, Boolean, CompletableFuture<Void>> clearChromium,
                DataStore persistent, Supplier<List<URI>> open, Consumer<Set<String>> reload, Executor ui, BiConsumer<String, Throwable> failed) {
        this.clearCookies = Objects.requireNonNull(clearCookies, "clearCookies");
        this.clearChromium = Objects.requireNonNull(clearChromium, "clearChromium");
        this.persistent = Objects.requireNonNull(persistent, "persistent");
        this.open = Objects.requireNonNull(open, "open");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.failed = Objects.requireNonNull(failed, "failed");
    }

    /**
     * Clear Cookies: every cookie of the persistent store goes ({@link Chromium#clearCookies}).
     *
     * @return completes once they are gone, or marked for Chromium's next start; fails, the failure told, when not
     */
    public CompletableFuture<Void> clearCookies() {
        return told(start(clearCookies), COOKIES_NOT_CLEARED);
    }

    /**
     * Clear Website Data, as the class says.
     *
     * @return completes once everything is cleared; fails, the failure told, when the erase, the compaction or Chromium's
     *     part fails, or the database runs in memory
     */
    public CompletableFuture<Void> clearAll() {
        Set<String> sites = new LinkedHashSet<>();
        for (URI address : open.get()) web(address.toString()).ifPresent(sites::add);
        Stores stores = persistent.stores();
        BrowserDatabase database = stores.database();
        // Whether the database uses the file it was asked for, known once a call has answered, failed or not (V25).
        CompletableFuture<Boolean> fileUnused = database.call(connection -> null).handle((answered, failure) -> database.fileUnused());
        // A history that cannot be read keeps Chromium's clearing from nothing: its sites wait for the next start's wipe.
        CompletableFuture<List<String>> history = stores.history().origins(LIVE_HISTORY).exceptionally(unread -> {
            LOG.log(System.Logger.Level.WARNING, "The history's sites could not be read; the next start's wipe clears them", unread);
            return List.of();
        });
        var cleared = new CompletableFuture<Void>();
        fileUnused.thenAcceptBoth(history, (unused, visited) -> {
            Set<String> live = new LinkedHashSet<>();
            for (String origin : visited) web(origin).ifPresent(live::add);
            live.addAll(sites);
            // V27: the database first, so a stop or quit while Chromium clears leaves no row; the compaction follows a failed
            // erase too, as what was deleted must not stay in the file. Chromium's part follows either way (V26), and a
            // database whose erase did not complete, or that runs in memory, is marked for deletion at the next start.
            erase(stores).handle((erased, eraseFailure) -> eraseFailure)
                .thenCompose(eraseFailure -> compact(database).handle((compacted, compactFailure) -> first(cause(eraseFailure), cause(compactFailure))))
                .thenCompose(databaseFailure -> start(() -> clearChromium.apply(Set.copyOf(live), unused || databaseFailure != null))
                    .handle((chromium, chromiumFailure) -> first(databaseFailure, cause(chromiumFailure), unused ? new IOException(IN_MEMORY) : null)))
                .thenAccept(failure -> {
                    if (failure == null) cleared.complete(null);
                    else cleared.completeExceptionally(failure);
                });
        }).exceptionally(broken -> {
            cleared.completeExceptionally(cause(broken));
            return null;
        });
        return told(cleared, NOT_CLEARED);
    }

    /**
     * Empties the persistent store's history, bookmarks and site permissions; the database takes the three in turn, so
     * the compaction asked for after them follows them. Once done, the pages of the sites whose permissions went load
     * again.
     */
    private CompletableFuture<Void> erase(Stores stores) {
        CompletableFuture<Void> history = stores.history().clear(), bookmarks = stores.bookmarks().clear();
        PermissionStore.Cleared permissions = persistent.permissions().clearAll();
        return CompletableFuture.allOf(history, bookmarks, permissions.written()).whenComplete((erased, failure) -> {
            if (!permissions.sites().isEmpty()) ui.execute(() -> reload.accept(permissions.sites()));
        });
    }

    /**
     * Compacts the database and checkpoints its write-ahead log, truncating it, so the pages of the deleted rows are in
     * neither file (F17).
     */
    private static CompletableFuture<Void> compact(BrowserDatabase database) {
        return database.call(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("VACUUM");
                try (var checkpoint = statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
                    if (checkpoint.next() && checkpoint.getInt(1) != 0)
                        throw new SQLException("The browser's database log could not be emptied: the database is busy");
                }
            }
            return null;
        });
    }

    /** {@code address}'s origin when it is a web one, http or https; any other is cleared by the next start's wipe. */
    private static Optional<String> web(String address) {
        return Origins.of(address).filter(origin -> origin.startsWith("https://") || origin.startsWith("http://"));
    }

    /** What {@code step} gives, or a failed future when it throws. */
    private static CompletableFuture<Void> start(Supplier<CompletableFuture<Void>> step) {
        try {
            return Objects.requireNonNull(step.get(), "the clearing's future");
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** The first of {@code failures} that is one, the later ones suppressed in it; null for none. */
    private static Throwable first(Throwable... failures) {
        Throwable first = null;
        for (Throwable failure : failures) {
            if (failure == null) continue;
            if (first == null) first = failure;
            else if (failure != first) first.addSuppressed(failure);
        }
        return first;
    }

    private static Throwable cause(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    /** {@code clearing}, whose failure is logged and told once, as {@code title}, with its cause. */
    private CompletableFuture<Void> told(CompletableFuture<Void> clearing, String title) {
        var result = new CompletableFuture<Void>();
        clearing.whenComplete((done, failure) -> {
            if (failure == null) {
                result.complete(null);
                return;
            }
            Throwable cause = cause(failure);
            LOG.log(System.Logger.Level.WARNING, title, cause);
            ui.execute(() -> failed.accept(title, cause));
            result.completeExceptionally(cause);
        });
        return result;
    }
}
