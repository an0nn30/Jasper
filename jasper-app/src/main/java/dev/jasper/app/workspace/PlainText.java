package dev.jasper.app.workspace;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JToolTip;

/**
 * Menus, items, buttons and tooltips that show text from a plugin, a program (a tab's title) or the user as typed, a
 * text starting with {@code <html>} included. Swing builds a text's HTML view when the component's UI is installed (at
 * construction) and when its text, font or foreground changes, and reads {@code html.disable} only then; so each of
 * these takes the property before its first text or Action. A tooltip is a component of its own and does not inherit
 * the property from the component it describes.
 */
final class PlainText {
    private PlainText() {}

    /** Sets {@code html.disable} on {@code component}, which has no text yet (a tooltip from {@code createToolTip}). */
    static <T extends JComponent> T plain(T component) {
        component.putClientProperty("html.disable", true);
        return component;
    }

    static JMenu menu(String title) {
        var menu = plain(new JMenu());
        menu.setText(title);
        return menu;
    }

    static JMenuItem item(String title) {
        var item = plain(new JMenuItem());
        item.setText(title);
        return item;
    }

    /** An item for {@code action}, titled with its name. */
    static JMenuItem item(Action action) {
        var item = plain(new JMenuItem());
        item.setAction(action);
        return item;
    }

    static JCheckBoxMenuItem checkBox(String title, Icon icon, boolean selected) {
        var item = plain(new JCheckBoxMenuItem());
        item.setText(title);
        item.setIcon(icon);
        item.setSelected(selected);
        return item;
    }

    /** A check box item for {@code action}, titled with its name and following its {@code SELECTED_KEY}. */
    static JCheckBoxMenuItem checkBox(Action action) {
        var item = plain(new JCheckBoxMenuItem());
        item.setAction(action);
        return item;
    }

    /** An empty button whose text and tooltip are plain. */
    static JButton button() {
        return plain(new JButton() {
            @Override public JToolTip createToolTip() { return plain(super.createToolTip()); }
        });
    }

    /** An empty toggle button whose text and tooltip are plain. */
    static JToggleButton toggle() {
        return plain(new JToggleButton() {
            @Override public JToolTip createToolTip() { return plain(super.createToolTip()); }
        });
    }
}
