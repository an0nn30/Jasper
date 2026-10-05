package dev.jasper.app.appearance;

import dev.jasper.app.config.WorkspaceThemeChoice;
import dev.jasper.app.testsupport.EdtTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ThemeStateTest {
    @Test void matchingUsesTheExactChromeObjectAndItsTerminalPalette() {
        var value = ThemeState.defaults().resolve();
        assertThat(value.workspace()).isSameAs(value.chrome());
        assertThat(value.matching()).isTrue();
        assertThat(value.palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(value.workspace().terminal()));
    }

    @Test void explicitWorkspaceSurvivesChromeChangesAndMissingResetsToMatch() {
        var choice = new WorkspaceThemeChoice("Darcula");
        var state = new ThemeState(Theme.DARK, choice, dev.jasper.app.appearance.ThemeFixtures.DARCULA);
        assertThat(state.resolve().workspace()).isSameAs(ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.DARCULA));
        assertThat(state.configure(Theme.JASPER_LIGHT).workspaceChoice()).isEqualTo(choice);
        assertThat(state.configure(Theme.JASPER_LIGHT).resolve().workspace()).isSameAs(state.resolve().workspace());
        var missing = new ThemeState(Theme.DARK, new WorkspaceThemeChoice("Missing"), null).resolve();
        assertThat(missing.workspace()).isSameAs(missing.chrome());
    }
}
