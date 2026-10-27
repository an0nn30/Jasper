package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
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

/** Tabs and panes spec 10: the tab keys on each platform, and where they run while a plugin's view has the focus. */
class TabShortcutsTest {
    /** The digits and the bracket keys (Ctrl+PageUp and Ctrl+PageDown off macOS) switch tabs; Ctrl+Tab is no default. */
    @Test void platformDefaultsSwitchTabs() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var keys = KeyBindings.defaults(mac);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {},
                    ThemeTestSupport.controller(true), keys);
            try {
                int primary = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                KeyStroke next = keys.strokeFor(ActionId.NEXT_TAB).orElseThrow();
                KeyStroke previous = keys.strokeFor(ActionId.PREVIOUS_TAB).orElseThrow();
                assertThat(next).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK));
                assertThat(previous).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK));
                assertThat(keys.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)))
                    .as("Ctrl+Tab has no default").isEmpty();
                assertThat(keys.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).isEmpty();
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTerminal();
                owner.invoke(ActionId.NEW_TAB); var second = owner.currentTerminal();
                owner.invoke(ActionId.NEW_TAB); var third = owner.currentTerminal();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isTrue();
                assertThat(owner.currentTerminal()).as("Select Tab 1").isSameAs(first);
                assertThat(root.activate(previous)).isTrue();
                assertThat(owner.currentTerminal()).as("Previous Tab wraps").isSameAs(third);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTerminal()).as("and Next Tab back").isSameAs(first);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(second);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, primary)))
                    .as("no ninth tab: the key reaches the focused component").isFalse();
                assertThat(owner.action(ActionId.SELECT_TAB_2).getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_2, primary));
                owner.close();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isFalse();
            } finally { owner.close(); }
        });
    }

    /**
     * Review Focus 5: in a plugin's view Next and Previous Tab run before the view's own bindings, such as a scroll pane's
     * Ctrl+PageDown (Rulings 16), and Ctrl+Tab, which no action holds, reaches the view.
     */
    @Test void nextAndPreviousTabRunBeforeAPluginViewsOwnBindingsAndCtrlTabReachesTheView() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            try (var owner = CommandPaletteShortcutsTest.owner(mac)) {
                var model = new Contributions(); owner.connectContributions(model);
                var area = new JTextArea();
                Tab view = model.tabs().open("dev.x", "a", "A", owner.id(), new JScrollPane(area));
                owner.invoke(ActionId.NEW_TAB);
                owner.selectTab(view);
                ParentTabId shown = owner.activeParentTab();
                CommandPaletteShortcutsTest.install(owner);
                assertThat(owner.selectedContent()).as("the plugin's view shows").isSameAs(view);
                KeyEvent ctrlTab = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(ctrlTab)).as("Ctrl+Tab is the view's").isFalse();
                assertThat(ctrlTab.isConsumed()).isFalse();
                assertThat(owner.activeParentTab()).isEqualTo(shown);
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));
                KeyStroke next = owner.bindings().strokeFor(ActionId.NEXT_TAB).orElseThrow();
                KeyEvent press = PaletteKeyRouterTest.press(area, next.getKeyCode(), next.getModifiers());
                assertThat(manager.send(press)).as("Next Tab").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).isNotEqualTo(shown);
                assertThat(manager.send(PaletteKeyRouterTest.release(area, next.getKeyCode()))).as("its release").isTrue();
                owner.selectTab(view);
                KeyStroke previous = owner.bindings().strokeFor(ActionId.PREVIOUS_TAB).orElseThrow();
                KeyEvent back = PaletteKeyRouterTest.press(area, previous.getKeyCode(), previous.getModifiers());
                assertThat(manager.send(back)).as("Previous Tab").isTrue();
                assertThat(back.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).isNotEqualTo(shown);
                assertThat(manager.send(PaletteKeyRouterTest.release(area, previous.getKeyCode()))).as("its release").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_A, 0))).as("the next key types as ever").isFalse();
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }

    @Test void braceOverrideUsesSameDispatchAndKeepsNativeEditingExemption() throws Exception {
        edt(() -> {
            var bindings = KeyBindings.withOverrides(false, Map.of("previous_tab", "alt+{", "next_tab", "alt+}"));
            try (var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {},
                    ThemeTestSupport.controller(true), bindings)) {
                var first = owner.currentTerminal(); owner.invoke(ActionId.NEW_TAB);
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

    /** A Ctrl+Tab binding still runs before focus traversal can take the key; plain Tab, no Keymap binding, traverses. */
    @Test void aCtrlTabBindingRunsBeforeFocusTraversalCanTakeIt() throws Exception {
        edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            var keys = KeyBindings.withOverrides(false, Map.of("next_tab", "ctrl+tab", "previous_tab", "ctrl+shift+tab"));
            try (var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {},
                    ThemeTestSupport.controller(true), keys, new CommandHistory(), false)) {
                var model = new Contributions(); owner.connectContributions(model);
                var editor = new JPanel(); var area = new JTextArea(); var field = new JTextField();
                editor.add(area); editor.add(field);
                Tab view = model.tabs().open("dev.x", "a", "A", owner.id(), editor);
                owner.invoke(ActionId.NEW_TAB);
                owner.selectTab(view);
                ParentTabId shown = owner.activeParentTab();
                CommandPaletteShortcutsTest.install(owner);
                assertThat(manager.dispatchers).hasSize(1);
                AWTKeyStroke ctrlTab = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(area.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)).contains(ctrlTab);

                KeyEvent press = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(press)).as("Ctrl+Tab press").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).as("Next Tab").isNotEqualTo(shown);
                KeyEvent typed = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_UNDEFINED, '\t');
                assertThat(manager.send(typed)).as("its typed tail").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB))).as("its release").isTrue();

                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).as("Ctrl+Shift+Tab").isTrue();
                assertThat(owner.activeParentTab()).as("Previous Tab").isEqualTo(shown);
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));

                for (var focus : java.util.List.<java.awt.Component>of(area, field)) {
                    KeyEvent tab = PaletteKeyRouterTest.press(focus, KeyEvent.VK_TAB, 0);
                    assertThat(manager.send(tab)).as("plain Tab is no Keymap binding").isFalse();
                    assertThat(tab.isConsumed()).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.typed(focus, '\t'))).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.release(focus, KeyEvent.VK_TAB))).isFalse();
                }
                assertThat(owner.activeParentTab()).isEqualTo(shown);
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
