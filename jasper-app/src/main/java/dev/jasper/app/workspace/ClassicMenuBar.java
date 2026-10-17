package dev.jasper.app.workspace;

import dev.jasper.app.contributions.MenuTarget;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

/**
 * The classic menu bar (spec 10.2): File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help. Window holds
 * the Pane items, the Tab items and the tool-window toggles; Help comes last. The row is the mocks'
 * ({@link dev.jasper.app.platform.ClassicMenuRow}).
 */
final class ClassicMenuBar implements MenuBarLayout {
    @Override public List<JMenu> fixed(BuiltIn menus) { return List.of(menus.file(), menus.edit(), menus.view()); }

    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab) {
        // Window gathers Pane and Tab every pass, so they are built fresh with their sections, as Tools is.
        slots.put(MenuTarget.Slot.PANE, pane.get());
        slots.put(MenuTarget.Slot.TAB, tab.get());
    }

    @Override public boolean toolWindowsInView() { return false; }

    @Override public List<JMenu> contributed(Pass pass) {
        var menus = new ArrayList<JMenu>();
        pass.filled(MenuTarget.Slot.SESSION).ifPresent(menus::add);
        pass.filled(MenuTarget.Slot.TRANSFER).ifPresent(menus::add);
        for (TopMenu top : pass.topLevel()) if (!top.help()) menus.add(top.menu());
        pass.filled(MenuTarget.Slot.TOOLS).ifPresent(menus::add);
        menus.add(window(pass));
        for (TopMenu top : pass.topLevel()) if (top.help()) menus.add(top.menu());
        return menus;
    }

    /** Pane's items, a rule, Tab's items, then a rule and the tool-window toggles. */
    private static JMenu window(Pass pass) {
        JMenu window = pass.menu().apply("Window");
        move(pass.slots().get(MenuTarget.Slot.PANE), window);
        if (window.getMenuComponentCount() > 0) window.addSeparator();
        move(pass.slots().get(MenuTarget.Slot.TAB), window);
        if (!pass.toolWindows().isEmpty()) {
            window.addSeparator();
            pass.toolWindows().forEach(window::add);
        }
        return window;
    }

    private static void move(JMenu from, JMenu to) {
        if (from == null) return;
        for (Component child : from.getMenuComponents()) to.add(child);
    }

    @Override public void style(JMenuBar bar) { dev.jasper.app.platform.ClassicMenuRow.style(bar); }
}
