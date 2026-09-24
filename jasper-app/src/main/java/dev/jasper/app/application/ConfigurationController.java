package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeController;
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
    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private final Consumer<Path> editor;
    private ConfigService.State state;
    private boolean closed;
    private Consumer<ConfigSnapshot> applicationListener = snapshot -> {};
    private final List<dev.jasper.app.config.ConfigDiagnostic> reported = new java.util.ArrayList<>();

    /** The saved state plus problems plugins reported about their own tables since the last reload. */
    ConfigService.State shown() {
        requireEdt();
        var merged = new java.util.ArrayList<>(state.diagnostics());
        merged.addAll(reported);
        if (state.snapshot().style() != themes.requestedStyle() && state.snapshot().style() != themes.style())
            merged.add(new dev.jasper.app.config.ConfigDiagnostic(
                dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING,
                state.file(), 0, 0, "ui.theme.style", "Restart Jasper to apply ui.theme.style."));
        if (state.snapshot().style() == themes.requestedStyle())
            themes.fallbackReason().ifPresent(reason -> merged.add(new dev.jasper.app.config.ConfigDiagnostic(
                dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING, state.file(), 0, 0, "ui.theme.style", reason)));
        return new ConfigService.State(state.snapshot(), merged, state.file(), state.present());
    }

    /** A plugin's complaint about one of its settings; shown with the file's diagnostics until the next reload. */
    void report(String key, String message) {
        requireEdt();
        if (closed) return;
        reported.add(new dev.jasper.app.config.ConfigDiagnostic(dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING,
            state.file(), 0, 0, key, message));
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    ConfigurationController(ThemeController themes, ConfigService service) {
        this(themes, service, new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor) {
        requireEdt();
        this.themes = themes; this.service = service; this.editor = editor;
        state = service.initialState();
        themes.configure(state.snapshot().variant(), state.snapshot().uiFont());
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
        try { themes.configure(next.snapshot().variant(), next.snapshot().uiFont()); }
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
