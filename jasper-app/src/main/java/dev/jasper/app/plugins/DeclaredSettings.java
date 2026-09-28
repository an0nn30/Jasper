package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.SchemaReader;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settings.SettingWriter;
import dev.jasper.app.settings.SettingsSchema;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.SettingSpec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One plugin's declared settings: its schema, the values its file holds and its handles. Declarations and reloads run
 * on the UI thread; values are readable from any thread; saves write off the UI thread.
 */
final class DeclaredSettings implements PluginSettings {
    private final String pluginId;
    private final PluginSettingsFiles files;
    private final Containment containment;
    private final Consumer<Runnable> ui;
    private final Consumer<ConfigDiagnostic> report;
    private final Runnable written;
    private final List<DeclaredSetting<?>> handles = new ArrayList<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile SettingsSchema schema = SettingsSchema.of(List.of(), Set.of(), Map.of());
    private volatile SettingValues values = SettingValues.defaults(schema);
    private volatile boolean open = true;
    private volatile boolean closed;

    // written: runs on the UI thread after a save; the host clears the file's reports and reloads it.
    DeclaredSettings(String pluginId, PluginSettingsFiles files, Containment containment, Consumer<Runnable> ui,
                     Consumer<ConfigDiagnostic> report, Runnable written) {
        this.pluginId = pluginId; this.files = files; this.containment = containment; this.ui = ui;
        this.report = report; this.written = written;
    }

    SettingValues values() { return values; }

    @Override public <T> dev.jasper.sdk.settings.Setting<T> declare(SettingSpec<T> spec) {
        Objects.requireNonNull(spec, "spec");
        if (!open) throw new IllegalStateException("Declare settings while the plugin starts: " + spec.key());
        if (spec.label().isBlank()) throw new IllegalArgumentException("A setting needs a label: " + spec.key());
        var translated = SettingTranslation.translate(spec, this::guard);
        var settings = new ArrayList<Setting<?>>();
        for (DeclaredSetting<?> handle : handles) settings.add(handle.appSetting());
        settings.add(translated.setting());
        SettingsSchema next = SettingsSchema.of(settings, Set.of(), Map.of());
        var handle = new DeclaredSetting<T>(this, translated);
        handles.add(handle);
        schema = next;
        values = SchemaReader.read(next, files.text(pluginId)).values();
        return handle;
    }

    @Override public Path file() { return files.file(pluginId); }

    @Override public Subscription onChanged(Runnable handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    /** The plugin's start returned: declarations close, and the file's problems are reported. UI thread. */
    void complete() {
        open = false;
        reload(true);
    }

    /**
     * Reads the file again; {@code report}: the file was reloaded and its earlier reports are gone. A rejected file
     * keeps the last good values. Listeners of changed values run. A plugin that declared nothing still reads its file
     * through {@code PluginConfig} until SDK 0.9, so nothing is read or reported for it here. UI thread.
     */
    void reload(boolean report) {
        if (closed || open || handles.isEmpty()) return;
        var result = SchemaReader.read(schema, files.text(pluginId));
        if (report) for (SchemaReader.Problem problem : result.problems()) this.report.accept(diagnostic(problem));
        if (result.rejected()) return;
        SettingValues before = values;
        values = result.values();
        boolean any = false;
        for (DeclaredSetting<?> handle : handles) {
            if (!handle.changed(before, values)) continue;
            any = true;
            handle.fire(values, containment, pluginId);
        }
        if (any) for (Runnable listener : listeners) containment.run(pluginId, "settings change", listener);
    }

    void close() {
        closed = true;
        open = false;
        listeners.clear();
        handles.forEach(DeclaredSetting::close);
    }

    /** Checks and writes one value off the UI thread; the future completes after the handles show it. */
    <T> CompletableFuture<Void> save(DeclaredSetting<T> handle, T value) {
        Objects.requireNonNull(value, "value");
        Optional<String> problem = handle.spec().kind().check(value);
        if (problem.isPresent()) return CompletableFuture.failedFuture(new IllegalArgumentException(problem.get()));
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("The plugin has stopped."));
        @SuppressWarnings("unchecked") Setting<Object> setting = (Setting<Object>) handle.appSetting();
        Object appValue = handle.toApp(value);
        Optional<Object> expected = values.saved(setting);
        SettingsSchema current = schema;
        var done = new CompletableFuture<Void>();
        Thread.ofVirtual().name("jasper-plugin-settings-save").start(() -> {
            try {
                files.save(pluginId, text -> edit(current, text, setting, appValue, expected));
                ui.accept(() -> { written.run(); done.complete(null); });
            } catch (IOException | RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    private String edit(SettingsSchema current, String text, Setting<Object> setting, Object value, Optional<Object> expected) {
        String name = file().getFileName().toString();
        var read = SchemaReader.read(current, text);
        if (read.rejected()) throw new IllegalStateException(name + " has errors; fix them before saving settings.");
        if (!read.values().saved(setting).equals(expected))
            throw new IllegalStateException(setting.name() + " changed on disk. Review the new value and try again.");
        String updated = SettingWriter.write(TomlDocument.parse(text), setting, Optional.of(value)).text();
        var checked = SchemaReader.read(current, updated);
        if (checked.rejected() || !checked.values().get(setting).equals(value))
            throw new IllegalStateException("Cannot safely save " + setting.name() + "; review " + name + ".");
        return updated;
    }

    /** A plugin-supplied rule that runs contained; a throw rejects the value. */
    private Predicate<String> guard(Predicate<String> rule) {
        return text -> {
            boolean[] accepted = {false};
            Throwable failure = containment.attempt(pluginId, "setting check", () -> { accepted[0] = rule.test(text); return null; });
            return failure == null && accepted[0];
        };
    }

    private ConfigDiagnostic diagnostic(SchemaReader.Problem problem) {
        return new ConfigDiagnostic(problem.severity() == Reading.Severity.WARNING ? ConfigDiagnostic.Severity.WARNING
            : ConfigDiagnostic.Severity.ERROR, file(), problem.line(), problem.column(), problem.key(), problem.message());
    }
}
