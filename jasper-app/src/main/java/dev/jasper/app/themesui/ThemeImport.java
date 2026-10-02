package dev.jasper.app.themesui;

import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.themeimport.ThemeImporter;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * The Appearance page's Import theme…: asks for a theme file over Settings, plans the conversion on a serial worker,
 * asks how to treat names already installed, writes the themes folder, reloads and reports. One import runs at a time;
 * a confirmed write may finish after closing, but never reopens UI. EDT only, except the worker.
 */
public final class ThemeImport implements AutoCloseable {
    private final PageContext context;
    private final ThemeImporter importer;
    private final ThemeDialogs dialogs;
    private final Supplier<CompletableFuture<Void>> refresh;
    private final ExecutorService worker;
    private final List<Future<?>> jobs = new ArrayList<>();
    private volatile boolean closed;
    private volatile long generation;
    private boolean busy;

    /**
     * An import for one Settings dialog.
     *
     * @param context the Appearance page's context: its file chooser and the dialog to open over
     * @param home the configuration folder, whose {@code themes} folder receives imports
     * @param windows opens the import's dialogs
     * @param refresh reloads the configuration after a write, on the EDT
     * @param worker runs conversions and writes; closed with this import
     */
    public ThemeImport(PageContext context, Path home, AuxiliaryWindows windows, Supplier<CompletableFuture<Void>> refresh,
                       ExecutorService worker) {
        this.context = Objects.requireNonNull(context);
        this.importer = new ThemeImporter(home);
        this.dialogs = new ThemeDialogs(windows, context.surface());
        this.refresh = Objects.requireNonNull(refresh);
        this.worker = Objects.requireNonNull(worker);
    }

    public boolean busy() { return busy; }

    /** Imports a chosen file and shows what was imported, warned about or failed. */
    public void start() { importThemes(result -> dialogs.message("Import themes", report(result))); }

    /** As {@link #start()}, handing the report to {@code report} instead of showing it. */
    void importThemes(Consumer<ThemeImporter.ImportReport> report) {
        if (!begin()) return;
        long ticket = generation;
        Optional<Path> chosen;
        try { chosen = context.chooseFile().apply("Import theme (.theme.json, .jar, .zip, .toml)", Optional.empty()); }
        catch (Exception failure) { error(failure); return; }
        if (closed || ticket != generation) return;
        if (chosen.isEmpty()) { done(); return; }
        String name = chosen.get().getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".theme.json") || name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".toml"))) {
            error(new IllegalArgumentException("Choose a .theme.json, .jar, .zip or .toml file."));
            return;
        }
        work(() -> importer.plan(chosen.get()), plan -> dialogs.view("Import themes", (accept, cancel) -> new ThemeImportView(plan, choices -> {
            accept.run();
            if (closed || ticket != generation) return;
            work(() -> importer.commit(plan, choices), result -> { done(); report.accept(result); }, true);
        }, cancel), this::done), false);
    }

    /** One line per imported theme, warning and failure. */
    static String report(ThemeImporter.ImportReport result) {
        var lines = new ArrayList<String>();
        result.imported().forEach(item -> lines.add("Imported " + item.name() + " — " + item.path()));
        lines.addAll(result.warnings());
        result.failures().forEach(failure -> lines.add(failure.sourceEntry() + ": " + failure.message()));
        return lines.isEmpty() ? "Nothing was imported." : String.join("\n", lines);
    }

    private boolean begin() {
        if (closed || busy || !dialogs.available()) return false;
        generation++;
        busy = true;
        return true;
    }

    private void done() { if (!closed) busy = false; }

    private void error(Throwable failure) {
        done();
        if (!closed) dialogs.message("Theme import failed", Objects.toString(failure.getMessage(), failure.toString()));
    }

    private <T> void work(Callable<T> operation, Consumer<T> success, boolean writes) {
        long ticket = generation;
        try {
            jobs.removeIf(Future::isDone);
            jobs.add(worker.submit(() -> {
                if (closed || ticket != generation) return;
                T value = null;
                Throwable failure = null;
                try { value = operation.call(); } catch (Exception e) { failure = e; }
                final T result = value;
                final Throwable problem = failure;
                SwingUtilities.invokeLater(() -> {
                    // ConfigurationController owns the reload request on the EDT, even when Settings has closed.
                    CompletableFuture<Void> reloaded = CompletableFuture.completedFuture(null);
                    if (writes) try { reloaded = refresh.get(); }
                    catch (Exception e) { reloaded = CompletableFuture.failedFuture(e); }
                    reloaded.whenComplete((ignored, reloadFailure) -> SwingUtilities.invokeLater(() -> {
                        if (closed || ticket != generation) return;
                        if (problem != null) error(problem);
                        else if (reloadFailure != null)
                            error(new IllegalStateException("The import finished, but reload failed: " + reloadFailure.getMessage(), reloadFailure));
                        else success.accept(result);
                    }));
                });
            }));
        } catch (RejectedExecutionException failure) { error(failure); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        generation++;
        for (var job : jobs) job.cancel(false);
        dialogs.close();
        worker.shutdown();
        busy = false;
    }
}
