package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KeyBindingsTest {
    @Test
    void actionCatalogCoversEveryPhaseOneActionAndUsesStableIds() {
        assertThat(EnumSet.allOf(ActionId.class)).containsExactly(
            ActionId.NEW_TAB, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP, ActionId.NEW_TERMINAL_WINDOW, ActionId.CLOSE_WINDOW,
            ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TAB_TO_MAIN_WINDOW, ActionId.OPEN_FILE,
            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_GROUP,
            ActionId.TOGGLE_ZEN_MODE,
            ActionId.FOCUS_GROUP_LEFT, ActionId.FOCUS_GROUP_RIGHT,
            ActionId.FOCUS_GROUP_UP, ActionId.FOCUS_GROUP_DOWN,
            ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB,
            ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3,
            ActionId.SELECT_TAB_4, ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6,
            ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9,
            ActionId.RENAME_TAB, ActionId.FIND, ActionId.FIND_NEXT, ActionId.FIND_PREVIOUS,
            ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT, ActionId.COPY, ActionId.PASTE,
            ActionId.COMMAND_PALETTE, ActionId.CLEAR_SCROLLBACK, ActionId.CLEAR_TERMINAL, ActionId.FONT_BIGGER, ActionId.FONT_SMALLER,
            ActionId.FONT_RESET, ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
        assertThat(ActionId.SPLIT_RIGHT.id()).isEqualTo("split_right");
        assertThat(ActionId.SPLIT_RIGHT.label()).isEqualTo("Split Right");
        assertThat(ActionId.SPLIT_RIGHT.defaultBinding(true)).isEqualTo("cmd+d");
        assertThat(ActionId.SPLIT_RIGHT.defaultBinding(false)).isEqualTo("ctrl+shift+d");
        assertThat(KeyBindings.defaults(true).strokeFor(ActionId.CLEAR_TERMINAL)).isEmpty();
        assertThat(KeyBindings.defaults(false).strokeFor(ActionId.CLEAR_TERMINAL)).isEmpty();
    }

    @Test
    void cmdUsesMetaOnMacAndCtrlShiftElsewhere() {
        KeyStroke macCopy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK);
        KeyStroke otherCopy = KeyStroke.getKeyStroke(
            KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

        assertThat(KeyBindings.defaults(true).strokeFor(ActionId.COPY)).contains(macCopy);
        assertThat(KeyBindings.defaults(false).strokeFor(ActionId.COPY)).contains(otherCopy);
    }

    @Test
    void explicitCmdShiftDefaultsGainAltOnlyOffMacToAvoidCollisions() {
        KeyStroke macSplitDown = KeyStroke.getKeyStroke(
            KeyEvent.VK_D, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        KeyStroke otherSplitDown = KeyStroke.getKeyStroke(
            KeyEvent.VK_D,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK);

        assertThat(KeyBindings.defaults(true).strokeFor(ActionId.SPLIT_DOWN)).contains(macSplitDown);
        assertThat(KeyBindings.defaults(false).strokeFor(ActionId.SPLIT_DOWN)).contains(otherSplitDown);
    }

    @Test
    void everyPlatformDefaultHasOneUniqueActiveStrokePerAction() {
        for (boolean macOs : new boolean[]{true, false}) {
            KeyBindings bindings = KeyBindings.defaults(macOs);
            HashSet<KeyStroke> strokes = new HashSet<>();
            for (ActionId action : ActionId.values()) {
                bindings.strokeFor(action).ifPresent(stroke -> {
                    assertThat(strokes.add(stroke)).as("unique %s binding for %s", macOs, action).isTrue();
                    assertThat(bindings.actionFor(stroke)).contains(action);
                });
            }
        }
    }

    @Test
    void tabDefaultsFollowEachPlatformsTerminals() {
        for (boolean mac : new boolean[]{true, false}) {
            int modifier = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (int number = 1; number <= 9; number++) {
                KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_1 + number - 1, modifier);
                assertThat(bindings.actionFor(stroke)).contains(ActionId.valueOf("SELECT_TAB_" + number));
            }
            KeyStroke previous = mac
                ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
            KeyStroke next = mac
                ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
            assertThat(bindings.actionFor(previous)).contains(ActionId.PREVIOUS_TAB);
            assertThat(bindings.actionFor(next)).contains(ActionId.NEXT_TAB);
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier))).isEmpty();
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                modifier | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK))).isEmpty();
        }
    }

    @Test
    void braceOverridesResolvePhysicalBracketEventsAndDetectEquivalentCollisions() {
        assertThat(KeyBindings.parse("ctrl+{", false)).contains(KeyStroke.getKeyStroke(
            KeyEvent.VK_OPEN_BRACKET, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(KeyBindings.parse("cmd+}", true)).contains(KeyStroke.getKeyStroke(
            KeyEvent.VK_CLOSE_BRACKET, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        var bindings = KeyBindings.withOverrides(false, Map.of("previous_tab", "ctrl+{", "next_tab", "ctrl+}"));
        assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).contains(ActionId.PREVIOUS_TAB);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> KeyBindings.withOverrides(true, Map.of("copy", "cmd+{")))
            .withMessageContaining("copy").withMessageContaining("previous_tab");
        assertThatIllegalArgumentException().isThrownBy(() -> KeyBindings.parse("ctrl+{+]", false));
    }

    @Test
    void noneRemovesAnActionsDefault() {
        KeyBindings bindings = KeyBindings.withOverrides(true, Map.of("copy", "none"));

        assertThat(bindings.strokeFor(ActionId.COPY)).isEmpty();
        assertThat(bindings.actionFor(
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK))).isEmpty();
    }

    @Test
    void overridesAreAppliedTogetherBeforeFinalCollisionValidation() {
        KeyBindings bindings = KeyBindings.withOverrides(true, Map.of(
            "copy", "cmd+v",
            "paste", "none"));

        assertThat(bindings.strokeFor(ActionId.COPY)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK));
        assertThat(bindings.strokeFor(ActionId.PASTE)).isEmpty();
    }

    @Test
    void literalOverridesDoNotGainTheNonMacDefaultCompatibilityModifier() {
        assertThat(KeyBindings.parse("cmd+shift+d", false)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_D,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> KeyBindings.withOverrides(false, Map.of("split_down", "cmd+shift+d")))
            .withMessageContaining("split_right")
            .withMessageContaining("split_down");
    }

    @Test
    void collisionReportsBothActionNamesAndTheKey() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> KeyBindings.withOverrides(true, Map.of("copy", "cmd+v")))
            .withMessageContaining("copy")
            .withMessageContaining("paste")
            .withMessageContaining("cmd+v");
    }

    @Test
    void unknownActionsAndKeysIdentifyTheInvalidInput() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> KeyBindings.withOverrides(true, Map.of("teleport", "cmd+t")))
            .withMessageContaining("teleport");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> KeyBindings.withOverrides(true, Map.of("copy", "cmd+banana")))
            .withMessageContaining("copy")
            .withMessageContaining("cmd+banana");
    }

    @Test
    void parserRecognizesFunctionKeysPunctuationAndArrowsCaseInsensitively() {
        assertThat(KeyBindings.parse("F2", true)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        assertThat(KeyBindings.parse("CMD+SHIFT+]", true)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(KeyBindings.parse("cmd+=", true)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.META_DOWN_MASK));
        assertThat(KeyBindings.parse("cmd+,", true)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.META_DOWN_MASK));
        assertThat(KeyBindings.parse("ctrl+alt+Left", true)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        assertThat(KeyBindings.parse("none", true)).isEmpty();
    }
    @Test
    void highFunctionKeyOverridesResolveActualAwtKeyEvents() {
        KeyBindings bindings = KeyBindings.withOverrides(true, Map.of("new_tab", "F13", "new_terminal_window", "F24"));
        assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_F13, 0))).contains(ActionId.NEW_TAB);
        assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0))).contains(ActionId.NEW_TERMINAL_WINDOW);
    }

    @Test void displayWritesTheMacGlyphsInOrderAndNamesElsewhere() {
        var stroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        assertThat(KeyBindings.display(stroke, true)).isEqualTo("\u21e7\u2318H");
        assertThat(KeyBindings.display(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false)).isEqualTo("Ctrl+Shift+H");
        assertThat(KeyBindings.display(null, true)).isEmpty();
    }
}
