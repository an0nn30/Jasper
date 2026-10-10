package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.TerminalColors;
import java.util.ArrayDeque;
import javax.swing.*;
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
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
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
            assertThat(hidden.view().palette().ansi()).as("retained terminal preserves all 16 workspace ANSI colors")
                .containsExactlyElementsOf(Theme.DARK.palette().ansi());
            assertThat(hidden.view().palette().ansi()).isNotEqualTo(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT.palette().ansi());
            assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(Theme.DARK.palette());
            assertThat(owner.toolbar().getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
            var find = hidden.findBar();
            assertThat(find.queryField().getForeground()).as("the find bar follows workspace tokens")
                .isEqualTo(owner.theme().workspace().tokens().get(dev.jasper.ui.theme.Token.TEXT));
            assertThat(find.getBackground()).isNotEqualTo(Theme.DARK.palette().background());
            owner.newTab(HOME);
            assertThat(owner.currentPane().getBackground()).as("a tab opened after the switch")
                .isEqualTo(Theme.DARK.palette().background());
            var session = hidden.session();
            owner.selectTerminalColors(TerminalColors.MATCH);
            assertThat(hidden.session()).isSameAs(session);
            assertThat(hidden.view().palette()).isEqualTo(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT.palette());
            assertThat(hidden.getBackground()).isEqualTo(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT.palette().background());
        });
    }

    @Test void compatibilityCommandsStillSelectExplicitNativeNames() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), new ThemeController(Theme.DARK)); owner.setActive(true);
            for (var entry : java.util.Map.of("light", "Jasper Light", "dark", "Jasper Dark", "match", "match").entrySet()) {
                owner.windowCommands().view("view.terminal_colors." + entry.getKey()).actionPerformed(new java.awt.event.ActionEvent(owner,0,""));
                assertThat(owner.workspaceChoice().name()).isEqualTo(entry.getValue());
            }
        });
    }
}
