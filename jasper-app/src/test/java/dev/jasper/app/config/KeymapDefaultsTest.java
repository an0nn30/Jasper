package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The approved defaults (spec 2026-09-30-keymap-defaults-design.md, Defaults) for the built-in actions plan K1
 * covers; the comment on each row is the spec's row number, or the spec that set it since. Find, Find Next, Find
 * Previous, Copy and Paste stay Global until plan K3.
 */
class KeymapDefaultsTest {
    private record Row(String id, KeyContext context, String macOs, String other) { }

    private static final List<Row> ROWS = List.of(
        new Row("new_terminal_window", KeyContext.GLOBAL, "cmd+n", "ctrl+shift+n"),            // 1, workspace tabs spec 11
        new Row("close_window", KeyContext.GLOBAL, "cmd+shift+w", "none"),                    // workspace tabs spec 11
        new Row("move_tab_to_new_window", KeyContext.GLOBAL, "none", "none"),                 // tabs and panes spec 10
        new Row("move_to_main_window", KeyContext.GLOBAL, "none", "none"),
        new Row("quit", KeyContext.GLOBAL, "cmd+q", "ctrl+alt+shift+q"),                       // 3
        new Row("open_settings", KeyContext.GLOBAL, "cmd+,", "ctrl+shift+,"),                  // 4
        new Row("open_file", KeyContext.GLOBAL, "none", "none"),  // Enterprise spec 13: File › Open File…, unbound
        new Row("reload_config", KeyContext.GLOBAL, "none", "none"),                           // 5
        new Row("command_palette", KeyContext.GLOBAL, "cmd+shift+p", "ctrl+shift+p"),          // 14
        new Row("new_tab", KeyContext.GLOBAL, "cmd+t", "ctrl+shift+t"),                        // 22; parent tabs spec 8: a parent tab
        new Row("new_tab_in_pane", KeyContext.GLOBAL, "none", "none"),                         // parent tabs spec 8
        new Row("close_tab", KeyContext.GLOBAL, "cmd+w", "ctrl+shift+w"),                      // 23, workspace tabs spec 11
        new Row("close_pane", KeyContext.GLOBAL, "alt+cmd+w", "ctrl+alt+shift+w"),             // parent tabs spec 8
        new Row("close_parent_tab", KeyContext.GLOBAL, "none", "none"),                        // parent tabs spec 8
        new Row("next_parent_tab", KeyContext.GLOBAL, "cmd+shift+]", "ctrl+pagedown"),         // parent tabs spec 8
        new Row("previous_parent_tab", KeyContext.GLOBAL, "cmd+shift+[", "ctrl+pageup"),
        new Row("select_parent_tab_1", KeyContext.GLOBAL, "cmd+1", "ctrl+1"),                  // parent tabs spec 8; plan 7a Spec issues 1
        new Row("select_parent_tab_2", KeyContext.GLOBAL, "cmd+2", "ctrl+2"),
        new Row("select_parent_tab_3", KeyContext.GLOBAL, "cmd+3", "ctrl+3"),
        new Row("select_parent_tab_4", KeyContext.GLOBAL, "cmd+4", "ctrl+4"),
        new Row("select_parent_tab_5", KeyContext.GLOBAL, "cmd+5", "ctrl+5"),
        new Row("select_parent_tab_6", KeyContext.GLOBAL, "cmd+6", "ctrl+6"),
        new Row("select_parent_tab_7", KeyContext.GLOBAL, "cmd+7", "ctrl+7"),
        new Row("select_parent_tab_8", KeyContext.GLOBAL, "cmd+8", "ctrl+8"),
        new Row("select_parent_tab_9", KeyContext.GLOBAL, "cmd+9", "ctrl+9"),
        new Row("next_tab", KeyContext.GLOBAL, "ctrl+tab", "ctrl+tab"),                        // 24; parent tabs spec 8
        new Row("previous_tab", KeyContext.GLOBAL, "ctrl+shift+tab", "ctrl+shift+tab"),        // 25
        new Row("select_tab_1", KeyContext.GLOBAL, "none", "none"),                            // 26; the digits select parent tabs now
        new Row("select_tab_2", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_3", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_4", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_5", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_6", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_7", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_8", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_9", KeyContext.GLOBAL, "none", "none"),                            // 27, Select Last Tab in K2
        new Row("rename_tab", KeyContext.GLOBAL, "cmd+i", "ctrl+shift+i"),                     // 28
        new Row("split_right", KeyContext.GLOBAL, "cmd+d", "ctrl+shift+d"),                    // 32
        new Row("split_down", KeyContext.GLOBAL, "cmd+shift+d", "ctrl+alt+shift+d"),           // 33
        new Row("maximize_pane", KeyContext.GLOBAL, "cmd+shift+enter", "ctrl+alt+shift+enter"), // 35, parent tabs spec 8
        new Row("toggle_zen_mode", KeyContext.GLOBAL, "none", "none"),                       // Zen follow-up
        new Row("focus_pane_left", KeyContext.GLOBAL, "cmd+alt+left", "ctrl+alt+shift+left"),  // 36, parent tabs spec 8
        new Row("focus_pane_right", KeyContext.GLOBAL, "cmd+alt+right", "ctrl+alt+shift+right"),
        new Row("focus_pane_up", KeyContext.GLOBAL, "cmd+alt+up", "ctrl+alt+shift+up"),
        new Row("focus_pane_down", KeyContext.GLOBAL, "cmd+alt+down", "ctrl+alt+shift+down"),
        new Row("copy", KeyContext.GLOBAL, "cmd+c", "ctrl+shift+c"),                           // 42
        new Row("paste", KeyContext.GLOBAL, "cmd+v", "ctrl+shift+v"),                          // 43
        new Row("clear_scrollback", KeyContext.TERMINAL, "cmd+k", "ctrl+shift+k"),             // 45
        new Row("clear_terminal", KeyContext.TERMINAL, "none", "none"),                       // Clear Terminal follow-up
        new Row("find", KeyContext.GLOBAL, "cmd+f", "ctrl+shift+f"),                           // 47
        new Row("find_next", KeyContext.GLOBAL, "cmd+g", "ctrl+shift+g"),                      // 48
        new Row("find_previous", KeyContext.GLOBAL, "cmd+shift+g", "ctrl+alt+shift+g"),        // 49
        new Row("previous_prompt", KeyContext.TERMINAL, "cmd+up", "ctrl+shift+up"),            // 50
        new Row("next_prompt", KeyContext.TERMINAL, "cmd+down", "ctrl+shift+down"),            // 51
        new Row("font_bigger", KeyContext.GLOBAL, "cmd+=", "ctrl+shift+="),                    // 55
        new Row("font_smaller", KeyContext.GLOBAL, "cmd+-", "ctrl+shift+-"),
        new Row("font_reset", KeyContext.GLOBAL, "cmd+0", "ctrl+shift+0"));

    @Test void everyBuiltInActionHasItsApprovedDefaultsAndContext() {
        Set<String> seen = new HashSet<>();
        for (Row row : ROWS) {
            ActionId action = ActionId.forId(row.id()).orElseThrow(() -> new AssertionError("no built-in " + row.id()));
            assertThat(seen.add(row.id())).as("listed once: %s", row.id()).isTrue();
            assertThat(action.context()).as("context of %s", row.id()).isEqualTo(row.context());
            assertThat(KeyBindings.parse(action.defaultBinding(true), true)).as("macOS default of %s", row.id())
                .isEqualTo(KeyBindings.parse(row.macOs(), true));
            assertThat(KeyBindings.parse(action.defaultBinding(false), false)).as("Linux/Windows default of %s", row.id())
                .isEqualTo(KeyBindings.parse(row.other(), false));
        }
        assertThat(seen).as("every built-in action is in the table").hasSize(ActionId.values().length);
    }

    @Test void bothKeymapsBuildAndBindEveryDefault() {
        for (boolean mac : new boolean[]{true, false}) {
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (ActionId action : ActionId.values())
                assertThat(bindings.strokeFor(action)).as("%s on %s", action, mac ? "macOS" : "Linux")
                    .isEqualTo(KeyBindings.parse(action.defaultBinding(mac), mac));
        }
    }

    /** Every macOS default uses Cmd, but Next and Previous Tab's Ctrl+Tab, as macOS's Terminal and Safari have it. */
    @Test void macDefaultsUseCmdOrCtrlTabAndLeaveMacOsItsKeys() {
        Set<KeyStroke> system = new HashSet<>();
        for (String key : List.of("cmd+h", "alt+cmd+h", "cmd+space", "alt+cmd+d", "ctrl+cmd+d", "ctrl+cmd+q",
                "shift+cmd+/", "shift+cmd+3", "shift+cmd+4", "shift+cmd+5"))
            system.add(KeyBindings.parse(key, true).orElseThrow());
        Set<KeyStroke> tabs = Set.of(KeyBindings.parse("ctrl+tab", true).orElseThrow(), KeyBindings.parse("ctrl+shift+tab", true).orElseThrow());
        for (ActionId action : ActionId.values()) KeyBindings.parse(action.defaultBinding(true), true).ifPresent(stroke -> {
            if (!tabs.contains(stroke)) assertThat(stroke.getModifiers() & InputEvent.META_DOWN_MASK).as("%s uses Cmd", action).isNotZero();
            assertThat(system).as("%s leaves macOS its keys", action).doesNotContain(stroke);
        });
    }

    /** Off macOS a default is Ctrl+Shift, Ctrl and a digit, Ctrl+Tab, or Ctrl+PageUp and PageDown, as GNOME Terminal's tabs. */
    @Test void linuxDefaultsLeaveTheShellItsKeys() {
        int mask = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
        for (ActionId action : ActionId.values()) KeyBindings.parse(action.defaultBinding(false), false).ifPresent(stroke -> {
            int modifiers = stroke.getModifiers() & mask, key = stroke.getKeyCode();
            boolean ctrlShift = (modifiers & InputEvent.CTRL_DOWN_MASK) != 0 && (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
            boolean ctrlDigit = modifiers == InputEvent.CTRL_DOWN_MASK && key >= KeyEvent.VK_1 && key <= KeyEvent.VK_9;
            boolean ctrlTab = key == KeyEvent.VK_TAB && (modifiers & ~InputEvent.SHIFT_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK;
            boolean ctrlPage = modifiers == InputEvent.CTRL_DOWN_MASK && (key == KeyEvent.VK_PAGE_UP || key == KeyEvent.VK_PAGE_DOWN);
            assertThat(ctrlShift || ctrlDigit || ctrlTab || ctrlPage).as("%s: %s", action, KeyBindings.format(stroke, false)).isTrue();
            assertThat(modifiers & InputEvent.META_DOWN_MASK).as("%s avoids the Super key", action).isZero();
        });
    }

    /**
     * Workspace plan 7a Spec issues 1: the tool windows keep their digits, Alt+1 to 9 off macOS (IntelliJ's; Remote's SSH
     * hosts, SFTP and Transfers) and Cmd+Alt+1 to 9 on macOS; no built-in default takes one.
     */
    @Test void noBuiltInDefaultTakesTheToolWindowsDigits() {
        for (boolean mac : new boolean[]{true, false}) {
            int toolWindows = mac ? InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (int key = KeyEvent.VK_1; key <= KeyEvent.VK_9; key++)
                assertThat(bindings.actionFor(KeyStroke.getKeyStroke(key, toolWindows)))
                    .as("digit %d on %s", key - KeyEvent.VK_0, mac ? "macOS" : "Linux").isEmpty();
        }
    }
}
