package dev.jasper.app.workspace;

import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import java.util.function.Consumer;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * A tool window's menu, the same from its stripe button (right-click) and its header's options button: View Mode ›
 * Docked, Float and Window with the current one checked, Move to › the six slots by name with the current one checked,
 * then Hide. A detached window's slot is only where its stripe button sits, so its checked item docks it there. EDT only.
 */
final class ToolWindowMenu {
    private ToolWindowMenu() { }

    /**
     * @param current the window's slot; choosing it again does nothing for a docked window, and docks a detached one there
     * @param mode how the window is placed now; choosing it again does nothing
     * @param visible whether the window shows, which enables Hide
     * @param move moves the window to another slot
     * @param setMode docks the window or detaches it as a Float or a Window
     * @param hide hides the window
     */
    static JPopupMenu create(Slot current, ToolWindowMode mode, boolean visible, Consumer<Slot> move,
                             Consumer<ToolWindowMode> setMode, Runnable hide) {
        var menu = new JPopupMenu();
        var viewMode = new JMenu("View Mode");
        var modes = new ButtonGroup();
        for (ToolWindowMode choice : ToolWindowMode.values()) {
            var item = new JCheckBoxMenuItem(label(choice), choice == mode);
            if (choice != mode) item.addActionListener(event -> setMode.accept(choice));
            modes.add(item);
            viewMode.add(item);
        }
        menu.add(viewMode);
        var moveTo = new JMenu("Move to");
        var group = new ButtonGroup();
        for (Slot slot : Slot.values()) {
            var item = new JCheckBoxMenuItem(slot.displayName(), slot == current);
            if (slot != current || mode.detached()) item.addActionListener(event -> move.accept(slot));
            group.add(item);
            moveTo.add(item);
        }
        menu.add(moveTo);
        var hideItem = new JMenuItem("Hide");
        hideItem.setEnabled(visible);
        hideItem.addActionListener(event -> hide.run());
        menu.add(hideItem);
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme); return menu;
    }

    /** The mode's name in the View Mode submenu, as IntelliJ's. */
    private static String label(ToolWindowMode mode) {
        return switch (mode) {
            case DOCKED -> "Docked";
            case FLOAT -> "Float";
            case WINDOW -> "Window";
        };
    }
}
