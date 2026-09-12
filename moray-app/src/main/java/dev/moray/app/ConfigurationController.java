package dev.moray.app;

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
    private final SystemAppearance source;
    private String appearanceWarning = "";
    private final ConfigService service;
    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private final Consumer<Path> editor;
    private ConfigService.State state;
    private boolean closed;

    ConfigurationController(ThemeController themes, ConfigService service) {
        this(themes, service, new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor) {
        this(themes, service, SystemAppearance.fixed(BuiltinTheme.DARK), editor);
    }

    ConfigurationController(ThemeController themes, ConfigService service, SystemAppearance source) {
        this(themes, service, source, new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, SystemAppearance source, Consumer<Path> editor) {
        requireEdt();
        this.themes = themes; this.service = service; this.editor = editor; this.source = source;
        state = service.initialState();
        themes.configure(state.snapshot().colors(), state.palette());
        service.start(this::accept);
        source.start(this::appearanceChanged);
    }

    ConfigSnapshot snapshot() {
        requireEdt();
        return state.snapshot();
    }

    void register(WindowContent owner) {
        requireEdt();
        if (closed || !owners.add(owner)) return;
        owner.connectConfiguration(() -> reportFailure(owner, service.openSettings(editor)),
            () -> reportFailure(owner, service.reload()), () -> unregister(owner));
        owner.applyConfiguration(state.snapshot(), service.macOs());
        owner.setConfigurationState(displayed());
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
        try { themes.configure(next.snapshot().colors(), next.palette()); }
        catch (ThemeController.InstallationFailure failure) { reportThemeFailure(failure); }
        state = next;
        for (WindowContent owner : List.copyOf(owners)) {
            owner.applyConfiguration(next.snapshot(), service.macOs());
            owner.setConfigurationState(displayed());
        }
    }

    private void appearanceChanged(SystemAppearance.Reading reading) {
        requireEdt();
        if (closed) return;
        appearanceWarning = reading.warning();
        try { themes.systemChanged(reading.theme()); }
        catch (ThemeController.InstallationFailure failure) { reportThemeFailure(failure); }
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(displayed());
    }

    private void reportThemeFailure(ThemeController.InstallationFailure failure) {
        for (WindowContent owner : List.copyOf(owners)) owner.onError.accept(failure.getMessage());
    }

    private ConfigService.State displayed() {
        var diagnostics = new java.util.ArrayList<>(state.diagnostics());
        if (!appearanceWarning.isEmpty()) diagnostics.add(new ConfigDiagnostic(
            ConfigDiagnostic.Severity.WARNING, state.file(), 0, 0, "colors.appearance", appearanceWarning));
        return new ConfigService.State(state.snapshot(), diagnostics, state.file(), state.present(), state.palette());
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        source.close();
        for (WindowContent owner : List.copyOf(owners)) unregister(owner);
        service.close();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Configuration UI requires the EDT");
    }
}
