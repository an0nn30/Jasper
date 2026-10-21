package dev.jasper.sdk.palette;

import dev.jasper.sdk.ui.Nodes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a scope is: identity, wording, verbs and how it is reached. The palette shows either no scope chosen (All),
 * which searches every scope that takes part, or one scope's chip; the user chooses a scope by typing its label and
 * {@code >}, with the scope's shortcut action, from a "More in …" row, or from the Run window's scope menu.
 *
 * @param id               namespaced like an action id and starting with the plugin's id, such as
 *                         {@code dev.jasper.history.scope}; at most 128 characters; the host rejects any other prefix
 * @param label            the scope's name, such as "History": the chip text, the heading of its rows in the
 *                         command palette's All results, and the prefix the user types
 * @param description      one line the Run window shows as its help while this scope is chosen; may be empty
 * @param placeholder      the command palette's query hint while this scope is chosen
 * @param verbs            one to three verbs with distinct ids, bound to Enter, Cmd+Enter (Ctrl+Enter on Linux and
 *                         Windows) and Shift+Enter; at most one is a {@link PaletteVerb.Role#SPLIT} verb, and never the
 *                         first
 * @param monospaceRows    whether rows are data values (commands, paths), which the command palette draws in the data
 *                         face
 * @param icon             a semantic icon name for the scope, checked at registration; neither palette view draws it
 * @param shortcutActionId one of the plugin's own actions: while the palette is open, that action's shortcut chooses
 *                         this scope instead of running the handler (the command palette dismisses when this scope is
 *                         already chosen); the Run window shows its shortcut beside the scope in its menu
 * @param inAll            whether a query with no scope chosen searches this scope too (since 0.8.0)
 * @param inRecents        whether the palette may offer this scope's rows before anything is typed, among the
 *                         recently and most used items; only a scope in All is offered there (since 0.9.2)
 * @param hue              the hue of this scope's row squares where a presentation draws them; NONE takes the plugin's
 *                         manifest {@code icon_hue}, and NEUTRAL without one (since 0.18.0)
 * @param itemNoun         what one row is, as a lowercase singular noun such as "session", which a presentation shows
 *                         capitalised before a row's detail; blank uses the label (since 0.18.0)
 */
public record ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                        boolean monospaceRows, Optional<String> icon, Optional<String> shortcutActionId, boolean inAll,
                        boolean inRecents, Nodes.Hue hue, String itemNoun) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");
    /** The most characters an item noun has. */
    private static final int NOUN_LENGTH = 40;

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
        if (verbs.getFirst().role() == PaletteVerb.Role.SPLIT)
            throw new IllegalArgumentException("A scope's first verb cannot be its split variant: " + verbs.getFirst().id());
        if (verbs.stream().filter(verb -> verb.role() == PaletteVerb.Role.SPLIT).count() > 1)
            throw new IllegalArgumentException("A scope has at most one split verb");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(shortcutActionId, "shortcutActionId");
        Objects.requireNonNull(hue, "hue");
        itemNoun = itemNoun == null || itemNoun.isBlank() ? "" : itemNoun;
        if (!itemNoun.isEmpty() && (!itemNoun.equals(itemNoun.strip()) || !itemNoun.equals(itemNoun.toLowerCase(Locale.ROOT))
                || itemNoun.length() > NOUN_LENGTH || itemNoun.chars().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("An item noun is a lowercase singular noun, such as \"session\": " + itemNoun);
    }

    /**
     * A scope with no hue of its own and the label as the item noun.
     *
     * @param id               the scope id
     * @param label            the scope's name
     * @param description      the Run window's help line
     * @param placeholder      the query hint
     * @param verbs            the verbs
     * @param monospaceRows    whether rows are data values
     * @param icon             the scope's icon name
     * @param shortcutActionId the plugin's action whose shortcut reaches this scope
     * @param inAll            whether a query with no scope chosen searches this scope
     * @param inRecents        whether the palette may offer this scope's rows before anything is typed
     */
    public ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                     boolean monospaceRows, Optional<String> icon, Optional<String> shortcutActionId, boolean inAll, boolean inRecents) {
        this(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, Nodes.Hue.NONE, "");
    }

    /**
     * A scope offered among recent items, with no hue of its own and the label as the item noun.
     *
     * @param id               the scope id
     * @param label            the scope's name
     * @param description      the Run window's help line
     * @param placeholder      the query hint
     * @param verbs            the verbs
     * @param monospaceRows    whether rows are data values
     * @param icon             the scope's icon name
     * @param shortcutActionId the plugin's action whose shortcut reaches this scope
     * @param inAll            whether a query with no scope chosen searches this scope
     */
    public ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                     boolean monospaceRows, Optional<String> icon, Optional<String> shortcutActionId, boolean inAll) {
        this(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, true, Nodes.Hue.NONE, "");
    }

    /**
     * The minimum: no description, icon or shortcut, proportional rows, in All and among recent items, no hue of its
     * own and the label as the item noun.
     *
     * @param id          the scope id
     * @param label       the scope's name
     * @param placeholder the query hint
     * @param verbs       the verbs
     * @return the spec
     */
    public static ScopeSpec of(String id, String label, String placeholder, List<PaletteVerb> verbs) {
        return new ScopeSpec(id, label, "", placeholder, verbs, false, Optional.empty(), Optional.empty(), true, true, Nodes.Hue.NONE, "");
    }

    /**
     * Derives a value.
     *
     * @param value the Run window's help line while this scope is chosen
     * @return a copy with that description
     */
    public ScopeSpec withDescription(String value) { return new ScopeSpec(id, label, value, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value whether rows are data values
     * @return a copy with that setting
     */
    public ScopeSpec withMonospaceRows(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, value, icon, shortcutActionId, inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value the scope's semantic icon name, or null for none
     * @return a copy with that icon
     */
    public ScopeSpec withIcon(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, Optional.ofNullable(value), shortcutActionId, inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value the plugin's action whose shortcut reaches this scope, or null for none
     * @return a copy naming it
     */
    public ScopeSpec withShortcutActionId(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, Optional.ofNullable(value), inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value. A scope whose rows should not appear beside others, such as secrets, opts out; a scope out of
     * All is also never offered among recent items.
     *
     * @param value whether a query with no scope chosen searches this scope
     * @return a copy with that setting
     * @since 0.8.0
     */
    public ScopeSpec withInAll(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, value, inRecents, hue, itemNoun); }

    /**
     * Derives a value. A scope whose rows should appear only when searched for, such as shell history, opts out. Jasper
     * keeps each row run from a scope offered among recent items, by its id and title, in its command history, and
     * offers it again only while searching the scope for that title still returns a row with that id.
     *
     * @param value whether the palette may offer this scope's rows before anything is typed
     * @return a copy with that setting
     * @since 0.9.2
     */
    public ScopeSpec withInRecents(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, value, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value the hue of this scope's row squares; NONE takes the plugin's {@code icon_hue}
     * @return a copy with that hue
     * @since 0.18.0
     */
    public ScopeSpec withHue(Nodes.Hue value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, value, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value what one row is, as a lowercase singular noun such as "session"; blank or null uses the label
     * @return a copy with that noun
     * @since 0.18.0
     */
    public ScopeSpec withItemNoun(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, hue, value); }
}
