package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A plugin's split item (tabs and panes spec 8 and 11): what a new pane can hold besides a terminal. Jasper lists it in
 * every Split list and registers two actions for it, which can be bound like any action and have no default shortcut:
 * {@link #rightAction()}, titled "Split Right with" and the label, and {@link #downAction()}, titled "Split Down with"
 * and the label. The Split lists show it as the label followed by "Right" and "Down". An item whose actions ask
 * something before they open the pane, as a file chooser does ({@link #asking()}), ends those entries and its actions'
 * titles with an ellipsis, as menus mark a command that asks for more first (pane flexibility spec 3).
 *
 * @param id namespaced id that starts with the plugin's id and a dot, its last part naming the item, for example
 *           {@code dev.example.notes.notes}; at most 116 characters, so that its actions' ids are action ids
 * @param label non-blank name the Split lists and the actions' titles show, for example {@code Notes}
 * @param icon icon for the palette, a kit name or registered plugin icon
 * @param asks whether its actions ask something before they open the pane, such as which file to show: its entries and
 *             its actions' titles then end with an ellipsis
 * @since 0.21.0
 */
public record SplitItemSpec(String id, String label, Optional<String> icon, boolean asks) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and the label. */
    public SplitItemSpec {
        if (id == null || id.length() > 116 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced split item id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A split item needs a label");
        Objects.requireNonNull(icon, "icon");
    }

    /**
     * A split item with only an id and a label.
     *
     * @param id namespaced id
     * @param label non-blank label
     * @return the spec
     */
    public static SplitItemSpec of(String id, String label) {
        return new SplitItemSpec(id, label, Optional.empty(), false);
    }

    /**
     * A copy with an icon.
     *
     * @param value the icon, or null for none
     * @return the copy
     */
    public SplitItemSpec withIcon(String value) {
        return new SplitItemSpec(id, label, Optional.ofNullable(value), asks);
    }

    /**
     * A copy whose actions ask something before they open the pane, such as which file to show.
     *
     * @return the copy
     */
    public SplitItemSpec asking() {
        return new SplitItemSpec(id, label, icon, true);
    }

    /**
     * The action that opens the item in a new pane to the right of the focused pane: the id's namespace, then
     * {@code split_right_} and its last part, as {@code dev.example.notes.split_right_notes}.
     *
     * @return the action's id
     */
    public String rightAction() {
        return action("split_right_");
    }

    /**
     * The action that opens the item in a new pane below the focused pane, as {@code dev.example.notes.split_down_notes}.
     *
     * @return the action's id
     */
    public String downAction() {
        return action("split_down_");
    }

    private String action(String prefix) {
        int dot = id.lastIndexOf('.');
        return id.substring(0, dot + 1) + prefix + id.substring(dot + 1);
    }
}
