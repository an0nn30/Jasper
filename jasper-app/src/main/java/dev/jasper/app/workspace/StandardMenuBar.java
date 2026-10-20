package dev.jasper.app.workspace;

import dev.jasper.app.contributions.MenuTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

/** Today's menu bar: File, Edit, View, Pane, Tab, Session, Transfer, Tools, then top-level menus as registered. */
final class StandardMenuBar implements MenuBarLayout {
    @Override public List<JMenu> fixed(BuiltIn menus) { return List.of(menus.file(), menus.edit(), menus.view(), menus.pane(), menus.tab()); }

    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab) { }

    @Override public boolean toolWindowsInView() { return true; }

    @Override public List<JMenu> contributed(Pass pass) {
        var menus = new ArrayList<JMenu>();
        for (MenuTarget.Slot slot : List.of(MenuTarget.Slot.SESSION, MenuTarget.Slot.TRANSFER, MenuTarget.Slot.TOOLS))
            pass.filled(slot).ifPresent(menus::add);
        for (TopMenu top : pass.topLevel()) menus.add(top.menu());
        return menus;
    }

    @Override public void style(JMenuBar bar) { }
}
