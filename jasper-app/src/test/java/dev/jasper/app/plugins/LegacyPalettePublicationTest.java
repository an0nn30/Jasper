package dev.jasper.app.plugins;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.ui.*;
import dev.jasper.ui.theme.Token;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Transitional dynamic SDK readers must remain live until their own consumer migration. */
@ExtendWith(EdtTestExtension.class)
class LegacyPalettePublicationTest {
    @Test void dynamicWorkspaceEditorAndSyntaxReadersTrackIndependentSelectionAndMatch() {
        try (var themes = new ThemeController(Theme.JASPER_LIGHT)) {
            var component = new JPanel(); component.putClientProperty("Jasper.zone", "workspace");
            for (Theme workspace : java.util.List.of(Theme.DARK, Theme.JASPER_LIGHT, Theme.DARK)) {
                themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice(workspace.name()), workspace, UiFontConfig.defaults());
                var expected = themes.current().workspace();
                assertThat(WorkspacePalette.current(component).text()).isEqualTo(expected.tokens().get(Token.TEXT));
                assertThat(WorkspacePalette.current().background()).isEqualTo(expected.tokens().get(Token.SURFACE));
                var editor = EditorPalette.current();
                assertThat(editor.background()).isEqualTo(expected.tokens().get(Token.EDITOR_BACKGROUND));
                assertThat(editor.selection()).isEqualTo(expected.tokens().get(Token.EDITOR_SELECTION));
                for (var role : SyntaxRole.values()) {
                    var style = expected.syntax().get(dev.jasper.ui.theme.SyntaxRole.valueOf(role.name()));
                    assertThat(SyntaxPalette.current().style(role)).isEqualTo(new SyntaxStyle(style.color(), style.bold(), style.italic()));
                }
                assertThat(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP))
                    .isEqualTo(themes.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
            }
            themes.configureTerminal(TerminalColors.MATCH);
            assertThat(WorkspacePalette.current().text()).isEqualTo(themes.current().chrome().tokens().get(Token.TEXT));
        }
    }
}
