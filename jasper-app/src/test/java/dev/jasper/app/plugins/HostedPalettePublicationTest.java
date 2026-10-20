package dev.jasper.app.plugins;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.ui.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit host adapters follow the published workspace's roles, editor scheme and syntax through variant changes. */
@ExtendWith(EdtTestExtension.class)
class HostedPalettePublicationTest {
    @Test void dynamicWorkspaceEditorAndSyntaxReadersTrackTheVariant() {
        try (var themes = ThemeTestSupport.controller(false)) {
            for (boolean dark : new boolean[]{true, false, true}) {
                themes.configure(dark, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
                var expected = themes.current().workspace();
                assertThat(HostedPalettes.workspace(themes.current().workspace()).text()).isEqualTo(expected.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
                assertThat(HostedPalettes.workspace(themes.current().workspace()).background()).isEqualTo(expected.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
                var editor = HostedPalettes.editor(themes.current().workspace());
                assertThat(editor.background()).isEqualTo(expected.editor().background());
                assertThat(editor.selection()).isEqualTo(expected.editor().selection());
                for (var role : SyntaxRole.values()) {
                    var style = expected.syntax().get(dev.jasper.ui.theme.SyntaxRole.valueOf(role.name()));
                    assertThat(HostedPalettes.syntax(themes.current().workspace()).style(role)).isEqualTo(new SyntaxStyle(style.color(), style.bold(), style.italic()));
                }
                assertThat(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP))
                    .isEqualTo(themes.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
            }
            assertThat(HostedPalettes.workspace(themes.current().workspace()).text()).isEqualTo(themes.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
        }
    }
}
