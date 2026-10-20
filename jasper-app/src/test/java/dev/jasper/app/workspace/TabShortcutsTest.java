package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.layout.ParentTabId;
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
    /** Parent tabs spec 8: the digits and the bracket keys (Ctrl+PageUp/PageDown off macOS) switch parent tabs, Ctrl+Tab tabs. */
    @Test void platformDefaultsSwitchParentTabsAndTabsWithinAPane() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var keys = KeyBindings.defaults(mac);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    ThemeTestSupport.controller(true), keys);
            try {
                int primary = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                KeyStroke nextParent = keys.strokeFor(ActionId.NEXT_PARENT_TAB).orElseThrow();
                KeyStroke previousParent = keys.strokeFor(ActionId.PREVIOUS_PARENT_TAB).orElseThrow();
                assertThat(nextParent).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK));
                assertThat(previousParent).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK));
                KeyStroke next = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                KeyStroke previous = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
                assertThat(keys.strokeFor(ActionId.NEXT_TAB)).contains(next);
                assertThat(keys.strokeFor(ActionId.PREVIOUS_TAB)).contains(previous);
                assertThat(keys.strokeFor(ActionId.SELECT_TAB_1)).as("the digits select parent tabs").isEmpty();
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTerminal(); owner.newTab(HOME); var stacked = owner.currentTerminal();
                var other = owner.newParentTab(HOME);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isTrue();
                assertThat(owner.currentTerminal()).as("the first parent tab, as it was left").isSameAs(stacked);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTerminal()).as("Next Tab wraps within the pane").isSameAs(first);
                assertThat(root.activate(previous)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(stacked);
                assertThat(root.activate(nextParent)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(other);
                assertThat(root.activate(nextParent)).as("Next Parent Tab wraps").isTrue();
                assertThat(owner.currentTerminal()).isSameAs(stacked);
                assertThat(root.activate(previousParent)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(other);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, primary)))
                    .as("no ninth parent tab: the key reaches the focused component").isFalse();
                assertThat(owner.action(ActionId.SELECT_PARENT_TAB_2).getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_2, primary));
                owner.close();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isFalse();
            } finally { owner.close(); }
        });
    }

    /**
     * Review Focus 2: in a plugin's view the window's tab keys still run first: Ctrl+Tab, on both platforms now, before
     * focus traversal, and Next Parent Tab before the view's own bindings, such as a scroll pane's Ctrl+PageDown.
     */
    @Test void tabAndParentTabKeysRunBeforeAPluginViewsOwnBindings() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            try (var owner = CommandPaletteShortcutsTest.owner(mac)) {
                var model = new Contributions(); owner.connectContributions(model);
                var area = new JTextArea();
                model.tabs().open("dev.x", "a", "A", owner.id(), new JScrollPane(area));
                ParentTabId first = owner.activeParentTab();
                owner.newParentTab(HOME);
                owner.selectParentTab(first);
                CommandPaletteShortcutsTest.install(owner);
                assertThat(owner.selectedContent()).as("the plugin's view shows").isNotNull();
                KeyEvent tab = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(tab)).as("Ctrl+Tab").isTrue();
                assertThat(owner.selectedContent()).as("Next Tab: the terminal beside it in the pane").isNull();
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));
                owner.selectTab(owner.tabs().get(1));
                KeyStroke next = owner.bindings().strokeFor(ActionId.NEXT_PARENT_TAB).orElseThrow();
                KeyEvent press = PaletteKeyRouterTest.press(area, next.getKeyCode(), next.getModifiers());
                assertThat(manager.send(press)).as("Next Parent Tab").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).isNotEqualTo(first);
                assertThat(manager.send(PaletteKeyRouterTest.release(area, next.getKeyCode()))).as("its release").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_A, 0))).as("the next key types as ever").isFalse();
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }

    @Test void braceOverrideUsesSameDispatchAndKeepsNativeEditingExemption() throws Exception {
        edt(() -> {
            var bindings = KeyBindings.withOverrides(false, Map.of("previous_tab", "alt+{", "next_tab", "alt+}"));
            try (var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    ThemeTestSupport.controller(true), bindings)) {
                var first = owner.currentTerminal(); owner.newTab(HOME);
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET,
                    InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), owner)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(first);
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
                model.tabs().open("dev.x", "a", "A", owner.id(), editor);
                CommandPaletteShortcutsTest.install(owner);
                assertThat(manager.dispatchers).hasSize(1);
                AWTKeyStroke ctrlTab = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(area.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)).contains(ctrlTab);
                assertThat(owner.tabs().indexOf(owner.selectedTab())).as("the content tab").isEqualTo(1);

                KeyEvent press = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(press)).as("Ctrl+Tab press").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.tabs().indexOf(owner.selectedTab())).as("Next Tab wraps to the terminal").isZero();
                KeyEvent typed = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_UNDEFINED, '\t');
                assertThat(manager.send(typed)).as("its typed tail").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB))).as("its release").isTrue();

                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).as("Ctrl+Shift+Tab").isTrue();
                assertThat(owner.tabs().indexOf(owner.selectedTab())).as("Previous Tab").isEqualTo(1);
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));

                for (var focus : java.util.List.<java.awt.Component>of(area, field)) {
                    KeyEvent tab = PaletteKeyRouterTest.press(focus, KeyEvent.VK_TAB, 0);
                    assertThat(manager.send(tab)).as("plain Tab is no Keymap binding").isFalse();
                    assertThat(tab.isConsumed()).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.typed(focus, '\t'))).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.release(focus, KeyEvent.VK_TAB))).isFalse();
                }
                assertThat(owner.tabs().indexOf(owner.selectedTab())).isEqualTo(1);
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }

    @Test void theTabMenusCloseTabShowsTheCloseTabBinding() throws Exception {
        edt(() -> {
            var keys = KeyBindings.defaults(true);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {}, ThemeTestSupport.controller(true), keys);
            try {
                var menu = owner.tabMenu(owner.selectedTab());
                var close = java.util.Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem item && item.getText().equals("Close Tab")).map(JMenuItem.class::cast).findFirst().orElseThrow();
                assertThat(close.getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_TAB).orElseThrow());
            } finally { owner.close(); }
        });
    }
}
