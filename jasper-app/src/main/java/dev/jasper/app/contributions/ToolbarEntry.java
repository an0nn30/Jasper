package dev.jasper.app.contributions;

import java.util.List;
import javax.swing.Icon;

/** One contributed toolbar control. */
public sealed interface ToolbarEntry permits ToolbarEntry.Button, ToolbarEntry.Dropdown {
    /** A button for one contributed action. */
    record Button(String actionId) implements ToolbarEntry { }

    /** A button that opens a menu of contributed actions. */
    record Dropdown(Icon icon, String title, List<String> actionIds) implements ToolbarEntry {
        public Dropdown { actionIds = List.copyOf(actionIds); }
    }
}
