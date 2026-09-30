package dev.jasper.app.themestore;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import java.awt.Component;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * The IntelliJ Themes page's controller: the Marketplace client, the installer and a worker for their blocking work.
 * Call it on the EDT; every future it returns completes on the EDT. The application owns one and closes it at shutdown.
 */
public final class ThemeStore implements AutoCloseable {
    /**
     * What the store needs from the application.
     *
     * @param rescan reads the themes folder again (the configuration reload does) and completes when the catalog is current
     * @param browse opens a web page
     * @param reveal shows a folder in the file manager
     * @param confirm asks a yes/no question over a component
     */
    public record Hooks(Supplier<CompletableFuture<?>> rescan, Consumer<URI> browse, Consumer<Path> reveal,
                        BiPredicate<Component, String> confirm) {
        public Hooks {
            Objects.requireNonNull(rescan); Objects.requireNonNull(browse); Objects.requireNonNull(reveal); Objects.requireNonNull(confirm);
        }
    }

    private final ThemeCatalog catalog;
    private final MarketplaceClient client;
    private final ThemeInstaller installer;
    private final Executor worker;
    private final Executor edt;
    private final Hooks hooks;
    private final Runnable stopWorker;
    private final Map<String, CompletableFuture<byte[]>> icons = new ConcurrentHashMap<>();

    /** The application's store: the real Marketplace, a virtual-thread worker, results on the EDT. */
    public static ThemeStore create(ThemeCatalog catalog, Hooks hooks) {
        var worker = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("jasper-themes-", 0).factory());
        return new ThemeStore(catalog, new MarketplaceClient(), worker, SwingUtilities::invokeLater, hooks, () -> worker.shutdownNow());
    }

    ThemeStore(ThemeCatalog catalog, MarketplaceClient client, Executor worker, Executor edt, Hooks hooks, Runnable stopWorker) {
        this.catalog = Objects.requireNonNull(catalog);
        this.client = Objects.requireNonNull(client);
        this.worker = Objects.requireNonNull(worker);
        this.edt = Objects.requireNonNull(edt);
        this.hooks = Objects.requireNonNull(hooks);
        this.stopWorker = Objects.requireNonNull(stopWorker);
        this.installer = new ThemeInstaller(catalog.directory()
            .orElseThrow(() -> new IllegalArgumentException("The built-in theme catalog has no themes folder")));
    }

    /** A standard Yes/No question; the production {@link Hooks#confirm}. */
    public static boolean ask(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "IntelliJ Themes", JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** The IntelliJ Themes page for one Settings dialog, around {@code header}, the schema page holding its settings. */
    public SettingsPage page(PageContext context, SettingsPage header) { return new IntelliJThemesPage(this, context, header); }

    CompletableFuture<MarketplaceClient.Page> search(String query, int offset) { return onEdt(supply(() -> client.search(query, offset))); }

    CompletableFuture<MarketplaceClient.Details> details(int id) { return onEdt(supply(() -> client.details(id))); }

    CompletableFuture<Optional<MarketplaceClient.Release>> latest(int id) { return onEdt(supply(() -> client.latest(id))); }

    /** An icon's bytes, fetched once per session. */
    CompletableFuture<byte[]> icon(String path) {
        var fetch = icons.computeIfAbsent(path, key -> supply(() -> client.icon(key)));
        fetch.whenComplete((bytes, failure) -> { if (failure != null) icons.remove(path, fetch); });
        return onEdt(fetch);
    }

    /** The archives in the themes folder as the catalog last read them. */
    CompletableFuture<List<InstalledTheme>> installed() {
        return onEdt(supply(() -> InstalledTheme.of(catalog.latest(), installer.folder(), installer.sources())));
    }

    /** The newer release of each Marketplace install that has one; a failed check leaves that theme out. */
    CompletableFuture<Map<Path, MarketplaceClient.Release>> updates(List<InstalledTheme> themes) {
        var checks = new ArrayList<CompletableFuture<Optional<Map.Entry<Path, MarketplaceClient.Release>>>>();
        for (InstalledTheme theme : themes) {
            if (theme.source().isEmpty()) continue;
            Provenance.Entry source = theme.source().get();
            checks.add(supply(() -> client.latest(source.marketplaceId())).handle((release, failure) ->
                failure != null || release == null ? Optional.<Map.Entry<Path, MarketplaceClient.Release>>empty()
                    : release.filter(found -> Versions.newer(found.version(), source.version()))
                        .map(found -> Map.entry(theme.file(), found))));
        }
        return onEdt(CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            var found = new LinkedHashMap<Path, MarketplaceClient.Release>();
            for (var check : checks) check.join().ifPresent(entry -> found.put(entry.getKey(), entry.getValue()));
            return Map.copyOf(found);
        }));
    }

    /** Downloads the plugin's latest release, installs it (replacing an older one) and rescans. */
    CompletableFuture<ThemeInstaller.Installed> install(int id, String xmlId, String name) {
        return onEdt(onEdt(supply(() -> {
            MarketplaceClient.Release release = client.latest(id)
                .orElseThrow(() -> new ThemeInstaller.InstallException(name + " has no release to download."));
            Path part = installer.downloadTarget(release);
            try { client.download(release, part); }
            catch (MarketplaceClient.MarketplaceException failure) { Files.deleteIfExists(part); throw failure; }
            String extension = release.file().toLowerCase(Locale.ROOT).endsWith(".jar") ? ".jar" : ".zip";
            return installer.installDownload(part, extension, new Provenance.Entry(id, xmlId, name, release.version()));
        })).thenCompose(this::rescanned));
    }

    CompletableFuture<ThemeInstaller.Installed> installFromDisk(Path file, boolean replace) {
        return onEdt(onEdt(supply(() -> installer.installFromDisk(file, replace))).thenCompose(this::rescanned));
    }

    CompletableFuture<Void> uninstall(Path file) {
        return onEdt(onEdt(supply(() -> { installer.uninstall(file); return (Void) null; })).thenCompose(this::rescanned));
    }

    /** Deletes leftover downloads in the background. */
    void clearDownloads() {
        try { worker.execute(installer::clearDownloads); } catch (RejectedExecutionException closed) { }
    }

    void revealFolder() { hooks.reveal().accept(installer.folder()); }

    boolean confirm(Component parent, String question) { return hooks.confirm().test(parent, question); }

    /** Opens {@code href}, resolved against the Marketplace, when it is an http or https link. */
    void browse(String href) {
        URI uri;
        try { uri = MarketplaceClient.DEFAULT_BASE.resolve(href.strip()); }
        catch (IllegalArgumentException malformed) { return; }
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) hooks.browse().accept(uri);
    }

    /** The failure behind a future's wrapping. */
    static Throwable cause(Throwable failure) {
        Throwable at = failure;
        while ((at instanceof CompletionException || at instanceof ExecutionException) && at.getCause() != null) at = at.getCause();
        return at;
    }

    /** A message for the user about {@code failure}. */
    static String message(Throwable failure) {
        Throwable cause = cause(failure);
        if (cause instanceof MarketplaceClient.MarketplaceException || cause instanceof ThemeInstaller.InstallException) return cause.getMessage();
        return "Something went wrong: " + cause;
    }

    @Override public void close() {
        try { stopWorker.run(); }
        finally { client.close(); }
    }

    /** {@code value} once the catalog has been read again; a failed rescan does not undo the change. */
    private <T> CompletableFuture<T> rescanned(T value) {
        try { return hooks.rescan().get().handle((ignored, failure) -> value); }
        catch (RuntimeException failure) { return CompletableFuture.completedFuture(value); }
    }

    private <T> CompletableFuture<T> supply(Callable<T> work) {
        var result = new CompletableFuture<T>();
        try {
            worker.execute(() -> {
                try { result.complete(work.call()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException closed) {
            result.completeExceptionally(closed);
        }
        return result;
    }

    private <T> CompletableFuture<T> onEdt(CompletableFuture<T> work) {
        var result = new CompletableFuture<T>();
        work.whenComplete((value, failure) -> edt.execute(() -> {
            if (failure == null) result.complete(value);
            else result.completeExceptionally(cause(failure));
        }));
        return result;
    }
}
