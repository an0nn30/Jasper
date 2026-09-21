package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** One plugin's {@code [plugins."<id>"]} table. Getters are safe from any thread; {@link #update} is UI-thread only. */
final class PluginSettings implements PluginConfig {
    private final String pluginId;
    private final Containment containment;
    private final BiConsumer<String, String> report;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final View root;
    private volatile Map<String, Object> values;

    PluginSettings(String pluginId, Map<String, Object> initial, Containment containment, BiConsumer<String, String> report) {
        this.pluginId = pluginId; this.containment = containment; this.report = report;
        this.values = Map.copyOf(initial);
        this.root = new View(() -> values, "");
    }

    void update(Map<String, Object> next) {
        if (next.equals(values)) return;
        values = Map.copyOf(next);
        for (Runnable listener : listeners) containment.run(pluginId, "configuration change", listener);
    }

    void close() { listeners.clear(); }

    @Override public Optional<String> string(String key) { return root.string(key); }
    @Override public OptionalLong integer(String key) { return root.integer(key); }
    @Override public Optional<Boolean> bool(String key) { return root.bool(key); }
    @Override public List<String> stringList(String key) { return root.stringList(key); }
    @Override public Optional<PluginConfig> table(String key) { return root.table(key); }
    @Override public Subscription onChanged(Runnable handler) { return root.onChanged(handler); }
    @Override public void report(String key, String message) { root.report(key, message); }

    private final class View implements PluginConfig {
        private final Supplier<Map<String, Object>> table;
        private final String prefix;

        View(Supplier<Map<String, Object>> table, String prefix) { this.table = table; this.prefix = prefix; }

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
            return Optional.of(new View(() -> nested(key), prefix + key + "."));
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
            report.accept("plugins.\"" + pluginId + "\"." + prefix + key, message);
        }
    }
}
