package dev.jasper.editor.ui;

import dev.jasper.sdk.ui.TerminalTypography;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EditorTypographyTest {
    @Test void followDoesNotOverwriteIndependentFont() {
        var own = new EditorSettings("Monospaced", 13.5, List.of("Dialog"), false, 1.2, false, 4, false, 10, false);
        var terminal = new TerminalTypography("DialogInput", 17, List.of(), true, 1.6);
        assertThat(EditorTypography.effective(own, terminal)).isEqualTo(
            new TerminalTypography("Monospaced", 13.5, List.of("Dialog"), false, 1.2));
        var followed = new EditorSettings(own.fontFamily(), own.fontSize(), own.fontFallback(), own.fontLigatures(),
            own.fontLineHeight(), true, 4, false, 10, false);
        assertThat(EditorTypography.effective(followed, terminal)).isSameAs(terminal);
        assertThat(own.fontSize()).isEqualTo(13.5);
        assertThat(own.font().getSize2D()).isEqualTo(13.5f);
    }
}
