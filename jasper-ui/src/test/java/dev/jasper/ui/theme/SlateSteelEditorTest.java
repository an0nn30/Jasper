package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class SlateSteelEditorTest {
    @Test void theEditorSchemesAreTheirVariantsTokens() {
        var dark = JasperTheme.bundled().editor(true);
        assertThat(dark.background()).isEqualTo(new Color(0x23262C)).isEqualTo(dark.gutter());
        assertThat(dark.foreground()).isEqualTo(new Color(0xF4F6F8));
        assertThat(dark.caret()).isEqualTo(Color.WHITE);
        assertThat(dark.selection()).isEqualTo(new Color(0x3D5F8A));
        assertThat(dark.currentLine()).isEqualTo(ColorMath.mix(Color.WHITE, new Color(0x23262C), .05));
        assertThat(dark.lineNumbers()).isEqualTo(new Color(0x8B919C));
        var syntax = JasperTheme.bundled().syntax(true);
        assertThat(syntax).containsOnlyKeys(SyntaxRole.values());
        assertThat(syntax.get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xE99CFF), false, false));
        assertThat(syntax.get(SyntaxRole.COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x99A2B1), false, true));
        assertThat(syntax.get(SyntaxRole.OPERATOR).color()).isEqualTo(new Color(0xA7ADB8));
        var light = JasperTheme.bundled().syntax(false);
        assertThat(light.get(SyntaxRole.DOC_COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x5D6470), false, true));
        assertThat(JasperTheme.bundled().editor(false).currentLine()).isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xFBFBFC), .05));
    }

    @Test void everyRoleReadsOnItsBackground() {
        for (boolean dark : new boolean[] {true, false}) {
            var editor = JasperTheme.bundled().editor(dark);
            JasperTheme.bundled().syntax(dark).forEach((role, style) -> {
                boolean comment = role == SyntaxRole.COMMENT || role == SyntaxRole.DOC_COMMENT;
                assertThat(ColorMath.contrast(style.color(), editor.background())).as("%s %s", dark, role)
                    .isGreaterThanOrEqualTo(comment ? 3 : 4.5);
            });
            assertThat(ColorMath.contrast(editor.foreground(), editor.background())).isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(editor.lineNumbers(), editor.background())).isGreaterThanOrEqualTo(3);
        }
    }
}
