package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import java.awt.AWTKeyStroke;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class TabShortcutsTest {
    @Test void platformDefaultsDispatchRealSelectionWrapAndIgnoreMissingTabs() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var keys = KeyBindings.defaults(mac);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), keys);
            try {
                int modifier = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                KeyStroke next = keys.strokeFor(ActionId.NEXT_TAB).orElseThrow();
                KeyStroke previous = keys.strokeFor(ActionId.PREVIOUS_TAB).orElseThrow();
                assertThat(next).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
                assertThat(previous).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTab(); owner.newTab(HOME); var second = owner.currentTab();
                owner.selectTab(first);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifier))).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(root.activate(previous)).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, modifier)))
                    .as("no ninth tab: the key reaches the focused component").isFalse();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                    modifier | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK), owner)).isFalse();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(owner.action(ActionId.SELECT_TAB_2).getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifier));
                owner.close();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifier))).isFalse();
            } finally { owner.close(); }
        });
    }

    @Test void braceOverrideUsesSameDispatchAndKeepsNativeEditingExemption() throws Exception {
        edt(() -> {
            var bindings = KeyBindings.withOverrides(false, Map.of("previous_tab", "alt+{", "next_tab", "alt+}"));
            try (var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), bindings)) {
                var first = owner.currentTab(); owner.newTab(HOME);
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET,
                    InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), owner)).isTrue();
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_C,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), new JTextField())).isFalse();
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), owner)).isFalse();
            }
        });
    }

    @Test void linuxCtrlTabSwitchesTabsBeforeFocusTraversalCanTakeIt() throws Exception {
        edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            try (var owner = CommandPaletteShortcutsTest.owner(false)) {
                var model = new Contributions(); owner.connectContributions(model);
                var editor = new JPanel(); var area = new JTextArea(); var field = new JTextField();
                editor.add(area); editor.add(field);
                model.content().open("dev.x", "a", "A", owner.id(), editor);
                CommandPaletteShortcutsTest.install(owner);
                assertThat(manager.dispatchers).hasSize(1);
                AWTKeyStroke ctrlTab = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(area.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)).contains(ctrlTab);
                assertThat(owner.tabStrip().getSelectedIndex()).as("the content tab").isEqualTo(1);

                KeyEvent press = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(press)).as("Ctrl+Tab press").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.tabStrip().getSelectedIndex()).as("Next Tab wraps to the terminal").isZero();
                KeyEvent typed = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_UNDEFINED, '\t');
                assertThat(manager.send(typed)).as("its typed tail").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB))).as("its release").isTrue();

                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).as("Ctrl+Shift+Tab").isTrue();
                assertThat(owner.tabStrip().getSelectedIndex()).as("Previous Tab").isEqualTo(1);
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));

                for (var focus : java.util.List.<java.awt.Component>of(area, field)) {
                    KeyEvent tab = PaletteKeyRouterTest.press(focus, KeyEvent.VK_TAB, 0);
                    assertThat(manager.send(tab)).as("plain Tab is no Keymap binding").isFalse();
                    assertThat(tab.isConsumed()).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.typed(focus, '\t'))).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.release(focus, KeyEvent.VK_TAB))).isFalse();
                }
                assertThat(owner.tabStrip().getSelectedIndex()).isEqualTo(1);
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
