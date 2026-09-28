package dev.jasper.app.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loaded values: each setting's value, and whether the file set it and under which key. Immutable. */
public final class SettingValues {
    private record Entry(Object value, List<String> source) { }

    private final SettingsSchema schema;
    private final Map<List<String>, Entry> entries;

    private SettingValues(SettingsSchema schema, Map<List<String>, Entry> entries) {
        this.schema = schema;
        this.entries = Map.copyOf(entries);
    }

    /** Every setting at its default; nothing present. */
    public static SettingValues defaults(SettingsSchema schema) { return new SettingValues(schema, Map.of()); }

    public static Builder builder(SettingsSchema schema) { return new Builder(schema); }

    /** The value in force: the file's, or the default. */
    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> setting) {
        Entry entry = entries.get(known(schema, setting));
        return entry == null ? setting.defaultValue() : (T) entry.value();
    }

    /** Whether the file set this setting, under its current key or an old one. */
    public boolean present(Setting<?> setting) { return entries.containsKey(known(schema, setting)); }

    /** The key the file set it under. */
    public Optional<List<String>> source(Setting<?> setting) {
        return Optional.ofNullable(entries.get(known(schema, setting))).map(Entry::source);
    }

    /** The saved value, or empty when the file does not set it. */
    public <T> Optional<T> saved(Setting<T> setting) { return present(setting) ? Optional.of(get(setting)) : Optional.empty(); }

    private static List<String> known(SettingsSchema schema, Setting<?> setting) {
        if (!schema.find(setting.key()).map(setting::equals).orElse(false))
            throw new IllegalArgumentException("Not a setting of this schema: " + setting.name());
        return setting.key();
    }

    @Override public boolean equals(Object other) {
        return other instanceof SettingValues values && schema == values.schema && entries.equals(values.entries);
    }
    @Override public int hashCode() { return entries.hashCode(); }
    @Override public String toString() { return "SettingValues" + entries; }

    /** Caller-confined; the last {@link #put} for a setting wins. */
    public static final class Builder {
        private final SettingsSchema schema;
        private final Map<List<String>, Entry> entries = new LinkedHashMap<>();

        private Builder(SettingsSchema schema) { this.schema = schema; }

        public <T> Builder put(Setting<T> setting, T value, List<String> source) {
            entries.put(known(schema, setting), new Entry(Objects.requireNonNull(value, "value"), List.copyOf(source)));
            return this;
        }

        public boolean present(Setting<?> setting) { return entries.containsKey(known(schema, setting)); }

        public SettingValues build() { return new SettingValues(schema, entries); }
    }
}
