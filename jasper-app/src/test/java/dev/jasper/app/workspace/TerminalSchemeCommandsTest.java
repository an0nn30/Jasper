package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.TerminalPaletteAdapter;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayDeque;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The command palette's terminal-scheme commands: every pane and window follows, the UI variant stays. */
class TerminalSchemeCommandsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    static dev.jasper.terminal.config.Palette palette(TerminalScheme scheme) {
        return TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme));
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aLightUiRunsAHighContrastTerminalInEveryPaneAndWindowAndSwitchesBackInPlace() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> {
            var themes = new ThemeController(false, TerminalSchemeChoice.MATCH);
            owners[0] = content(launcher(pending), themes);
            owners[1] = content(launcher(pending), themes);
        });
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owners[0].currentPane().view() != null && owners[1].currentPane().view() != null);
        TerminalPane[] hidden = new TerminalPane[1];
        edt(() -> {
            var owner = owners[0];
            hidden[0] = owner.currentPane();
            owner.newTab(HOME);
            owner.selectTerminalScheme(TerminalSchemeChoice.HIGH_CONTRAST);
            assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).as("the UI stays light").isFalse();
            assertThat(hidden[0].view().palette()).as("a background tab").isEqualTo(palette(TerminalScheme.HIGH_CONTRAST));
            assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(palette(TerminalScheme.HIGH_CONTRAST));
            owner.newTab(HOME);
        });
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owners[0].currentPane().view() != null);
        edt(() -> {
            assertThat(owners[0].currentPane().view().palette()).as("a tab opened after the switch")
                .isEqualTo(palette(TerminalScheme.HIGH_CONTRAST));
            var session = hidden[0].session();
            owners[0].selectTerminalScheme(TerminalSchemeChoice.MATCH);
            assertThat(hidden[0].session()).isSameAs(session);
            assertThat(hidden[0].view().palette()).isEqualTo(palette(TerminalScheme.SLATE_STEEL_LIGHT));
        });
    }

    @Test void theCommandsChooseEachSchemeAndShowWhichIsChosen() throws Exception {
        edt(() -> {
            var themes = new ThemeController(true, TerminalSchemeChoice.MATCH);
            var owner = content(launcher(new ArrayDeque<>()), themes); owner.setActive(true);
            for (var scheme : TerminalSchemeChoice.values()) {
                var command = owner.windowCommands().view("view.terminal_scheme." + scheme.name().toLowerCase(Locale.ROOT));
                command.actionPerformed(new java.awt.event.ActionEvent(owner, 0, ""));
                assertThat(themes.schemeChoice()).isEqualTo(scheme);
                assertThat(owner.terminalSchemeSelected(scheme)).isTrue();
            }
            assertThat(owner.windowCommands().view("view.terminal_scheme.high_contrast").getValue(dev.jasper.app.commands.Command.TITLE))
                .isEqualTo("Terminal Scheme: High Contrast");
        });
    }
}
