package dev.moray.app;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class TabShortcutsTest {
    @Test void platformDefaultsDispatchRealSelectionWrapAndIgnoreMissingTabs() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), KeyBindings.defaults(mac));
            try {
                int modifier = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTab(); owner.newTab(HOME); var second = owner.currentTab();
                owner.selectTab(first);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifier))).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                    modifier | InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET,
                    modifier | InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, modifier))).isTrue();
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

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
