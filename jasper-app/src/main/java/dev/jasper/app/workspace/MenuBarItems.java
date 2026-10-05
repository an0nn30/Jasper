package dev.jasper.app.workspace;

import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

/**
 * Terminal-window menu-bar items. They show their accelerators but never fire them from the keyboard: the root input
 * map is the only key path, so a key it declines (no applicable enabled action where focus is) reaches the focused
 * component instead of the menu bar's copy of the action. Clicks and mnemonics still work. Popup and context menus
 * keep ordinary items, which bind only while they are showing.
 */
final class MenuBarItems {
    private MenuBarItems() {}

    private static void style(JMenuItem item) {
        dev.jasper.ui.swing.NativeControlStyles.menu(item, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
    }

    /** Swing offers a declined window key to the menu bar's items under {@code WHEN_IN_FOCUSED_WINDOW}; they refuse it. */
    private static boolean accelerator(int condition) { return condition == JComponent.WHEN_IN_FOCUSED_WINDOW; }

    static final class Item extends JMenuItem {
        Item(Action action) { super(action); style(this); }

        @Override protected boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
            return !accelerator(condition) && super.processKeyBinding(stroke, event, condition, pressed);
        }
    }

    static final class CheckBox extends JCheckBoxMenuItem {
        CheckBox(String text, boolean selected) { super(text, selected); style(this); }

        @Override protected boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
            return !accelerator(condition) && super.processKeyBinding(stroke, event, condition, pressed);
        }
    }

    static final class Radio extends JRadioButtonMenuItem {
        Radio(Action action) { super(action); style(this); }

        @Override protected boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
            return !accelerator(condition) && super.processKeyBinding(stroke, event, condition, pressed);
        }
    }
}
