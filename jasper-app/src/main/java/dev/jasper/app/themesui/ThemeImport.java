package dev.jasper.app.themesui;

import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.themeimport.ThemeImporter;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
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
        this.dialogs = new ThemeDialogs(windows, context.surface(), context.kit());
        this.refresh = Objects.requireNonNull(refresh);
        this.worker = Objects.requireNonNull(worker);
    }

    public boolean busy() { return busy; }

    /** Imports a chosen file and shows what was imported, warned about or failed. */
    public void start() { importThemes(result -> dialogs.sheet(reportTitle(result), report(result))); }

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
        work(() -> importer.plan(chosen.get()), plan -> dialogs.view("Import Themes", (accept, cancel) -> new ThemeImportView(dialogs.kit(), plan, choices -> {
            accept.run();
            if (closed || ticket != generation) return;
            work(() -> importer.commit(plan, choices), result -> { done(); report.accept(result); }, true);
        }, cancel).view, this::done), false);
    }

    /** The report sheet's title: what happened, in Title Case. */
    static String reportTitle(ThemeImporter.ImportReport result) {
        int imported = result.imported().size();
        return imported == 0 ? "Nothing Imported" : imported == 1 ? "Theme Imported" : "Themes Imported";
    }

    /**
     * The report as sheet sections: an Imported card of theme name and written file rows, then Warnings and Not Imported
     * cards of literal lines; a single sentence when nothing happened. All text is literal.
     */
    static List<Nodes.Node> report(ThemeImporter.ImportReport result) {
        var sections = new ArrayList<Nodes.Node>();
        if (!result.imported().isEmpty()) {
            var rows = new ArrayList<Nodes.Node>();
            for (int i = 0; i < result.imported().size(); i++) {
                var item = result.imported().get(i);
                rows.add(new Nodes.FormRow("imported:" + i, ControlState.READY, item.name(), "",
                    new Nodes.Text("imported:" + i + ".path", ControlState.READY, item.path().toString(), Nodes.TextRole.MONO_SMALL), Optional.empty()));
            }
            sections.add(section("imported", "Imported", rows));
        }
        if (!result.warnings().isEmpty()) sections.add(section("warnings", "Warnings", List.of(
            new Nodes.Text("warnings.text", ControlState.READY, String.join("\n", result.warnings()), Nodes.TextRole.DESCRIPTION))));
        if (!result.failures().isEmpty()) {
            var lines = new ArrayList<String>();
            result.failures().forEach(failure -> lines.add(failure.sourceEntry() + ": " + failure.message()));
            sections.add(section("failures", "Not Imported", List.of(
                new Nodes.Text("failures.text", ControlState.READY, String.join("\n", lines), Nodes.TextRole.DESCRIPTION))));
        }
        if (sections.isEmpty()) sections.add(new Nodes.Text("message-text", ControlState.READY, "Nothing was imported.", Nodes.TextRole.DESCRIPTION));
        return sections;
    }

    private static Nodes.Section section(String id, String title, List<Nodes.Node> children) {
        return new Nodes.Section(id, ControlState.READY, title, new Nodes.Card(id + ".card", ControlState.READY, Nodes.CardKind.FORM, children));
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
        if (!closed) dialogs.message("Theme Import Failed", Objects.toString(failure.getMessage(), failure.toString()));
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
