package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.*;
import dev.jasper.app.workspace.WindowContent;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Package-local test access, excluded from production artifacts. */
public final class ConfigurationTestSupport {
    private final ConfigurationController controller;
    public ConfigurationTestSupport(ThemeController themes, ConfigService service) { controller = new ConfigurationController(themes, service); }
    public ConfigurationTestSupport(ThemeController themes, ConfigService service, Consumer<Path> editor) { controller = new ConfigurationController(themes, service, editor); }
    public void register(WindowContent owner) { controller.register(owner); }
    public void unregister(WindowContent owner) { controller.unregister(owner); }
    public void accept(ConfigService.State state) { controller.accept(state); }
    public void onSnapshot(Consumer<ConfigSnapshot> listener) { controller.onSnapshot(listener); }
    public ConfigSnapshot snapshot() { return controller.snapshot(); }
    public void close() { controller.close(); }
}
