package dev.jasper.browser.item;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.Stores;
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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Settings › Tools › Browser's Clear Cookies and Clear Website Data, for the persistent store (spec section 12); the
 * /dev/null groups' stores are untouched, as each goes with its group. Clear Website Data first gathers the sites to
 * clear live, through {@link Origins#of}: the newest 1,000 sites of the history and the sites of the persistent store's
 * open items (plan 6's F20), web origins only, as Chromium adds its cookies' own and the next start's wipe covers the
 * rest, as it does a history that cannot be read. Then Chromium clears ({@link Chromium#clearWebsiteData}); then the
 * persistent store's history, bookmarks and site permissions are emptied; then the database is compacted, and its
 * write-ahead log checkpointed and truncated, so no page of a deleted row stays in its file or its log (F17). Each step
 * follows the one before, and a failure stops the clearing and is told once, with its cause: "Website data could not be
 * cleared", or for Clear Cookies "Cookies could not be cleared". Names no Chromium type. UI thread, where the open items
 * are read; the clearing goes on other threads.
 */
public final class WebsiteData {
    /** What the notification of a Clear Website Data that failed says. */
    public static final String NOT_CLEARED = "Website data could not be cleared";
    /** What the notification of a Clear Cookies that failed says. */
    public static final String COOKIES_NOT_CLEARED = "Cookies could not be cleared";
    /** The most history sites cleared live (F20). */
    static final int LIVE_HISTORY = 1_000;
    private static final System.Logger LOG = System.getLogger(WebsiteData.class.getName());
    private final Supplier<CompletableFuture<Void>> clearCookies;
    private final Function<Set<String>, CompletableFuture<Void>> clearChromium;
    private final DataStore persistent;
    private final Supplier<List<URI>> open;
    private final Executor ui;
    private final BiConsumer<String, Throwable> failed;

    /**
     * The clearing of {@code persistent} through {@code chromium}.
     *
     * @param chromium the plugin's Chromium
     * @param persistent the persistent data store
     * @param open the addresses of the persistent store's open items, read on the UI thread
     * @param ui runs a task on the UI thread
     * @param failed told, on the UI thread, what failed, its notification's text, and why
     */
    public WebsiteData(Chromium chromium, DataStore persistent, Supplier<List<URI>> open, Executor ui, BiConsumer<String, Throwable> failed) {
        this(Objects.requireNonNull(chromium, "chromium")::clearCookies, chromium::clearWebsiteData, persistent, open, ui, failed);
    }

    /** As the public constructor, with Chromium's two clearings given: a test's stand-ins. */
    WebsiteData(Supplier<CompletableFuture<Void>> clearCookies, Function<Set<String>, CompletableFuture<Void>> clearChromium,
                DataStore persistent, Supplier<List<URI>> open, Executor ui, BiConsumer<String, Throwable> failed) {
        this.clearCookies = Objects.requireNonNull(clearCookies, "clearCookies");
        this.clearChromium = Objects.requireNonNull(clearChromium, "clearChromium");
        this.persistent = Objects.requireNonNull(persistent, "persistent");
        this.open = Objects.requireNonNull(open, "open");
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
     * @return completes once everything is cleared; fails, the failure told, at the first step that fails
     */
    public CompletableFuture<Void> clearAll() {
        Set<String> sites = new LinkedHashSet<>();
        for (URI address : open.get()) web(address.toString()).ifPresent(sites::add);
        Stores stores = persistent.stores();
        // A history that cannot be read keeps Chromium's clearing from nothing: its sites wait for the next start's wipe.
        CompletableFuture<Void> cleared = stores.history().origins(LIVE_HISTORY)
            .exceptionally(unread -> {
                LOG.log(System.Logger.Level.WARNING, "The history's sites could not be read; the next start's wipe clears them", unread);
                return List.of();
            })
            .thenCompose(history -> {
                Set<String> live = new LinkedHashSet<>();
                for (String origin : history) web(origin).ifPresent(live::add);
                live.addAll(sites);
                return clearChromium.apply(Set.copyOf(live));
            })
            .thenCompose(chromium -> erase(stores))
            .thenCompose(erased -> compact(stores.database()));
        return told(cleared, NOT_CLEARED);
    }

    /**
     * Empties the persistent store's history, bookmarks and site permissions; the database takes the three in turn, so
     * the compaction asked for after them follows them.
     */
    private CompletableFuture<Void> erase(Stores stores) {
        CompletableFuture<Void> history = stores.history().clear(), bookmarks = stores.bookmarks().clear();
        persistent.permissions().clearAll();
        return CompletableFuture.allOf(history, bookmarks);
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

    /** {@code clearing}, whose failure is logged and told once, as {@code title}, with its cause. */
    private CompletableFuture<Void> told(CompletableFuture<Void> clearing, String title) {
        var result = new CompletableFuture<Void>();
        clearing.whenComplete((done, failure) -> {
            if (failure == null) {
                result.complete(null);
                return;
            }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            LOG.log(System.Logger.Level.WARNING, title, cause);
            ui.execute(() -> failed.accept(title, cause));
            result.completeExceptionally(cause);
        });
        return result;
    }
}
