package dev.jasper.app.workspace;

import dev.jasper.app.contributions.MenuTarget;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 * How a window's menu bar composes its menus (Enterprise presentation spec 10.2): the same contributions in two orders.
 * The window builds every menu; a layout chooses which built-in menus the bar shows from the start, which menus a
 * contribution pass builds fresh, and the order of the menus each pass adds. EDT only.
 */
sealed interface MenuBarLayout permits StandardMenuBar, ClassicMenuBar {
    /**
     * The built-in menus, built once by the window.
     *
     * @param file File
     * @param edit Edit
     * @param view View
     * @param tab Tab
     * @param window Window, holding Layouts (full windows plan 4)
     */
    record BuiltIn(JMenu file, JMenu edit, JMenu view, JMenu tab, JMenu window) { }

    /**
     * A contributed top-level menu of this pass.
     *
     * @param menu the menu
     * @param help whether it is the app's Help menu ({@link MenuTarget#HELP})
     */
    record TopMenu(JMenu menu, boolean help) { }

    /**
     * One contribution pass's menus.
     *
     * @param slots the built-in menus by slot, the fresh ones of this pass included, with their contributed sections
     * @param topLevel the contributed top-level menus in registration order
     * @param menu makes an empty menu that refreshes the window's actions when opened
     * @param toolWindows one item per tool window's toggle action, in registration order; empty for a layout whose
     *        {@link MenuBarLayout#toolWindowsInView()} is true
     * @param layouts makes a Layouts submenu (full windows plan 4) for a layout that builds its Window menu every pass
     */
    record Pass(Map<MenuTarget.Slot, JMenu> slots, List<TopMenu> topLevel, Function<String, JMenu> menu, List<JMenuItem> toolWindows,
                Supplier<JMenu> layouts) {
        /** Copies the lists. */
        public Pass {
            topLevel = List.copyOf(topLevel); toolWindows = List.copyOf(toolWindows);
            Objects.requireNonNull(slots); Objects.requireNonNull(menu); Objects.requireNonNull(layouts);
        }

        /** {@code slot}'s menu while it has entries. */
        Optional<JMenu> filled(MenuTarget.Slot slot) {
            JMenu menu = slots.get(slot);
            return menu != null && menu.getMenuComponentCount() > 0 ? Optional.of(menu) : Optional.empty();
        }
    }

    /** The built-in menus the bar shows from the start, in order. */
    List<JMenu> fixed(BuiltIn menus);

    /** Before a pass: puts fresh menus in the slots this layout rebuilds every pass. */
    void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> tab);

    /** Whether View keeps the app's "Tool Windows" section. */
    boolean toolWindowsInView();

    /** The menus a pass adds after the fixed ones, in order. */
    List<JMenu> contributed(Pass pass);

    /** Styles the bar from the installed theme and kit; called when the window is built and on every theme change. */
    void style(JMenuBar bar);
}
