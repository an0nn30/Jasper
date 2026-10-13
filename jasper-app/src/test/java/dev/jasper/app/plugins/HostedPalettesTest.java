package dev.jasper.app.plugins;

import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.ui.theme.SlateSteel;
import org.junit.jupiter.api.Test;

/** Review Focus 2: plugins receive Slate Steel's roles and editor scheme, as before the token view went. */
class HostedPalettesTest {
    @Test void thePalettesAreTheVariantsRolesAndEditorScheme() {
        for (boolean dark : new boolean[]{true, false}) {
            var theme = SlateSteel.bundled().generated(dark);
            var p = theme.semantic();
            assertThat(HostedPalettes.workspace(theme)).isEqualTo(new WorkspacePalette(p.color(SURFACE_PANEL), p.color(SURFACE_PANEL_RAISED),
                p.color(SURFACE_CARD), p.color(TEXT_PRIMARY), p.color(TEXT_SECONDARY), p.color(BORDER_WINDOW), p.color(SELECTION_BG),
                p.color(TEXT_ONACCENT), p.color(SURFACE_CONTROL_HOVER), p.color(ACCENT), p.color(STATUS_SUCCESS),
                p.color(STATUS_WARNING), p.color(STATUS_DANGER)));
            var e = SlateSteel.bundled().editor(dark);
            assertThat(theme.editor()).isEqualTo(e);
            assertThat(HostedPalettes.editor(theme)).isEqualTo(new EditorPalette(e.background(), e.foreground(), e.caret(),
                e.selection(), e.currentLine(), e.gutter(), e.lineNumbers()));
        }
    }
}
