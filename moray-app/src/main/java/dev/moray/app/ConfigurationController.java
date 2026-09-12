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
    private final ConfigService service;
    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private final Consumer<Path> editor;
    private ConfigService.State state;
    private boolean closed;

    ConfigurationController(ThemeController themes, ConfigService service) {
        this(themes, service, new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor) {
        requireEdt();
        this.themes = themes; this.service = service; this.editor = editor;
        state = service.initialState();
        themes.select(state.snapshot().theme());
        service.start(this::accept);
    }

    void register(WindowContent owner) {
        requireEdt();
        if (closed || !owners.add(owner)) return;
        owner.connectConfiguration(() -> reportFailure(owner, service.openSettings(editor)),
            () -> reportFailure(owner, service.reload()), () -> unregister(owner));
        owner.applyConfiguration(state.snapshot(), service.macOs());
        owner.setConfigurationState(state);
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
        if (state.snapshot().theme() != next.snapshot().theme()) {
            try { themes.select(next.snapshot().theme()); }
            catch (IllegalStateException failure) {
                for (WindowContent owner : List.copyOf(owners)) owner.onError.accept(failure.getMessage());
            }
        }
        state = next;
        for (WindowContent owner : List.copyOf(owners)) {
            owner.applyConfiguration(next.snapshot(), service.macOs());
            owner.setConfigurationState(next);
        }
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        for (WindowContent owner : List.copyOf(owners)) unregister(owner);
        service.close();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Configuration UI requires the EDT");
    }
}
