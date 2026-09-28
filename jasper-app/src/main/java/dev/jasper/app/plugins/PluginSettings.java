package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.tomlj.Toml;

/** One plugin's settings, read from its own file. Getters are safe from any thread; {@link #update} is UI-thread only. */
final class PluginSettings implements PluginConfig {
    private final String pluginId;
    private final Containment containment;
    private final Consumer<ConfigDiagnostic> report;
    private final Function<List<String>, Optional<PluginSettingsFiles.Position>> positions;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final View root;
    private volatile Map<String, Object> values;

    private final java.nio.file.Path file;

    PluginSettings(String pluginId, java.nio.file.Path file, Map<String, Object> initial, Containment containment,
                   Function<List<String>, Optional<PluginSettingsFiles.Position>> positions, Consumer<ConfigDiagnostic> report) {
        this.pluginId = pluginId; this.file = file; this.containment = containment; this.positions = positions; this.report = report;
        this.values = Map.copyOf(initial);
        this.root = new View(() -> values, List.of());
    }

    void update(Map<String, Object> next) { update(next, false); }

    /** Replaces the values; listeners run when they changed, or always when {@code force} (the file was reloaded). */
    void update(Map<String, Object> next, boolean force) {
        if (next.equals(values) && !force) return;
        values = Map.copyOf(next);
        for (Runnable listener : listeners) containment.run(pluginId, "configuration change", listener);
    }

    void close() { listeners.clear(); }

    @Override public java.nio.file.Path file() { return file; }
    @Override public Optional<String> string(String key) { return root.string(key); }
    @Override public OptionalLong integer(String key) { return root.integer(key); }
    @Override public Optional<Boolean> bool(String key) { return root.bool(key); }
    @Override public List<String> stringList(String key) { return root.stringList(key); }
    @Override public Optional<PluginConfig> table(String key) { return root.table(key); }
    @Override public Subscription onChanged(Runnable handler) { return root.onChanged(handler); }
    @Override public void report(String key, String message) { root.report(key, message); }

    private final class View implements PluginConfig {
        private final Supplier<Map<String, Object>> table;
        private final List<String> prefix;

        View(Supplier<Map<String, Object>> table, List<String> prefix) { this.table = table; this.prefix = prefix; }
        @Override public java.nio.file.Path file() { return file; }

        @Override public Optional<String> string(String key) {
            return table.get().get(key) instanceof String text ? Optional.of(text) : Optional.empty();
        }
        @Override public OptionalLong integer(String key) {
            return table.get().get(key) instanceof Long number ? OptionalLong.of(number) : OptionalLong.empty();
        }
        @Override public Optional<Boolean> bool(String key) {
            return table.get().get(key) instanceof Boolean flag ? Optional.of(flag) : Optional.empty();
        }
        @Override public List<String> stringList(String key) {
            if (!(table.get().get(key) instanceof List<?> list)) return List.of();
            for (Object item : list) if (!(item instanceof String)) return List.of();
            return list.stream().map(String.class::cast).toList();
        }
        @Override public Optional<PluginConfig> table(String key) {
            if (!(table.get().get(key) instanceof Map<?, ?>)) return Optional.empty();
            var nested = new ArrayList<>(prefix);
            nested.add(key);
            return Optional.of(new View(() -> nested(key), List.copyOf(nested)));
        }
        @SuppressWarnings("unchecked")
        private Map<String, Object> nested(String key) {
            return table.get().get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }
        @Override public Subscription onChanged(Runnable handler) {
            listeners.add(java.util.Objects.requireNonNull(handler, "handler"));
            return () -> listeners.remove(handler);
        }
        @Override public void report(String key, String message) {
            var path = new ArrayList<>(prefix);
            path.add(key);
            var at = positions.apply(path);
            report.accept(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file,
                at.map(PluginSettingsFiles.Position::line).orElse(0), at.map(PluginSettingsFiles.Position::column).orElse(0),
                Toml.joinKeyPath(path), message));
        }
    }
}
