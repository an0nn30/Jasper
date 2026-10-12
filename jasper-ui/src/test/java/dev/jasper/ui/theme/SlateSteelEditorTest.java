package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class SlateSteelEditorTest {
    @Test void theEditorSchemesAreTheirVariantsTokens() {
        var dark = SlateSteel.bundled().editor(true);
        assertThat(dark.background()).isEqualTo(new Color(0x23262C)).isEqualTo(dark.gutter());
        assertThat(dark.foreground()).isEqualTo(new Color(0xF4F6F8));
        assertThat(dark.caret()).isEqualTo(Color.WHITE);
        assertThat(dark.selection()).isEqualTo(new Color(0x3D5F8A));
        assertThat(dark.currentLine()).isEqualTo(ColorMath.mix(Color.WHITE, new Color(0x23262C), .05));
        assertThat(dark.lineNumbers()).isEqualTo(new Color(0x8B919C));
        var syntax = SlateSteel.bundled().syntax(true);
        assertThat(syntax).containsOnlyKeys(SyntaxRole.values());
        assertThat(syntax.get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xE99CFF), false, false));
        assertThat(syntax.get(SyntaxRole.COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x99A2B1), false, true));
        assertThat(syntax.get(SyntaxRole.OPERATOR).color()).isEqualTo(new Color(0xA7ADB8));
        var light = SlateSteel.bundled().syntax(false);
        assertThat(light.get(SyntaxRole.DOC_COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x5D6470), false, true));
        assertThat(SlateSteel.bundled().editor(false).currentLine()).isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xFBFBFC), .05));
    }

    @Test void everyRoleReadsOnItsBackground() {
        for (boolean dark : new boolean[] {true, false}) {
            var editor = SlateSteel.bundled().editor(dark);
            SlateSteel.bundled().syntax(dark).forEach((role, style) -> {
                boolean comment = role == SyntaxRole.COMMENT || role == SyntaxRole.DOC_COMMENT;
                assertThat(ColorMath.contrast(style.color(), editor.background())).as("%s %s", dark, role)
                    .isGreaterThanOrEqualTo(comment ? 3 : 4.5);
            });
            assertThat(ColorMath.contrast(editor.foreground(), editor.background())).isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(editor.lineNumbers(), editor.background())).isGreaterThanOrEqualTo(3);
        }
    }

    @Test void aReferenceTheEngineCannotResolveFailsLoading() throws Exception {
        String json = SlateSteelPaletteTest.bundledJson();
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"terminal.ansi.pink\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("editor.slate-steel-dark.keyword").hasMessageContaining("terminal.ansi.pink");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"currentLine\": \"hover.overlay @ 5%\"", "\"currentLine\": \"hover.overlay @ 105%\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("currentLine");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"background\": \"terminal.bg\"", "\"background\": \"terminal.bg @ 50%\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("background");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("terminal.ansi.bright.black, italic", "terminal.ansi.bright.black, wavy"), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("wavy");
    }
}
