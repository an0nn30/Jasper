package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class SyntaxSampleTest {
    @Test void exactSpansUseEffectiveStylesAndSwitchResetsFlags() {
        var sample = new SyntaxSample(); var base = ThemeManager.generated(Theme.DARK);
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class); styles.putAll(base.syntax());
        styles.put(SyntaxRole.KEYWORD, new SyntaxStyle(Color.PINK, true, true));
        var bold = new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), styles, base.terminal(), base.uiKeys(), base.icons());
        sample.theme(bold);
        assertThat(SyntaxSample.SPANS.stream().map(s -> SyntaxSample.TEXT.substring(s.start(), s.end())).toList())
            .isEqualTo(List.of("// Theme preview", "class", "Sample", "String", "greet", "int", "return", "\"Hello\"", "+", "42"));
        for (var span : SyntaxSample.SPANS) {
            var attrs = sample.getStyledDocument().getCharacterElement(span.start()).getAttributes();
            var style = styles.get(span.role());
            assertThat(StyleConstants.getForeground(attrs)).isEqualTo(style.color());
            assertThat(StyleConstants.isBold(attrs)).isEqualTo(style.bold());
            assertThat(StyleConstants.isItalic(attrs)).isEqualTo(style.italic());
        }
        sample.theme(base);
        var attrs = sample.getStyledDocument().getCharacterElement(17).getAttributes();
        assertThat(StyleConstants.isBold(attrs)).isEqualTo(base.syntax().get(SyntaxRole.KEYWORD).bold());
        assertThat(StyleConstants.isItalic(attrs)).isEqualTo(base.syntax().get(SyntaxRole.KEYWORD).italic());
        assertThat(sample.isEditable()).isFalse(); assertThat(sample.isFocusable()).isFalse();
    }
}
