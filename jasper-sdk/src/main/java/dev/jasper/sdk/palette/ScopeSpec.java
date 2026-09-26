package dev.jasper.sdk.palette;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * What a scope is: identity, wording, verbs and how it is reached. The palette shows an All tab first,
 * then one tab per scope.
 *
 * @param id               namespaced like an action id and starting with the plugin's id, such as
 *                         {@code dev.jasper.history.scope}; the host rejects any other prefix
 * @param label            the tab text, such as "History"
 * @param description      one line, shown as the tab's tooltip
 * @param placeholder      the query field's hint while this scope's tab is open
 * @param verbs            one to three verbs with distinct ids, bound to Enter, Cmd/Ctrl+Enter, Shift+Enter
 * @param monospaceRows    whether rows are painted in the terminal font (commands, paths)
 * @param icon             the tab icon
 * @param shortcutActionId one of the plugin's own actions: while the palette is open, that action's
 *                         shortcut switches to or dismisses this scope's tab instead of running the handler
 * @param inAll            whether the All tab searches this scope too (since 0.8.0)
 */
public record ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                        boolean monospaceRows, Optional<Icon> icon, Optional<String> shortcutActionId, boolean inAll) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every part and copies the verbs. */
    public ScopeSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches()) throw new IllegalArgumentException("Not a namespaced scope id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A scope needs a label");
        description = description == null ? "" : description;
        if (placeholder == null || placeholder.isBlank()) throw new IllegalArgumentException("A scope needs a placeholder");
        verbs = List.copyOf(Objects.requireNonNull(verbs, "verbs"));
        if (verbs.isEmpty() || verbs.size() > 3) throw new IllegalArgumentException("A scope has one to three verbs");
        Set<String> ids = new HashSet<>();
        for (PaletteVerb verb : verbs) if (!ids.add(verb.id())) throw new IllegalArgumentException("Duplicate verb id: " + verb.id());
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(shortcutActionId, "shortcutActionId");
    }

    /**
     * The minimum: no description, icon or shortcut, proportional rows, searched by the All tab.
     *
     * @param id          the scope id
     * @param label       the tab text
     * @param placeholder the query hint
     * @param verbs       the verbs
     * @return the spec
     */
    public static ScopeSpec of(String id, String label, String placeholder, List<PaletteVerb> verbs) {
        return new ScopeSpec(id, label, "", placeholder, verbs, false, Optional.empty(), Optional.empty(), true);
    }

    /**
     * Derives a value.
     *
     * @param value the tooltip line
     * @return a copy with that description
     */
    public ScopeSpec withDescription(String value) { return new ScopeSpec(id, label, value, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll); }

    /**
     * Derives a value.
     *
     * @param value whether rows use the terminal font
     * @return a copy with that setting
     */
    public ScopeSpec withMonospaceRows(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, value, icon, shortcutActionId, inAll); }

    /**
     * Derives a value.
     *
     * @param value the tab icon, or null for none
     * @return a copy with that icon
     */
    public ScopeSpec withIcon(Icon value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, Optional.ofNullable(value), shortcutActionId, inAll); }

    /**
     * Derives a value.
     *
     * @param value the plugin's action whose shortcut reaches this scope, or null for none
     * @return a copy naming it
     */
    public ScopeSpec withShortcutActionId(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, Optional.ofNullable(value), inAll); }

    /**
     * Derives a value. A scope whose rows should not appear beside others, such as secrets, opts out.
     *
     * @param value whether the All tab searches this scope
     * @return a copy with that setting
     * @since 0.8.0
     */
    public ScopeSpec withInAll(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, value); }
}
