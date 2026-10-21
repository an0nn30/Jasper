package dev.jasper.sdk.services;

import dev.jasper.sdk.PluginInfo;
import java.util.Optional;
import java.util.function.Function;

/**
 * The typed service registry. A publication lasts from the provider's successful start until it stops
 * at shutdown; there is no withdrawal. Publications become visible only when the provider's
 * {@code start} returns normally, so no consumer sees a service from a plugin that failed to start.
 * Lookups are cache reads that run no provider code and are safe from any thread.
 */
public interface Services {
    /**
     * Publishes one shared implementation. UI thread, during the provider's own {@code start} only.
     *
     * @param api an interface from one of the provider's exported packages
     * @param implementation the instance every consumer receives
     * @param <T> service type
     * @throws IllegalStateException outside the provider's {@code start}
     * @throws IllegalArgumentException when {@code api} is not an interface the provider owns, or is already published
     */
    <T> void publish(Class<T> api, T implementation);

    /**
     * Publishes a per-consumer factory. The runtime calls it on the UI thread once for each plugin
     * that declares {@code requires} on the provider, optional or not, just before that consumer starts, with the
     * consumer's verified identity; a factory that returns null or throws gives that consumer no instance. This gives
     * caller attribution; it is not a security boundary.
     *
     * @param api an interface from one of the provider's exported packages
     * @param perConsumer creates the instance one consumer receives
     * @param <T> service type
     * @throws IllegalStateException outside the provider's {@code start}
     * @throws IllegalArgumentException when {@code api} is not an interface the provider owns, or is already published
     */
    <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer);

    /**
     * Looks up a service published by one of the plugins this plugin {@code requires}, optional or not, and throws when
     * there is none.
     *
     * @param api the service interface
     * @param <T> service type
     * @return the caller's instance
     * @throws ServiceUnavailableException when no provider this plugin requires has started and published it
     */
    <T> T require(Class<T> api);

    /**
     * Looks up a service as {@link #require} does, empty when there is none. When an optional provider is absent, its
     * API classes are not visible either: the caller's first executed reference to one, such as the class literal it
     * passes here, throws {@link NoClassDefFoundError}, so catch that where the API is first used.
     *
     * @param api the service interface
     * @param <T> service type
     * @return the caller's instance, or empty
     */
    <T> Optional<T> find(Class<T> api);
}
