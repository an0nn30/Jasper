package dev.jasper.app.workspace;

import dev.jasper.app.dock.Slot;
import java.util.function.Consumer;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * A tool window's menu, the same from its stripe button (right-click) and its header's options button: Move to ›
 * the six slots by name with the current one checked, then Hide. EDT only.
 */
final class ToolWindowMenu {
    private ToolWindowMenu() { }

    /**
     * @param current the window's slot; choosing it again does nothing
     * @param visible whether the window shows, which enables Hide
     * @param move moves the window to another slot
     * @param hide hides the window
     */
    static JPopupMenu create(Slot current, boolean visible, Consumer<Slot> move, Runnable hide) {
        var menu = new JPopupMenu();
        var moveTo = new JMenu("Move to");
        var group = new ButtonGroup();
        for (Slot slot : Slot.values()) {
            var item = new JCheckBoxMenuItem(slot.displayName(), slot == current);
            if (slot != current) item.addActionListener(event -> move.accept(slot));
            group.add(item);
            moveTo.add(item);
        }
        menu.add(moveTo);
        var hideItem = new JMenuItem("Hide");
        hideItem.setEnabled(visible);
        hideItem.addActionListener(event -> hide.run());
        menu.add(hideItem);
        dev.jasper.ui.swing.NativeControlStyles.menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme); return menu;
    }
}
