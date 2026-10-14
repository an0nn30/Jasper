package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Review Focus 5: a window switches between Slate Steel Dark and Light in place. */
class WindowVariantTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void selectingAVariantRethemesTheWindowAndKeepsTheSchemeAndSessions() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending), new ThemeController(true, TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow())));
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        edt(() -> {
            var session = owner[0].currentPane().session();
            owner[0].selectVariant(false);
            assertThat(owner[0].theme().chrome()).isSameAs(JasperTheme.bundled().generated(false));
            assertThat(owner[0].terminalSchemeSelected(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow())).as("the scheme stays").isTrue();
            assertThat(owner[0].currentPane().session()).as("the shell keeps running").isSameAs(session);
            assertThat(owner[0].getBackground()).isEqualTo(ThemeTestSupport.color(false, SemanticColor.SURFACE_TERMINAL));
            assertThat(owner[0].currentPane().view().palette()).isEqualTo(ThemeTestSupport.terminal(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
            owner[0].selectVariant(true);
            assertThat(owner[0].theme().chrome()).isSameAs(JasperTheme.bundled().generated(true));
            assertThat(owner[0].getBackground()).isEqualTo(ThemeTestSupport.color(true, SemanticColor.SURFACE_TERMINAL));
        });
    }
}
