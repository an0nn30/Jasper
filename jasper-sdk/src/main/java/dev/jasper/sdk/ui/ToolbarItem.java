package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;
import javax.swing.Icon;

/** Something a plugin places in the main toolbar. The application draws it in the toolbar's own style. */
public sealed interface ToolbarItem permits ToolbarItem.Button, ToolbarItem.Dropdown {
    /**
     * A button for one action, using the action's title and icon.
     *
     * @param actionId an action this plugin registered
     */
    record Button(String actionId) implements ToolbarItem {
        /** Rejects a null id. */
        public Button { Objects.requireNonNull(actionId, "actionId"); }
    }

    /**
     * A button that opens a menu of actions.
     *
     * @param icon the button's icon
     * @param title non-blank label
     * @param actionIds one or more actions this plugin registered; copied
     */
    record Dropdown(Icon icon, String title, List<String> actionIds) implements ToolbarItem {
        /** Validates and copies. */
        public Dropdown {
            Objects.requireNonNull(icon, "icon");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A toolbar menu needs a title");
            actionIds = List.copyOf(Objects.requireNonNull(actionIds, "actionIds"));
            if (actionIds.isEmpty()) throw new IllegalArgumentException("A toolbar menu needs at least one action");
        }
    }

    /**
     * A button for one action.
     *
     * @param actionId an action this plugin registered
     * @return the item
     */
    static ToolbarItem action(String actionId) { return new Button(actionId); }

    /**
     * A button that opens a menu of actions.
     *
     * @param icon the button's icon
     * @param title non-blank label
     * @param actionIds one or more actions this plugin registered
     * @return the item
     */
    static ToolbarItem menu(Icon icon, String title, List<String> actionIds) { return new Dropdown(icon, title, actionIds); }
}
