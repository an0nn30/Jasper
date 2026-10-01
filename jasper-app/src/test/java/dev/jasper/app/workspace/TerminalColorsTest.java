package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.terminal.config.Palette;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalColorsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aLightUiRunsADarkTerminalInEveryPaneAndWindowAndSwitchesBackInPlace() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> {
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            owners[0] = content(launcher(pending), themes);
            owners[1] = content(launcher(pending), themes);
        });
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owners[0].currentPane().view() != null && owners[1].currentPane().view() != null);
        edt(() -> {
            var owner = owners[0];
            var hidden = owner.currentPane();
            owner.newTab(HOME);
            var pane = owner.currentPane();
            owner.selectTerminalColors(TerminalColors.DARK);
            assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).as("the UI stays light").isFalse();
            assertThat(pane.getBackground()).isEqualTo(Theme.DARK.palette().background());
            assertThat(hidden.getBackground()).as("a background tab").isEqualTo(Theme.DARK.palette().background());
            assertThat(hidden.view().palette()).isEqualTo(Theme.DARK.palette());
            assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(Theme.DARK.palette());
            assertThat(owner.toolbar().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
            var find = hidden.findBar();
            assertThat(find.queryField().getForeground()).as("the find bar keeps UI colours")
                .isEqualTo(UIManager.getColor("TextField.foreground"));
            assertThat(find.getBackground()).isNotEqualTo(Theme.DARK.palette().background());
            owner.newTab(HOME);
            assertThat(owner.currentPane().getBackground()).as("a tab opened after the switch")
                .isEqualTo(Theme.DARK.palette().background());
            var session = hidden.session();
            owner.selectTerminalColors(TerminalColors.MATCH);
            assertThat(hidden.session()).isSameAs(session);
            assertThat(hidden.view().palette()).isEqualTo(Theme.INTELLIJ_LIGHT.palette());
            assertThat(hidden.getBackground()).isEqualTo(Theme.INTELLIJ_LIGHT.palette().background());
        });
    }

    @Test void terminalColorsMenuOffersChoicesThatApplyLiveAndReflectTheCurrentChoice() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), new ThemeController(Theme.DARK));
            owner.updateActions();
            JMenu menu = terminalColors(owner);
            List<JRadioButtonMenuItem> items = terminalItems(menu);
            assertThat(items).extracting(JMenuItem::getText)
                .containsExactly("Terminal: Match UI", "Terminal: Light", "Terminal: Dark");
            open(menu);
            assertThat(items.get(0).isSelected()).isTrue();
            items.get(1).doClick();
            assertThat(owner.workspaceChoice().name()).isEqualTo("Jasper Light");
            assertThat(owner.theme().palette()).isEqualTo(Palette.jasperLight());
            owner.selectTerminalColors(TerminalColors.DARK);
            open(menu);
            assertThat(items.get(2).isSelected()).isTrue();
            assertThat(items).allMatch(JMenuItem::isEnabled);
        });
    }

    @Test void aNamedTerminalThemeLeavesNoMenuChoiceChecked() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.DARK);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            owner.updateActions();
            JMenu menu = terminalColors(owner);
            List<JRadioButtonMenuItem> items = terminalItems(menu);
            owner.selectTerminalColors(TerminalColors.DARK);
            open(menu);
            assertThat(items.get(2).isSelected()).isTrue();
            themes.configure(Theme.DARK, new dev.jasper.app.config.WorkspaceThemeChoice("Darcula"), Theme.DARCULA, UiFontConfig.defaults());
            open(menu);
            assertThat(items).as("no built-in choice is checked for a named theme").noneMatch(JMenuItem::isSelected);
            owner.selectTerminalColors(TerminalColors.DARK);
            open(menu);
            assertThat(items).extracting(JMenuItem::isSelected).containsExactly(false, false, true);
        });
    }

    @Test void nativeLightNameDoesNotSelectTheLegacyLightMenuChoice() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.DARK);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var menu = terminalColors(owner);
            owner.selectTerminalColors(TerminalColors.LIGHT);
            open(menu);
            assertThat(terminalItems(menu).get(1).isSelected()).isTrue();
            themes.configure(Theme.DARK, new dev.jasper.app.config.WorkspaceThemeChoice("Light"),
                Theme.DARCULA, UiFontConfig.defaults());
            open(menu);
            assertThat(terminalItems(menu)).noneMatch(JMenuItem::isSelected);
            assertThat(themes.workspaceChoice().name()).isEqualTo("Light");
        });
    }

    private static void open(JMenu menu) {
        for (var listener : menu.getMenuListeners()) listener.menuSelected(new MenuEvent(menu));
    }

    private static JMenu terminalColors(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents())
            if (component instanceof JMenu menu && menu.getText().equals("Terminal Colors")) return menu;
        throw new AssertionError("Terminal Colors menu is absent");
    }

    private static List<JRadioButtonMenuItem> terminalItems(JMenu menu) {
        var items = new ArrayList<JRadioButtonMenuItem>();
        for (var component : menu.getMenuComponents())
            if (component instanceof JRadioButtonMenuItem item && item.getText().startsWith("Terminal:")) items.add(item);
        return items;
    }
}
