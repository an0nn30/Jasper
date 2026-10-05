package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.*;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.workspace.WindowContent;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Package-local test access, excluded from production artifacts. */
public final class ConfigurationTestSupport {
    private final ConfigurationController controller;
    public ConfigurationTestSupport(ThemeController themes, ConfigService service) { this(themes, service, ThemeCatalog.builtIn()); }
    public ConfigurationTestSupport(ThemeController themes, ConfigService service, ThemeCatalog catalog) {
        controller = new ConfigurationController(themes, service, catalog);
    }
    public ConfigurationTestSupport(ThemeController themes, ConfigService service, Consumer<Path> editor) {
        controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), editor);
    }
    public ConfigService.State shown() { return controller.shown(); }
    public void register(WindowContent owner) { controller.register(owner); }
    public void unregister(WindowContent owner) { controller.unregister(owner); }
    public void accept(ConfigService.State state) { controller.accept(state); }
    public void onSnapshot(Consumer<ConfigSnapshot> listener) { controller.onSnapshot(listener); }
    public void onProblems(Consumer<ConfigService.State> listener) { controller.onProblems(listener); }
    public ConfigSnapshot snapshot() { return controller.snapshot(); }
    public java.util.concurrent.CompletableFuture<Void> saveKeybindings(java.util.Map<String, java.util.Optional<String>> changes,
            java.util.List<KeyBindings.Extension> extensions) { return controller.saveKeybindings(changes, extensions); }
    public void report(ConfigDiagnostic diagnostic) { controller.report(diagnostic); }
    public void clearReports(Path file) { controller.clearReports(file); }
    public boolean migrationAvailable() { return controller.migrationAvailable(); }
    public java.util.concurrent.CompletableFuture<String> migrate() { return controller.migrate(); }
    public void pluginSettingsExist(java.util.function.Predicate<String> test) { controller.pluginSettingsExist(test); }
    public ConfigService.State state() { return controller.state(); }
    public java.util.concurrent.CompletableFuture<ConfigService.State> save(SettingsEdit edit) { return controller.save(edit); }
    public dev.jasper.app.lifecycle.Subscription onState(Consumer<ConfigService.State> listener) { return controller.onState(listener); }
    public void settingsOpener(java.util.function.BiConsumer<WindowContent, String> opener) { controller.settingsOpener(opener); }
    public void report(String key, String message) { controller.report(key, message); }
    public void close() { controller.close(); }
}
