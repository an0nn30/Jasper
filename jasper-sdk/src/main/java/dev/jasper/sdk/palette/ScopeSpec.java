package dev.jasper.sdk.palette;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * What a scope is: identity, wording, verbs and how it is reached.
 *
 * @param id               namespaced like an action id and starting with the plugin's id, such as
 *                         {@code dev.jasper.history.scope}; the host rejects any other prefix
 * @param label            the chip text, such as "History"
 * @param description      one line for the scope picker
 * @param placeholder      the query field's hint while this scope is active
 * @param aliases          lower-case words the picker accepts after {@code >}, such as {@code hist}
 * @param verbs            one to three verbs with distinct ids, bound to Enter, Cmd/Ctrl+Enter, Shift+Enter
 * @param monospaceRows    whether rows are painted in the terminal font (commands, paths)
 * @param icon             the chip icon
 * @param shortcutActionId one of the plugin's own actions: while the palette is open, that action's
 *                         shortcut switches to or dismisses this scope instead of running the handler
 */
public record ScopeSpec(String id, String label, String description, String placeholder, List<String> aliases,
                        List<PaletteVerb> verbs, boolean monospaceRows, Optional<Icon> icon, Optional<String> shortcutActionId) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");
    private static final Pattern ALIAS = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

    /** Validates every part and copies the lists. */
    public ScopeSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches()) throw new IllegalArgumentException("Not a namespaced scope id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A scope needs a label");
        description = description == null ? "" : description;
        if (placeholder == null || placeholder.isBlank()) throw new IllegalArgumentException("A scope needs a placeholder");
        aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
        for (String alias : aliases) if (!ALIAS.matcher(alias).matches()) throw new IllegalArgumentException("Not a lower-case alias: " + alias);
        verbs = List.copyOf(Objects.requireNonNull(verbs, "verbs"));
        if (verbs.isEmpty() || verbs.size() > 3) throw new IllegalArgumentException("A scope has one to three verbs");
        Set<String> ids = new HashSet<>();
        for (PaletteVerb verb : verbs) if (!ids.add(verb.id())) throw new IllegalArgumentException("Duplicate verb id: " + verb.id());
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(shortcutActionId, "shortcutActionId");
    }

    /**
     * The minimum: no aliases, description, icon or shortcut, proportional rows.
     *
     * @param id          the scope id
     * @param label       the chip text
     * @param placeholder the query hint
     * @param verbs       the verbs
     * @return the spec
     */
    public static ScopeSpec of(String id, String label, String placeholder, List<PaletteVerb> verbs) {
        return new ScopeSpec(id, label, "", placeholder, List.of(), verbs, false, Optional.empty(), Optional.empty());
    }

    /**
     * Derives a value.
     *
     * @param value the picker line
     *  @return a copy with that description */
    public ScopeSpec withDescription(String value) { return new ScopeSpec(id, label, value, placeholder, aliases, verbs, monospaceRows, icon, shortcutActionId); }

    /**
     * Derives a value.
     *
     * @param value the aliases
     *  @return a copy with those aliases */
    public ScopeSpec withAliases(List<String> value) { return new ScopeSpec(id, label, description, placeholder, value, verbs, monospaceRows, icon, shortcutActionId); }

    /**
     * Derives a value.
     *
     * @param value whether rows use the terminal font
     *  @return a copy with that setting */
    public ScopeSpec withMonospaceRows(boolean value) { return new ScopeSpec(id, label, description, placeholder, aliases, verbs, value, icon, shortcutActionId); }

    /**
     * Derives a value.
     *
     * @param value the chip icon, or null for none
     *  @return a copy with that icon */
    public ScopeSpec withIcon(Icon value) { return new ScopeSpec(id, label, description, placeholder, aliases, verbs, monospaceRows, Optional.ofNullable(value), shortcutActionId); }

    /**
     * Derives a value.
     *
     * @param value the plugin's action whose shortcut reaches this scope, or null for none
     *  @return a copy naming it */
    public ScopeSpec withShortcutActionId(String value) { return new ScopeSpec(id, label, description, placeholder, aliases, verbs, monospaceRows, icon, Optional.ofNullable(value)); }
}
