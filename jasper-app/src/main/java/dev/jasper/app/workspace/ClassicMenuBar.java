package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

/**
 * The classic menu bar (spec 10.2): File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help. Window holds
 * the Pane items, the Tab items and the tool-window toggles; Help comes last. The row is the mocks': 24 px of titles on
 * {@code surface.app} over a 1 px {@code border.divider} rule, an open title in the accent, square.
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

    @Override public void style(JMenuBar bar) {
        var theme = FlatLafInstaller.installedTheme();
        var metrics = FlatLafInstaller.installedKit().metrics();
        var palette = theme.semantic();
        // The item margins make the row exactly MENU_BAR_HEIGHT for the menu face's line, in logical pixels.
        int line = Math.round(bar.getFontMetrics(ThemeZone.font(Typography.Role.BODY)).getHeight() / UIScale.getUserScaleFactor());
        int height = metrics.value(DesignMetrics.Role.MENU_BAR_HEIGHT), padding = metrics.value(DesignMetrics.Role.MENU_BAR_ITEM_PADDING);
        int top = Math.max(0, (height - line) / 2), bottom = Math.max(0, height - line - top);
        bar.putClientProperty("FlatLaf.style", Map.of("itemMargins", new Insets(top, padding, bottom, padding),
            "selectionInsets", new Insets(0, 0, 0, 0), "selectionArc", 0,
            "hoverBackground", palette.color(SemanticColor.SURFACE_CONTROL_HOVER),
            "selectionBackground", palette.color(SemanticColor.ACCENT), "selectionForeground", palette.color(SemanticColor.TEXT_ONACCENT)));
        int side = UIScale.scale(metrics.value(DesignMetrics.Role.MENU_BAR_SIDE)), rule = UIScale.scale(metrics.value(DesignMetrics.Role.HAIRLINE));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, rule, 0, DerivedColor.BORDER_DIVIDER.color(theme)),
            BorderFactory.createEmptyBorder(0, side, 0, side)));
        bar.setBackground(palette.color(SemanticColor.SURFACE_APP));
        bar.setOpaque(true);
        bar.revalidate(); bar.repaint();
    }
}
