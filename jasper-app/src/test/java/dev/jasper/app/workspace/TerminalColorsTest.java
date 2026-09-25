package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.ThemeStyle;
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
            var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
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
            assertThat(pane.getBackground()).isEqualTo(Palette.jasperDark().background());
            assertThat(hidden.getBackground()).as("a background tab").isEqualTo(Palette.jasperDark().background());
            assertThat(hidden.view().palette()).isEqualTo(Palette.jasperDark());
            assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(Palette.jasperDark());
            assertThat(owner.toolbar().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
            var find = hidden.findBar();
            assertThat(find.queryField().getForeground()).as("the find bar keeps UI colours")
                .isEqualTo(UIManager.getColor("TextField.foreground"));
            assertThat(find.getBackground()).isNotEqualTo(Palette.jasperDark().background());
            owner.newTab(HOME);
            assertThat(owner.currentPane().getBackground()).as("a tab opened after the switch")
                .isEqualTo(Palette.jasperDark().background());
            var session = hidden.session();
            owner.selectTerminalColors(TerminalColors.MATCH);
            assertThat(hidden.session()).isSameAs(session);
            assertThat(hidden.view().palette()).isEqualTo(Palette.jasperLight());
            assertThat(hidden.getBackground()).isEqualTo(Palette.jasperLight().background());
        });
    }

    @Test void appearanceMenuOffersTerminalChoicesThatApplyLiveAndReflectTheCurrentChoice() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), new ThemeController(ThemeStyle.MODERN, Appearance.DARK));
            owner.updateActions();
            JMenu menu = appearance(owner);
            List<JRadioButtonMenuItem> items = terminalItems(menu);
            assertThat(items).extracting(JMenuItem::getText)
                .containsExactly("Terminal: Match UI", "Terminal: Light", "Terminal: Dark");
            open(menu);
            assertThat(items.get(0).isSelected()).isTrue();
            items.get(1).doClick();
            assertThat(owner.terminalColors()).isEqualTo(TerminalColors.LIGHT);
            assertThat(owner.theme().palette()).isEqualTo(Palette.jasperLight());
            owner.selectTerminalColors(TerminalColors.DARK);
            open(menu);
            assertThat(items.get(2).isSelected()).isTrue();
            assertThat(items).allMatch(JMenuItem::isEnabled);
        });
    }

    @Test void retroShowsTheTerminalChoicesDisabled() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT));
            owner.updateActions();
            assertThat(terminalItems(appearance(owner))).hasSize(3).noneMatch(JMenuItem::isEnabled);
            assertThat(owner.terminalColors()).isEqualTo(TerminalColors.MATCH);
        });
    }

    private static void open(JMenu menu) {
        for (var listener : menu.getMenuListeners()) listener.menuSelected(new MenuEvent(menu));
    }

    private static JMenu appearance(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents())
            if (component instanceof JMenu menu && menu.getText().equals("Appearance")) return menu;
        throw new AssertionError("Appearance menu is absent");
    }

    private static List<JRadioButtonMenuItem> terminalItems(JMenu menu) {
        var items = new ArrayList<JRadioButtonMenuItem>();
        for (var component : menu.getMenuComponents())
            if (component instanceof JRadioButtonMenuItem item && item.getText().startsWith("Terminal:")) items.add(item);
        return items;
    }
}
