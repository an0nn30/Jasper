package dev.jasper.app.commands;

import java.util.Optional;

/**
 * The built-in application action catalog; plugins contribute further actions by namespaced id. Each action has an
 * explicit default for macOS and one for Linux and Windows, both in the configuration syntax ("none" for no default;
 * the second names ctrl, alt and shift literally), and a {@link KeyContext}.
 */
public enum ActionId {
    NEW_TAB("new_tab", "New Tab", "cmd+t", "ctrl+shift+t"),
    CLOSE_PANE("close_pane", "Close Pane", "cmd+w", "ctrl+shift+w"),
    CLOSE_TAB("close_tab", "Close Tab", "alt+cmd+w", "ctrl+alt+shift+w"),
    NEW_TERMINAL_WINDOW("new_terminal_window", "New Terminal Window", "cmd+n", "ctrl+shift+n"),
    CLOSE_WINDOW("close_window", "Close Window", "cmd+shift+w", "none"),
    MOVE_TAB_TO_NEW_WINDOW("move_tab_to_new_window", "Move Tab to New Window", "none", "none"),
    MOVE_TO_MAIN_WINDOW("move_to_main_window", "Move to Main Window", "none", "none"),
    OPEN_FILE("open_file", "Open File…", "none", "none"),
    SPLIT_RIGHT("split_right", "Split Right", "cmd+d", "ctrl+shift+d"),
    SPLIT_DOWN("split_down", "Split Down", "cmd+shift+d", "ctrl+alt+shift+d"),
    MAXIMIZE_PANE("maximize_pane", "Maximize Pane", "cmd+shift+enter", "ctrl+alt+shift+enter"),
    MOVE_PANE_TO_NEW_TAB("move_pane_to_new_tab", "Move Pane to New Tab", "alt+cmd+t", "ctrl+alt+shift+t"),
    TOGGLE_ZEN_MODE("toggle_zen_mode", "Zen", "none", "none"),
    FOCUS_PANE_LEFT("focus_pane_left", "Focus Pane Left", "cmd+alt+left", "ctrl+alt+shift+left"),
    FOCUS_PANE_RIGHT("focus_pane_right", "Focus Pane Right", "cmd+alt+right", "ctrl+alt+shift+right"),
    FOCUS_PANE_UP("focus_pane_up", "Focus Pane Up", "cmd+alt+up", "ctrl+alt+shift+up"),
    FOCUS_PANE_DOWN("focus_pane_down", "Focus Pane Down", "cmd+alt+down", "ctrl+alt+shift+down"),
    NEXT_TAB("next_tab", "Next Tab", "cmd+shift+]", "ctrl+pagedown"),
    PREVIOUS_TAB("previous_tab", "Previous Tab", "cmd+shift+[", "ctrl+pageup"),
    SELECT_TAB_1("select_tab_1", "Select Tab 1", "cmd+1", "ctrl+1"),
    SELECT_TAB_2("select_tab_2", "Select Tab 2", "cmd+2", "ctrl+2"),
    SELECT_TAB_3("select_tab_3", "Select Tab 3", "cmd+3", "ctrl+3"),
    SELECT_TAB_4("select_tab_4", "Select Tab 4", "cmd+4", "ctrl+4"),
    SELECT_TAB_5("select_tab_5", "Select Tab 5", "cmd+5", "ctrl+5"),
    SELECT_TAB_6("select_tab_6", "Select Tab 6", "cmd+6", "ctrl+6"),
    SELECT_TAB_7("select_tab_7", "Select Tab 7", "cmd+7", "ctrl+7"),
    SELECT_TAB_8("select_tab_8", "Select Tab 8", "cmd+8", "ctrl+8"),
    SELECT_TAB_9("select_tab_9", "Select Tab 9", "cmd+9", "ctrl+9"),
    RENAME_TAB("rename_tab", "Rename Tab", "cmd+i", "ctrl+shift+i"),
    FIND("find", "Find", "cmd+f", "ctrl+shift+f"),
    FIND_NEXT("find_next", "Find Next", "cmd+g", "ctrl+shift+g"),
    FIND_PREVIOUS("find_previous", "Find Previous", "cmd+shift+g", "ctrl+alt+shift+g"),
    PREVIOUS_PROMPT("previous_prompt", "Previous Prompt", "cmd+up", "ctrl+shift+up", KeyContext.TERMINAL),
    NEXT_PROMPT("next_prompt", "Next Prompt", "cmd+down", "ctrl+shift+down", KeyContext.TERMINAL),
    COPY("copy", "Copy", "cmd+c", "ctrl+shift+c"),
    PASTE("paste", "Paste", "cmd+v", "ctrl+shift+v"),
    COMMAND_PALETTE("command_palette", "Command Palette", "cmd+shift+p", "ctrl+shift+p"),
    CLEAR_SCROLLBACK("clear_scrollback", "Clear Scrollback", "cmd+k", "ctrl+shift+k", KeyContext.TERMINAL),
    CLEAR_TERMINAL("clear_terminal", "Clear Terminal", "none", "none", KeyContext.TERMINAL),
    FONT_BIGGER("font_bigger", "Increase Font Size", "cmd+=", "ctrl+shift+="),
    FONT_SMALLER("font_smaller", "Decrease Font Size", "cmd+-", "ctrl+shift+-"),
    FONT_RESET("font_reset", "Reset Font Size", "cmd+0", "ctrl+shift+0"),
    OPEN_SETTINGS("open_settings", "Settings", "cmd+,", "ctrl+shift+,"),
    RELOAD_CONFIG("reload_config", "Reload Config", "none", "none"),
    QUIT("quit", "Quit", "cmd+q", "ctrl+alt+shift+q");

    private final String id;
    private final String label;
    private final String macDefault;
    private final String otherDefault;
    private final KeyContext context;

    ActionId(String id, String label, String macDefault, String otherDefault) {
        this(id, label, macDefault, otherDefault, KeyContext.GLOBAL);
    }

    ActionId(String id, String label, String macDefault, String otherDefault, KeyContext context) {
        this.id = id;
        this.label = label;
        this.macDefault = macDefault;
        this.otherDefault = otherDefault;
        this.context = context;
    }

    public String id() {
        return id;
    }

    /** The built-in action with this configuration id, if any; contributed actions are not in this catalog. */
    public static Optional<ActionId> forId(String id) {
        for (ActionId action : values()) if (action.id.equals(id)) return Optional.of(action);
        return Optional.empty();
    }

    public String label() {
        return label;
    }

    /** The default shortcut on this platform as written; "none" when the action has none there. */
    public String defaultBinding(boolean macOs) {
        return macOs ? macDefault : otherDefault;
    }

    /** Where the action's shortcut applies. */
    public KeyContext context() {
        return context;
    }
}
