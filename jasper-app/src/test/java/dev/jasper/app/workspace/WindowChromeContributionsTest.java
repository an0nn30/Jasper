package dev.jasper.app.workspace;

import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.ToolbarEntry;
import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowChromeContributionsTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    private static List<String> buttons(WindowContent owner) {
        List<String> labels = new ArrayList<>();
        for (Component child : owner.toolbar().getComponents())
            if (child instanceof JButton button) labels.add(String.valueOf(button.getClientProperty("label")));
        return labels;
    }

    private static List<String> items(JMenu menu) {
        List<String> labels = new ArrayList<>();
        for (Component child : menu.getMenuComponents())
            labels.add(child instanceof JMenu sub ? "submenu:" + sub.getText() : child instanceof JMenuItem item ? item.getText() : "---");
        return labels;
    }

    private static JMenu menu(WindowContent owner, String title) {
        for (int i = 0; i < owner.menuBar().getMenuCount(); i++)
            if (owner.menuBar().getMenu(i).getText().equals(title)) return owner.menuBar().getMenu(i);
        return null;
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void toolbarControlsFollowBuiltinsAndFollowModeAndRemoval(boolean retro) throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), new dev.jasper.app.appearance.ThemeController(retro ? dev.jasper.app.config.ThemeStyle.RETRO : dev.jasper.app.config.ThemeStyle.MODERN, dev.jasper.app.config.Appearance.DARK));
            List<String> before = buttons(owner);
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run Tool", null, List.of(), Optional.empty(), seen::add);
            model.addAction("dev.x.stop", "Stop Tool", null, List.of(), Optional.empty(), seen::add);
            model.addToolbar(new ToolbarEntry.Button("dev.x.run"));
            model.addToolbar(new ToolbarEntry.Dropdown(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.run", "dev.x.stop")));
            model.addToolbar(new ToolbarEntry.Button("dev.x.never-registered"));

            List<String> after = buttons(owner);
            assertThat(after).containsSubsequence("Find", "Run Tool", "Tool");
            assertThat(after).hasSize(before.size() + 2);
            JButton button = null;
            for (Component child : owner.toolbar().getComponents())
                if (child instanceof JButton candidate && "Run Tool".equals(candidate.getClientProperty("label"))) button = candidate;
            assertThat(button.getIcon()).as("a fallback icon keeps the toolbar's icon-led layout").isNotNull();
            if (retro) {
                assertThat(button.getIcon().getIconWidth()).isEqualTo(28);
                assertThat(button.getHorizontalTextPosition()).isEqualTo(javax.swing.SwingConstants.CENTER);
                assertThat(button.getVerticalTextPosition()).isEqualTo(javax.swing.SwingConstants.BOTTOM);
            }
            button.doClick();
            assertThat(seen).hasSize(1);

            owner.setToolbarMode(ToolbarMode.ICONS);
            assertThat(button.getText()).isNull();
            owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            run.setTitle("Run It");
            assertThat(buttons(owner)).contains("Run It").doesNotContain("Run Tool");
            run.close();
            assertThat(buttons(owner)).doesNotContain("Run It").contains("Tool");
        });
    }

    @Test void menusGainSectionsTopLevelMenusAndContextEntries() throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            int viewBefore = menu(owner, "View").getMenuComponentCount();
            int contextBefore = owner.chrome().contextMenu().getComponentCount();
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run Tool", null, List.of(), Optional.empty(), seen::add);
            MenuSection view = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.VIEW));
            view.set(List.of(new MenuEntry.Item("dev.x.run"), new MenuEntry.Separator(),
                new MenuEntry.Submenu("More", List.of(new MenuEntry.Item("dev.x.run"))),
                new MenuEntry.Submenu("Empty", List.of(new MenuEntry.Item("dev.x.gone")))));
            MenuSection top = model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Tool"));
            top.set(List.of(new MenuEntry.Item("dev.x.run")));
            model.addMenuSection(MenuTarget.terminalContext()).set(List.of(new MenuEntry.Item("dev.x.run")));

            List<String> viewItems = items(menu(owner, "View"));
            assertThat(viewItems).hasSize(viewBefore + 4);
            assertThat(viewItems.subList(viewBefore, viewItems.size())).containsExactly("---", "Run Tool", "---", "submenu:More");
            assertThat(owner.menuBar().getMenu(owner.menuBar().getMenuCount() - 1).getText()).isEqualTo("Tool");
            assertThat(items(menu(owner, "Tool"))).containsExactly("Run Tool");
            ((JMenuItem) menu(owner, "Tool").getMenuComponent(0)).doClick();
            assertThat(seen).hasSize(1);

            JPopupMenu context = owner.chrome().contextMenu();
            assertThat(context.getComponentCount()).isEqualTo(contextBefore + 2);
            assertThat(((JMenuItem) context.getComponent(contextBefore + 1)).getText()).isEqualTo("Run Tool");

            top.set(List.of());
            assertThat(menu(owner, "Tool")).as("an empty contributed menu is not shown").isNull();
            run.close();
            assertThat(items(menu(owner, "View"))).hasSize(viewBefore);
            assertThat(owner.chrome().contextMenu().getComponentCount()).isEqualTo(contextBefore);
        });
    }
}
