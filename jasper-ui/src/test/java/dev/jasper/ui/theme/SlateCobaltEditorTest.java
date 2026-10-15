package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class SlateCobaltEditorTest {
    @Test void theEditorSchemesAreTheirVariantsTokens() {
        var dark = JasperTheme.bundled().editor(true);
        assertThat(dark.background()).isEqualTo(new Color(0x2B2F36)).isEqualTo(dark.gutter());
        assertThat(dark.foreground()).isEqualTo(new Color(0xF2F4F7));
        assertThat(dark.caret()).isEqualTo(new Color(0x5B9BF5));
        assertThat(dark.selection()).isEqualTo(new Color(0x3A5A8C));
        assertThat(dark.currentLine()).isEqualTo(ColorMath.mix(Color.WHITE, new Color(0x2B2F36), .05));
        assertThat(dark.lineNumbers()).isEqualTo(new Color(0x9AA2AE));
        var syntax = JasperTheme.bundled().syntax(true);
        assertThat(syntax).containsOnlyKeys(SyntaxRole.values());
        assertThat(syntax.get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xEBA2FF), false, false));
        assertThat(syntax.get(SyntaxRole.COMMENT)).isEqualTo(new SyntaxStyle(new Color(0xA5AEB9), false, true));
        assertThat(syntax.get(SyntaxRole.OPERATOR).color()).isEqualTo(new Color(0xB2B9C4));
        var light = JasperTheme.bundled().syntax(false);
        assertThat(light.get(SyntaxRole.DOC_COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x5A6270), false, true));
        assertThat(JasperTheme.bundled().editor(false).currentLine()).isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xFAFBFC), .05));
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
