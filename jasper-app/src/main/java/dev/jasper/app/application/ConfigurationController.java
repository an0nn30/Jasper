package dev.jasper.app.application;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.ConfigEditor;
import dev.jasper.app.workspace.WindowContent;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;

/** EDT-owned join between saved configuration and retained window contents. */
final class ConfigurationController implements AutoCloseable {
    private final ThemeController themes;
    private final ConfigService service;
    private final ThemeCatalog catalog;
    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private final Consumer<Path> editor;
    private ConfigService.State state;
    private boolean closed;
    private Consumer<ConfigSnapshot> applicationListener = snapshot -> {};
    private final List<ConfigDiagnostic> reported = new java.util.ArrayList<>();
    /** The theme catalog's problems, and an unknown theme name, from the last state applied. */
    private List<ConfigDiagnostic> themeProblems = List.of();

    /** The saved state plus theme problems and problems plugins reported about their own tables since the last reload. */
    ConfigService.State shown() {
        requireEdt();
        var merged = new java.util.ArrayList<>(state.diagnostics());
        merged.addAll(themeProblems);
        merged.addAll(reported);
        return new ConfigService.State(state.snapshot(), merged, state.file(), state.present());
    }

    /** A plugin's complaint about one of its settings; shown with the file's diagnostics until the next reload. */
    void report(String key, String message) {
        requireEdt();
        if (closed) return;
        reported.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, state.file(), 0, 0, key, message));
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog) {
        this(themes, service, catalog, new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog, Consumer<Path> editor) {
        requireEdt();
        this.themes = themes; this.service = service; this.catalog = catalog; this.editor = editor;
        state = service.initialState();
        configureThemes(state);
        service.start(this::accept);
    }

    /** The platform the saved shortcuts were parsed for. */
    boolean macOs() { return service.macOs(); }

    ConfigSnapshot snapshot() {
        requireEdt();
        return state.snapshot();
    }

    /** One application-level observer for settings no window owns; receives the current snapshot at once. */
    void onSnapshot(Consumer<ConfigSnapshot> listener) {
        requireEdt();
        applicationListener = java.util.Objects.requireNonNull(listener, "listener");
        if (!closed) listener.accept(state.snapshot());
    }

    void register(WindowContent owner) {
        requireEdt();
        if (closed || !owners.add(owner)) return;
        owner.connectConfiguration(() -> reportFailure(owner, service.openSettings(editor)),
            () -> reportFailure(owner, service.reload()), () -> unregister(owner));
        owner.applyConfiguration(state.snapshot(), service.macOs());
        owner.setConfigurationState(shown());
    }

    private void reportFailure(WindowContent owner, CompletableFuture<?> result) {
        result.whenComplete((ignored, failure) -> {
            if (failure != null) SwingUtilities.invokeLater(() -> {
                if (!closed && owners.contains(owner)) owner.onError.accept(failure.getMessage());
            });
        });
    }

    void unregister(WindowContent owner) {
        requireEdt();
        if (owners.remove(owner)) owner.disconnectConfiguration();
    }

    void accept(ConfigService.State next) {
        requireEdt();
        if (closed) return;
        try { configureThemes(next); }
        catch (ThemeController.InstallationFailure failure) {
            for (WindowContent owner : List.copyOf(owners)) owner.onError.accept(failure.getMessage());
        }
        state = next;
        reported.clear();
        applicationListener.accept(next.snapshot());
        for (WindowContent owner : List.copyOf(owners)) {
            owner.applyConfiguration(next.snapshot(), service.macOs());
            owner.setConfigurationState(shown());
        }
    }

    /**
     * Applies the saved theme, terminal colours and UI font from the catalog's latest scan. An unknown
     * theme name keeps the theme showing (Jasper Dark at start-up) and is reported with the names available.
     */
    private void configureThemes(ConfigService.State next) {
        ThemeCatalog.Snapshot available = catalog.latest();
        var problems = new java.util.ArrayList<ConfigDiagnostic>();
        for (ThemeCatalog.Problem problem : available.problems())
            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, problem.file(), 0, 0, "", problem.message()));
        String name = next.snapshot().theme();
        Theme theme = available.find(name).orElse(null);
        if (theme == null) {
            theme = themes.choice();
            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, "ui.theme.name",
                "Unknown theme \"" + name + "\"; keeping " + theme.name() + ". Available: " + String.join(", ", available.names()) + "."));
        }
        themeProblems = List.copyOf(problems);
        themes.configure(theme, next.snapshot().terminalColors(), next.snapshot().uiFont());
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        applicationListener = snapshot -> {};
        for (WindowContent owner : List.copyOf(owners)) unregister(owner);
        service.close();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Configuration UI requires the EDT");
    }
}
