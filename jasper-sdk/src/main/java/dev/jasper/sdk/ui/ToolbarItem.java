package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;

/** Something a plugin places in the main toolbar. The application draws it in the toolbar's own style. */
public sealed interface ToolbarItem permits ToolbarItem.Button, ToolbarItem.Dropdown, ToolbarItem.View {
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
    record Dropdown(String icon, String title, List<String> actionIds) implements ToolbarItem {
        /** Validates and copies. */
        public Dropdown {
            Objects.requireNonNull(icon, "icon");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A toolbar menu needs a title");
            actionIds = List.copyOf(Objects.requireNonNull(actionIds, "actionIds"));
            if (actionIds.isEmpty()) throw new IllegalArgumentException("A toolbar menu needs at least one action");
        }
    }

    /**
     * A small group of the plugin's own controls, one per window, such as a session choice with a Connect button. The
     * factory runs when a window opens, never for a window nobody sees, and the view closes with its window or with this
     * registration. Its root is an INLINE {@link Nodes.Stack} of LABEL {@link Nodes.Text}s, DROPDOWN, SEARCHABLE or
     * PICKER {@link Nodes.Choice}s, at most one PRIMARY {@link Nodes.Button} and {@link Nodes.MenuButton}s: the host
     * refuses any other tree, naming the node, when it mounts the view and on every {@link UiView#update}. A view never
     * moves into the overflow menu: it hides while the toolbar is too narrow for it.
     *
     * @param factory builds one window's view
     * @since 0.18.0
     */
    record View(ToolbarViewFactory factory) implements ToolbarItem {
        /** Rejects a null factory. */
        public View { Objects.requireNonNull(factory, "factory"); }
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
    static ToolbarItem menu(String icon, String title, List<String> actionIds) { return new Dropdown(icon, title, actionIds); }

    /**
     * A group of the plugin's own controls, one per window.
     *
     * @param factory builds one window's view
     * @return the item
     * @since 0.18.0
     */
    static ToolbarItem view(ToolbarViewFactory factory) { return new View(factory); }
}
