package dev.jasper.app;

/** The complete Phase 1 application action catalog. */
enum ActionId {
    NEW_TAB("new_tab", "New Tab", "cmd+t"),
    CLOSE_TAB("close_tab", "Close Tab", "cmd+w"),
    NEW_WINDOW("new_window", "New Window", "cmd+n"),
    SPLIT_RIGHT("split_right", "Split Right", "cmd+d"),
    SPLIT_DOWN("split_down", "Split Down", "cmd+shift+d"),
    CLOSE_PANE("close_pane", "Close Pane", "cmd+shift+w"),
    ZOOM_PANE("zoom_pane", "Zoom Pane", "cmd+shift+enter"),
    FOCUS_PANE_LEFT("focus_pane_left", "Focus Pane Left", "cmd+alt+left"),
    FOCUS_PANE_RIGHT("focus_pane_right", "Focus Pane Right", "cmd+alt+right"),
    FOCUS_PANE_UP("focus_pane_up", "Focus Pane Up", "cmd+alt+up"),
    FOCUS_PANE_DOWN("focus_pane_down", "Focus Pane Down", "cmd+alt+down"),
    NEXT_TAB("next_tab", "Next Tab", "cmd+shift+]"),
    PREVIOUS_TAB("previous_tab", "Previous Tab", "cmd+shift+["),
    SELECT_TAB_1("select_tab_1", "Select Tab 1", "cmd+1"),
    SELECT_TAB_2("select_tab_2", "Select Tab 2", "cmd+2"),
    SELECT_TAB_3("select_tab_3", "Select Tab 3", "cmd+3"),
    SELECT_TAB_4("select_tab_4", "Select Tab 4", "cmd+4"),
    SELECT_TAB_5("select_tab_5", "Select Tab 5", "cmd+5"),
    SELECT_TAB_6("select_tab_6", "Select Tab 6", "cmd+6"),
    SELECT_TAB_7("select_tab_7", "Select Tab 7", "cmd+7"),
    SELECT_TAB_8("select_tab_8", "Select Tab 8", "cmd+8"),
    SELECT_TAB_9("select_tab_9", "Select Tab 9", "cmd+9"),
    RENAME_TAB("rename_tab", "Rename Tab", "f2"),
    FIND("find", "Find", "cmd+f"),
    FIND_NEXT("find_next", "Find Next", "cmd+g"),
    FIND_PREVIOUS("find_previous", "Find Previous", "cmd+shift+g"),
    PREVIOUS_PROMPT("previous_prompt", "Previous Prompt", "cmd+up"),
    NEXT_PROMPT("next_prompt", "Next Prompt", "cmd+down"),
    COPY("copy", "Copy", "cmd+c"),
    PASTE("paste", "Paste", "cmd+v"),
    COMMAND_PALETTE("command_palette", "Command Palette", "cmd+k"),
    CLEAR_SCROLLBACK("clear_scrollback", "Clear Scrollback", "cmd+shift+k"),
    FONT_BIGGER("font_bigger", "Increase Font Size", "cmd+="),
    FONT_SMALLER("font_smaller", "Decrease Font Size", "cmd+-"),
    FONT_RESET("font_reset", "Reset Font Size", "cmd+0"),
    OPEN_SETTINGS("open_settings", "Settings", "cmd+,"),
    RELOAD_CONFIG("reload_config", "Reload Config", "cmd+shift+r"),
    QUIT("quit", "Quit", "cmd+q");

    private final String id;
    private final String label;
    private final String defaultBinding;

    ActionId(String id, String label, String defaultBinding) {
        this.id = id;
        this.label = label;
        this.defaultBinding = defaultBinding;
    }

    String id() {
        return id;
    }

    String label() {
        return label;
    }

    String defaultBinding(boolean macOs) {
        if (macOs) return defaultBinding;
        return switch (this) {
            case COMMAND_PALETTE -> "ctrl+k";
            case CLEAR_SCROLLBACK -> "ctrl+shift+k";
            case NEXT_TAB -> "ctrl+shift+]";
            case PREVIOUS_TAB -> "ctrl+shift+[";
            case SELECT_TAB_1 -> "ctrl+1";
            case SELECT_TAB_2 -> "ctrl+2";
            case SELECT_TAB_3 -> "ctrl+3";
            case SELECT_TAB_4 -> "ctrl+4";
            case SELECT_TAB_5 -> "ctrl+5";
            case SELECT_TAB_6 -> "ctrl+6";
            case SELECT_TAB_7 -> "ctrl+7";
            case SELECT_TAB_8 -> "ctrl+8";
            case SELECT_TAB_9 -> "ctrl+9";
            default -> defaultBinding;
        };
    }

    String defaultBinding() {
        return defaultBinding;
    }
}
