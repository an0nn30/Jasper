package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** spec 5.3, Review Focus 3: a High Contrast terminal under the Light UI: only the text area is black. */
class TerminalSchemeSurfaceTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aSchemeColoursTheTextAreaAndTheWindowStaysOnTheUiVariant() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(launcher(pending), new ThemeController(false, TerminalSchemeChoice.HIGH_CONTRAST));
            owner[0].currentTab().rename("other"); owner[0].newTab(HOME); owner[0].currentTab().rename("selected");
            owner[0].update();
        });
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        edt(() -> {
            var light = SlateSteel.bundled().palette(false);
            var black = SlateSteel.bundled().terminal(TerminalScheme.HIGH_CONTRAST).colors().background();
            assertThat(owner[0].getBackground()).isEqualTo(light.color(SemanticColor.SURFACE_TERMINAL));
            assertThat(owner[0].workspaceSurface()).isEqualTo(light.color(SemanticColor.SURFACE_TERMINAL));
            assertThat(owner[0].windowTabs().getBackground()).as("the tab strip").isEqualTo(light.color(SemanticColor.SURFACE_TERMINAL));
            assertThat(WindowTabsTest.named(owner[0].windowTabs(), "select:selected").getForeground()).as("the active tab's title")
                .isEqualTo(light.color(SemanticColor.TEXT_PRIMARY));
            assertThat(owner[0].currentPane().view().palette().background()).as("the text area").isEqualTo(black);
        });
    }
}
