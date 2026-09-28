package dev.jasper.app.plugins;

import dev.jasper.app.settings.SettingValues;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** One declared setting's handle. {@link #get} is safe from any thread; listeners run on the UI thread, contained. */
final class DeclaredSetting<T> implements Setting<T> {
    private final DeclaredSettings owner;
    private final SettingTranslation.Translated translated;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    DeclaredSetting(DeclaredSettings owner, SettingTranslation.Translated translated) {
        this.owner = owner; this.translated = translated;
    }

    dev.jasper.app.settings.Setting<?> appSetting() { return translated.setting(); }

    @SuppressWarnings("unchecked")
    SettingSpec<T> spec() { return (SettingSpec<T>) translated.spec(); }

    Object toApp(T value) { return translated.toApp().apply(value); }

    @SuppressWarnings("unchecked")
    T value(SettingValues values) { return (T) translated.toSdk().apply(values.get(translated.setting())); }

    @Override public String key() { return translated.spec().key(); }

    @Override public T get() { return value(owner.values()); }

    @Override public CompletableFuture<Void> set(T value) { return owner.save(this, value); }

    @Override public Subscription onChanged(Consumer<T> handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    /** Whether this setting's value differs between two reads of the same schema. */
    boolean changed(SettingValues before, SettingValues after) {
        return !before.get(translated.setting()).equals(after.get(translated.setting()));
    }

    void fire(SettingValues values, Containment containment, String pluginId) {
        T value = value(values);
        for (Consumer<T> listener : listeners) containment.run(pluginId, "setting change", () -> listener.accept(value));
    }

    void close() { listeners.clear(); }
}
