package dev.jasper.sdk.settings;

import dev.jasper.sdk.Subscription;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A declared setting of the running plugin: its value in force and a way to save a new one.
 *
 * @param <T> the value type
 */
public interface Setting<T> {
    /**
     * The dotted key.
     *
     * @return the key
     */
    String key();

    /**
     * The value in force: the file's value when the kind allows it, otherwise the default. Safe from any thread.
     *
     * @return the value
     */
    T get();

    /**
     * Saves a new value to the plugin's settings file, keeping the file's comments. The future completes after this
     * setting's handle shows the new value. It fails with {@link IllegalArgumentException} when the kind rejects the
     * value, and otherwise when the file changed on disk, has errors or cannot be written, or the plugin has stopped.
     * Safe from any thread.
     *
     * @param value the new value
     * @return the save's completion
     */
    CompletableFuture<Void> set(T value);

    /**
     * Runs the handler on the UI thread whenever this setting's value changes, whether the file changed or
     * {@link #set} saved it.
     *
     * @param handler receives the new value
     * @return the registration; the runtime closes it when the plugin stops
     */
    Subscription onChanged(Consumer<T> handler);
}
