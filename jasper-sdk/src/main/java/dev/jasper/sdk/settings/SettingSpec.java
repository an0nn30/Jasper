package dev.jasper.sdk.settings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The declaration of one plugin setting: its key in the settings file, its kind and default, and how Settings shows
 * it. Start one with {@link #of}, give it at least a {@link #label(String) label}, and pass it to
 * {@link PluginSettings#declare}; do not change it afterwards, since Settings reads some of it, such as its section,
 * description, editor hint and unit, again when it shows the page.
 *
 * @param <T> the value type
 */
public final class SettingSpec<T> {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    private final String key;
    private final SettingKind<T> kind;
    private final T defaultValue;
    private String label = "";
    private String description = "";
    private List<String> keywords = List.of();
    private String section = "";
    private Applies applies = Applies.LIVE;
    private EditorHint editor;
    private String unit = "";

    private SettingSpec(String key, SettingKind<T> kind, T defaultValue) {
        this.key = key; this.kind = kind; this.defaultValue = defaultValue;
    }

    /**
     * Starts a declaration.
     *
     * @param key the dotted key in the settings file, such as {@code sftp.max_parallel_files}; each part is letters,
     *            digits, {@code _} and {@code -}, and each dot but the last names a table
     * @param kind how values are checked
     * @param defaultValue the value when the file does not set one; the kind must allow it
     * @param <T> the value type
     * @return the declaration
     */
    public static <T> SettingSpec<T> of(String key, SettingKind<T> kind, T defaultValue) {
        if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("Not a setting key: " + key);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Optional<String> problem = kind.check(defaultValue);
        if (problem.isPresent()) throw new IllegalArgumentException("The default of " + key + " is not allowed: " + problem.get());
        return new SettingSpec<>(key, kind, defaultValue);
    }

    /**
     * Sets the name Settings shows.
     *
     * @param value non-blank text
     * @return this declaration
     */
    public SettingSpec<T> label(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A setting needs a label: " + key);
        label = value;
        return this;
    }

    /**
     * Sets the explanation Settings shows under the control.
     *
     * @param value plain text
     * @return this declaration
     */
    public SettingSpec<T> description(String value) { description = Objects.requireNonNull(value, "value"); return this; }

    /**
     * Sets extra words Settings' search matches.
     *
     * @param values the words
     * @return this declaration
     */
    public SettingSpec<T> keywords(String... values) { keywords = List.of(values); return this; }

    /**
     * Sets the heading this setting appears under on the plugin's page.
     *
     * @param value the heading; empty for none
     * @return this declaration
     */
    public SettingSpec<T> section(String value) { section = Objects.requireNonNull(value, "value"); return this; }

    /**
     * Sets when a saved change takes effect.
     *
     * @param value when it applies
     * @return this declaration
     */
    public SettingSpec<T> applies(Applies value) { applies = Objects.requireNonNull(value, "value"); return this; }

    /**
     * Sets the control Settings uses.
     *
     * @param value the hint
     * @return this declaration
     */
    public SettingSpec<T> editor(EditorHint value) { editor = Objects.requireNonNull(value, "value"); return this; }

    /**
     * Sets the unit Settings shows after a number, such as {@code min} or {@code MiB}. Only a numeric kind shows it.
     *
     * @param value a short unit; empty for none
     * @return this declaration
     */
    public SettingSpec<T> unit(String value) {
        if (Objects.requireNonNull(value, "value").length() > 16 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Not a setting unit: " + key);
        unit = value.strip();
        return this;
    }

    /**
     * The dotted key.
     *
     * @return the key
     */
    public String key() { return key; }

    /**
     * How values are checked.
     *
     * @return the kind
     */
    public SettingKind<T> kind() { return kind; }

    /**
     * The value when the file does not set one.
     *
     * @return the default
     */
    public T defaultValue() { return defaultValue; }

    /**
     * The name Settings shows.
     *
     * @return the label; empty until {@link #label(String)} sets it
     */
    public String label() { return label; }

    /**
     * The explanation Settings shows.
     *
     * @return the description; empty by default
     */
    public String description() { return description; }

    /**
     * Extra search words.
     *
     * @return the keywords
     */
    public List<String> keywords() { return keywords; }

    /**
     * The heading on the plugin's page.
     *
     * @return the section; empty for none
     */
    public String section() { return section; }

    /**
     * When a saved change takes effect.
     *
     * @return {@link Applies#LIVE} unless set
     */
    public Applies applies() { return applies; }

    /**
     * The control Settings uses, when the kind alone does not decide it.
     *
     * @return the hint, if any
     */
    public Optional<EditorHint> editor() { return Optional.ofNullable(editor); }

    /**
     * The unit Settings shows after a number.
     *
     * @return the unit; empty for none
     */
    public String unit() { return unit; }
}
