package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.tomlj.Toml;

/**
 * One saved setting: where it lives, how it is read and written, its default and how people find it.
 *
 * @param key the full key path, such as {@code [terminal, font, size]}
 * @param kind how its value is read, checked and written
 * @param defaultValue the value when the file does not set it
 * @param example a value the generated template shows instead of the default (for a default that is not a
 *                writable value, such as a platform-sized font)
 * @param aliases older keys still read as this setting
 * @param label a short name for people
 * @param description plain text; each line becomes a comment line in the generated template
 * @param keywords extra words search should match
 * @param applies when a saved change takes effect
 */
public record Setting<T>(List<String> key, SettingKind<T> kind, T defaultValue, Optional<T> example,
                         List<List<String>> aliases, String label, String description, List<String> keywords,
                         Applies applies) {
    public Setting {
        key = List.copyOf(key);
        if (key.isEmpty()) throw new IllegalArgumentException("A setting needs a key");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(example, "example");
        aliases = aliases.stream().map(List::copyOf).toList();
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A setting needs a label: " + TomlLiterals.path(key));
        Objects.requireNonNull(description, "description");
        keywords = List.copyOf(keywords);
        Objects.requireNonNull(applies, "applies");
    }

    /** Starts a setting at a dotted TOML key, such as {@code "terminal.font.size"}. */
    public static <T> Builder<T> of(String dottedKey, SettingKind<T> kind, T defaultValue) {
        return new Builder<>(Toml.parseDottedKey(dottedKey), kind, defaultValue);
    }

    /** The key as TOML writes it, for messages. */
    public String name() { return TomlLiterals.path(key); }

    /** The table that holds this setting's key. */
    public List<String> table() { return key.subList(0, key.size() - 1); }

    /** The key's last component. */
    public String leaf() { return key.getLast(); }

    /**
     * Whether two saved states of this setting mean the same file. For a table, no entries is the same as no table: an
     * empty {@code [table]} header (the template has them uncommented) holds nothing.
     */
    public boolean sameSaved(Optional<?> one, Optional<?> other) {
        return withEntries(one).equals(withEntries(other));
    }

    private Optional<?> withEntries(Optional<?> saved) {
        if (!(kind instanceof SettingKind.StringMap)) return saved;
        return saved.filter(map -> !((java.util.Map<?, ?>) map).isEmpty());
    }

    /** Caller-confined construction; {@link #build} validates. */
    public static final class Builder<T> {
        private final List<String> key;
        private final SettingKind<T> kind;
        private final T defaultValue;
        private final List<List<String>> aliases = new ArrayList<>();
        private T example;
        private String label;
        private String description = "";
        private List<String> keywords = List.of();
        private Applies applies = Applies.LIVE;

        private Builder(List<String> key, SettingKind<T> kind, T defaultValue) {
            this.key = key; this.kind = kind; this.defaultValue = defaultValue;
        }

        public Builder<T> alias(String dottedKey) { aliases.add(Toml.parseDottedKey(dottedKey)); return this; }
        public Builder<T> label(String value) { label = value; return this; }
        public Builder<T> description(String value) { description = value; return this; }
        public Builder<T> keywords(String... values) { keywords = List.of(values); return this; }
        public Builder<T> applies(Applies value) { applies = value; return this; }
        public Builder<T> example(T value) { example = value; return this; }

        public Setting<T> build() {
            return new Setting<>(key, kind, defaultValue, Optional.ofNullable(example), aliases, label, description, keywords, applies);
        }
    }
}
