package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** One fake plugin's declared settings over the host's raw values, keyed by dotted key. Caller's thread, like the rest of the fake. */
final class FakePluginSettings implements PluginSettings {
    static final String UNKNOWN = "Unknown setting or table; ignored.";

    private final FakePluginHost host;
    private final String pluginId;
    private final Path file;
    private final List<FakeSetting<?>> declared = new ArrayList<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean open = true;
    private dev.jasper.sdk.settings.SettingsPanel panel;
    Setting<?> handleByKey(String key){return declared.stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();}

    FakePluginSettings(FakePluginHost host, String pluginId, Path file) {
        this.host = host; this.pluginId = pluginId; this.file = file;
    }

    @Override public <T> Setting<T> declare(SettingSpec<T> spec) {
        Objects.requireNonNull(spec, "spec");
        if (!open) throw new IllegalStateException("Declare settings while the plugin starts: " + spec.key());
        if (spec.label().isBlank()) throw new IllegalArgumentException("A setting needs a label: " + spec.key());
        for (FakeSetting<?> other : declared) {
            if (other.key().equals(spec.key())) throw new IllegalArgumentException("Duplicate setting key: " + spec.key());
            if (other.key().startsWith(spec.key() + ".") || spec.key().startsWith(other.key() + "."))
                throw new IllegalArgumentException(spec.key() + " overlaps the setting " + other.key());
        }
        var setting = new FakeSetting<>(this, spec);
        declared.add(setting);
        setting.load(raw().get(spec.key()), false, new ArrayList<>());
        return setting;
    }

    @Override public Path file() { return file; }

    @Override public Subscription onChanged(Runnable handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    Map<String, Object> raw() { return host.settingFile(pluginId); }

    List<String> keys() { return declared.stream().map(FakeSetting::key).toList(); }

    @Override public void page(dev.jasper.sdk.settings.SettingsPanel panel) {
        Objects.requireNonNull(panel, "panel");
        if (!open) throw new IllegalStateException("Give a settings page while the plugin starts");
        if (this.panel != null) throw new IllegalStateException("A plugin has one settings page");
        this.panel = panel;
    }

    java.util.Optional<dev.jasper.sdk.settings.SettingsPanel> panel() {
        return java.util.Optional.ofNullable(panel);
    }

    /**
     * The handle this plugin declared, by identity.
     *
     * @param setting the setting to look up
     * @return the fake setting handle
     * @throws IllegalArgumentException if the setting is not from this plugin
     */
    FakeSetting<?> handle(dev.jasper.sdk.settings.Setting<?> setting) {
        for (FakeSetting<?> declared : this.declared) if (declared == setting) return declared;
        throw new IllegalArgumentException("Not a setting of this plugin: " + setting.key());
    }

    /** The plugin's start returned: declarations close, and the file's problems are reported. */
    void complete() {
        open = false;
        reload();
    }

    /** Re-reads every value, replacing this plugin's reports; listeners of changed values run. */
    void reload() {
        var reports = new ArrayList<String>();
        boolean changed = false;
        for (FakeSetting<?> setting : declared) changed |= setting.load(raw().get(setting.key()), true, reports);
        for (String key : List.copyOf(raw().keySet()))
            if (declared.stream().noneMatch(setting -> setting.key().equals(key))) reports.add(key + ": " + UNKNOWN);
        host.settingsReports.put(pluginId, List.copyOf(reports));
        if (changed) for (Runnable listener : listeners) listener.run();
    }

    void close() {
        open = false;
        listeners.clear();
        declared.forEach(FakeSetting::close);
    }
}
