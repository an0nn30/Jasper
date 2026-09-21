package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Map-backed configuration view; nested tables share the root's listeners and report sink. */
final class FakePluginConfig implements PluginConfig {
    private final Supplier<Map<String, Object>> values;
    private final String prefix;
    private final CopyOnWriteArrayList<Runnable> listeners;
    private final BiConsumer<String, String> report;

    FakePluginConfig(Supplier<Map<String, Object>> values, String prefix,
                     CopyOnWriteArrayList<Runnable> listeners, BiConsumer<String, String> report) {
        this.values = values; this.prefix = prefix; this.listeners = listeners; this.report = report;
    }

    @Override public Optional<String> string(String key) {
        return values.get().get(key) instanceof String text ? Optional.of(text) : Optional.empty();
    }
    @Override public OptionalLong integer(String key) {
        return values.get().get(key) instanceof Long number ? OptionalLong.of(number) : OptionalLong.empty();
    }
    @Override public Optional<Boolean> bool(String key) {
        return values.get().get(key) instanceof Boolean flag ? Optional.of(flag) : Optional.empty();
    }
    @Override public List<String> stringList(String key) {
        if (!(values.get().get(key) instanceof List<?> list)) return List.of();
        for (Object item : list) if (!(item instanceof String)) return List.of();
        return list.stream().map(String.class::cast).toList();
    }
    @Override public Optional<PluginConfig> table(String key) {
        if (!(values.get().get(key) instanceof Map<?, ?>)) return Optional.empty();
        return Optional.of(new FakePluginConfig(() -> nested(key), prefix + key + ".", listeners, report));
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(String key) {
        return values.get().get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    @Override public Subscription onChanged(Runnable handler) {
        listeners.add(handler);
        return () -> listeners.remove(handler);
    }
    @Override public void report(String key, String message) { report.accept(prefix + key, message); }
}
