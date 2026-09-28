package dev.jasper.app.application;

import dev.jasper.app.config.KeyBindings;
import java.util.Map;
import java.util.Optional;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.ConfigEditor;
import dev.jasper.app.workspace.WindowContent;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
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
    private Predicate<String> pluginSettingsExist = id -> false;
    private final List<Consumer<ConfigService.State>> stateListeners = new java.util.ArrayList<>();
    private java.util.function.BiConsumer<WindowContent, String> settingsOpener;
    private final java.util.Map<Path, java.util.LinkedHashSet<ConfigDiagnostic>> pluginReports = new java.util.LinkedHashMap<>();

    /** The saved state plus theme problems, application problems since the last reload, and problems plugins reported about their own settings files. */
    ConfigService.State shown() {
        requireEdt();
        var merged = new java.util.ArrayList<>(state.diagnostics());
        merged.addAll(themeProblems);
        merged.addAll(reported);
        pluginReports.values().forEach(merged::addAll);
        var removable = state.deadTables().stream()
            .filter(table -> !table.getFirst().equals("plugins") || pluginSettingsExist.test(table.get(1))).toList();
        return new ConfigService.State(state.snapshot(), merged, state.file(), state.present(), state.legacyKeys(), removable,
            state.values(), state.rejected());
    }

    /** A plugin's problem with its own settings file; shown until that file is reloaded. */
    void report(ConfigDiagnostic diagnostic) {
        requireEdt();
        if (closed) return;
        pluginReports.computeIfAbsent(diagnostic.file(), file -> new java.util.LinkedHashSet<>()).add(diagnostic);
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    /** Forgets every plugin problem in {@code file}. */
    void clearReports(Path file) {
        requireEdt();
        if (closed || pluginReports.remove(file) == null) return;
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    /** An application problem with a key of config.toml; shown with the file's diagnostics until the next reload. */
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
        try { configureThemes(state); }
        catch (ThemeController.InstallationFailure ignored) {
            // Already recorded as a theme problem below; a theme that cannot be installed never stops start-up.
        }
        settingsOpener = (owner, page) -> reportFailure(owner, service.openSettings(editor));
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
        owner.connectConfiguration(() -> settingsOpener.accept(owner, null),
            () -> reportFailure(owner, service.reload()), () -> migrateFor(owner), () -> unregister(owner));
        owner.connectSettings(this::save, page -> settingsOpener.accept(owner, page));
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

    CompletableFuture<Void> saveKeybindings(Map<String, Optional<String>> changes,
            List<KeyBindings.Extension> extensions) {
        requireEdt();
        return service.saveKeybindings(state.snapshot().keybindings(), changes, extensions).thenApply(ignored -> null);
    }

    /** Which plugins already have their own settings file, so Migrate Config may drop their old tables. */
    void pluginSettingsExist(Predicate<String> test) {
        requireEdt();
        pluginSettingsExist = Objects.requireNonNull(test, "test");
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    /** Whether Migrate Config has anything to do for the file as last loaded. */
    boolean migrationAvailable() {
        requireEdt();
        return shown().migratable();
    }

    /** Runs Migrate Config; completes with the message to show. */
    CompletableFuture<String> migrate() {
        requireEdt();
        return service.migrate(pluginSettingsExist).thenApply(result -> result.changes() == 0
            ? "Nothing to migrate: " + result.state().file().getFileName() + " already uses the current layout."
            : "Migrated " + result.changes() + " old setting" + (result.changes() == 1 ? "" : "s") + " to the new layout. Backup: "
                + result.backup().map(path -> path.getFileName().toString()).orElse("none") + ".");
    }

    private void migrateFor(WindowContent owner) {
        migrate().whenComplete((message, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed || !owners.contains(owner)) return;
            if (failure == null) owner.onInfo.accept(message);
            else owner.onError.accept((failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure).getMessage());
        }));
    }

    /**
     * What Cmd+, each window's Settings command and View \u203a Window Settings\u2026 open, with the page to show (null for the
     * last one visited); by default config.toml in an editor.
     */
    void settingsOpener(java.util.function.BiConsumer<WindowContent, String> opener) {
        requireEdt();
        settingsOpener = java.util.Objects.requireNonNull(opener, "opener");
    }

    /** Opens config.toml in the user's editor, creating it from the template first when it is missing. */
    CompletableFuture<Path> openConfigFile() {
        requireEdt();
        return service.openSettings(editor);
    }

    /** Reads config.toml again and publishes it, even when it did not change. */
    CompletableFuture<ConfigService.State> reload() {
        requireEdt();
        return service.reload();
    }

    /** The state windows show: the saved state plus theme, application and plugin problems. */
    ConfigService.State state() {
        requireEdt();
        return shown();
    }

    /** Saves settings; the new state also arrives through the normal reload. */
    CompletableFuture<ConfigService.State> save(SettingsEdit edit) {
        requireEdt();
        return service.save(edit);
    }

    /** Calls {@code listener} with every state applied from now on. */
    Subscription onState(Consumer<ConfigService.State> listener) {
        requireEdt();
        stateListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> stateListeners.remove(listener));
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
        var visible = shown();
        for (Consumer<ConfigService.State> listener : List.copyOf(stateListeners)) listener.accept(visible);
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
        try {
            themes.configure(theme, next.snapshot().terminalColors(), next.snapshot().uiFont());
            themeProblems = List.copyOf(problems);
        } catch (ThemeController.InstallationFailure failure) {
            Throwable cause = failure.getCause();
            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, "ui.theme.name",
                "Could not apply theme \"" + theme.name() + "\": " + (cause != null ? cause.getMessage() : failure.getMessage())
                    + "; keeping " + themes.choice().name() + "."));
            themeProblems = List.copyOf(problems);
            throw failure;
        }
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        applicationListener = snapshot -> {};
        stateListeners.clear();
        for (WindowContent owner : List.copyOf(owners)) unregister(owner);
        service.close();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Configuration UI requires the EDT");
    }
}
