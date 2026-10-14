package dev.jasper.app.plugins;

import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.ui.theme.JasperTheme;
import org.junit.jupiter.api.Test;

/** Review Focus 2: plugins receive Slate Steel's roles and editor scheme, as before the token view went. */
class HostedPalettesTest {
    /** Every role and editor colour distinct, so two palette fields that swap sources are caught even where Slate Steel shares a colour. */
    @Test void eachPaletteFieldTakesItsOwnSource() {
        var roles = new java.util.EnumMap<dev.jasper.ui.theme.SemanticColor, java.awt.Color>(dev.jasper.ui.theme.SemanticColor.class);
        for (var role : dev.jasper.ui.theme.SemanticColor.values()) {
            int i = role.ordinal() + 1;
            roles.put(role, new java.awt.Color(i * 3, 255 - i * 2, (i * 53) % 256));
        }
        var base = dev.jasper.app.appearance.ThemeTestSupport.recolored(JasperTheme.bundled().generated(true), roles);
        var editor = new dev.jasper.ui.theme.EditorColors(new java.awt.Color(1, 2, 3), new java.awt.Color(4, 5, 6), new java.awt.Color(7, 8, 9),
            new java.awt.Color(10, 11, 12), new java.awt.Color(13, 14, 15), new java.awt.Color(16, 17, 18), new java.awt.Color(19, 20, 21));
        var theme = new dev.jasper.ui.theme.GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), editor,
            base.syntax(), base.uiKeys(), base.glass());
        assertThat(HostedPalettes.workspace(theme)).isEqualTo(new WorkspacePalette(roles.get(SURFACE_PANEL), roles.get(SURFACE_PANEL_RAISED),
            roles.get(SURFACE_CARD), roles.get(TEXT_PRIMARY), roles.get(TEXT_SECONDARY), roles.get(BORDER_WINDOW), roles.get(SELECTION_BG),
            roles.get(TEXT_ONSELECTION), roles.get(SURFACE_CONTROL_HOVER), roles.get(ACCENT), roles.get(STATUS_SUCCESS),
            roles.get(STATUS_WARNING), roles.get(STATUS_DANGER)));
        assertThat(HostedPalettes.editor(theme)).isEqualTo(new EditorPalette(new java.awt.Color(1, 2, 3), new java.awt.Color(4, 5, 6),
            new java.awt.Color(7, 8, 9), new java.awt.Color(10, 11, 12), new java.awt.Color(13, 14, 15), new java.awt.Color(16, 17, 18),
            new java.awt.Color(19, 20, 21)));
    }

    @Test void thePalettesAreTheVariantsRolesAndEditorScheme() {
        for (boolean dark : new boolean[]{true, false}) {
            var theme = JasperTheme.bundled().generated(dark);
            var p = theme.semantic();
            assertThat(HostedPalettes.workspace(theme)).isEqualTo(new WorkspacePalette(p.color(SURFACE_PANEL), p.color(SURFACE_PANEL_RAISED),
                p.color(SURFACE_CARD), p.color(TEXT_PRIMARY), p.color(TEXT_SECONDARY), p.color(BORDER_WINDOW), p.color(SELECTION_BG),
                p.color(TEXT_ONSELECTION), p.color(SURFACE_CONTROL_HOVER), p.color(ACCENT), p.color(STATUS_SUCCESS),
                p.color(STATUS_WARNING), p.color(STATUS_DANGER)));
            var e = JasperTheme.bundled().editor(dark);
            assertThat(theme.editor()).isEqualTo(e);
            assertThat(HostedPalettes.editor(theme)).isEqualTo(new EditorPalette(e.background(), e.foreground(), e.caret(),
                e.selection(), e.currentLine(), e.gutter(), e.lineNumbers()));
        }
    }
}
