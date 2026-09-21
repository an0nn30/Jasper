package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Publications are staged during a provider's start and committed only when it succeeds. Factories
 * run on the UI thread in {@link #prepare}, just before a consumer starts; lookups are cache reads.
 * Staging, commit, discard and prepare are UI-thread only; {@link #find} is safe from any thread.
 */
final class ServiceRegistry {
    private record Provider(String pluginId, Function<PluginInfo, ?> factory) { }

    private final Map<Class<?>, Provider> committed = new LinkedHashMap<>();
    private final Map<String, Map<Class<?>, Provider>> staged = new LinkedHashMap<>();
    private final ConcurrentHashMap<String, Map<Class<?>, Object>> resolved = new ConcurrentHashMap<>();

    void stage(HostedPlugin owner, Class<?> api, Function<PluginInfo, ?> factory) {
        Objects.requireNonNull(factory, "factory");
        String id = owner.info().id();
        if (!api.isInterface()) throw new IllegalArgumentException("A service API must be an interface: " + api.getName());
        if (api.getClassLoader() != owner.loader() || !owner.exports().contains(api.getPackageName()))
            throw new IllegalArgumentException(api.getName() + " is not from one of " + id + "'s exported packages");
        var mine = staged.computeIfAbsent(id, key -> new LinkedHashMap<>());
        if (committed.containsKey(api) || mine.containsKey(api))
            throw new IllegalArgumentException("Service already published: " + api.getName());
        mine.put(api, new Provider(id, factory));
    }

    void commit(String pluginId) {
        Map<Class<?>, Provider> mine = staged.remove(pluginId);
        if (mine != null) committed.putAll(mine);
    }

    void discard(String pluginId) { staged.remove(pluginId); }

    void prepare(PluginInfo consumer, Set<String> providerIds, Containment containment) {
        Map<Class<?>, Object> mine = new ConcurrentHashMap<>();
        for (var service : committed.entrySet()) {
            Provider provider = service.getValue();
            if (!providerIds.contains(provider.pluginId())) continue;
            containment.attempt(provider.pluginId(), "service factory for " + service.getKey().getName(), () -> {
                Object instance = provider.factory().apply(consumer);
                if (instance != null) mine.put(service.getKey(), service.getKey().cast(instance));
                return null;
            });
        }
        resolved.put(consumer.id(), mine);
    }

    <T> Optional<T> find(String consumerId, Class<T> api) {
        Map<Class<?>, Object> mine = resolved.get(consumerId);
        return mine == null ? Optional.empty() : Optional.ofNullable(api.cast(mine.get(api)));
    }
}
