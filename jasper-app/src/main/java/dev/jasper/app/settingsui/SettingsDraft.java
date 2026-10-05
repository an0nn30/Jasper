package dev.jasper.app.settingsui;

import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingValues;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Settings dialog's view of the core settings: the saved values it opened with (the baseline) and the edits the
 * user has made since. An empty pending value means "restore the default", which removes the key when saved. EDT only.
 */
public final class SettingsDraft {
    private final Map<Setting<?>, Optional<?>> pending = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private SettingValues baseline;

    public SettingsDraft(SettingValues baseline) { this.baseline = Objects.requireNonNull(baseline, "baseline"); }

    public SettingValues baseline() { return baseline; }

    /** The value the dialog shows: the pending edit, else the saved value, else the default. */
    @SuppressWarnings("unchecked")
    public <T> T value(Setting<T> setting) {
        if (!pending.containsKey(setting)) return baseline.get(setting);
        Optional<T> staged = (Optional<T>) pending.get(setting);
        return staged.orElse(setting.defaultValue());
    }

    /** The saved value, or empty when the file does not set it. */
    public <T> Optional<T> saved(Setting<T> setting) { return baseline.saved(setting); }

    /** Records an edit; one that matches the saved state removes any pending edit instead. */
    public <T> void stage(Setting<T> setting, Optional<T> value) {
        Objects.requireNonNull(value, "value");
        if (setting.sameSaved(value, baseline.saved(setting))) pending.remove(setting);
        else pending.put(setting, value);
        changed();
    }

    public boolean modified() { return !pending.isEmpty(); }

    public boolean modified(Setting<?> setting) { return pending.containsKey(setting); }

    /** How many settings have pending edits. */
    public int size() { return pending.size(); }

    /** The save request for every pending edit, expecting the baseline's saved values. */
    public SettingsEdit edit() {
        var expected = new LinkedHashMap<Setting<?>, Optional<?>>();
        for (Setting<?> setting : pending.keySet()) expected.put(setting, baseline.saved(setting));
        return new SettingsEdit(pending, expected);
    }

    /** Adopts newer saved values; pending edits the new values already hold are dropped. */
    public void rebase(SettingValues next) {
        baseline = Objects.requireNonNull(next, "next");
        pending.entrySet().removeIf(entry -> entry.getKey().sameSaved(entry.getValue(), baseline.saved(entry.getKey())));
        changed();
    }

    /** Drops the pending edits of {@code settings}. */
    public void reset(Collection<? extends Setting<?>> settings) {
        if (pending.keySet().removeAll(settings)) changed();
    }

    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private void changed() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
