package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.List;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class JasperLightTest {
    private static final GeneratedTheme LIGHT = ThemeGenerator.generate(Theme.JASPER_LIGHT.source().document());

    private static Color token(Token token) { return LIGHT.tokens().get(token); }

    @Test void jasperLightIsABundledNativeLightThemeWithJasperLightsTerminal() {
        assertThat(Theme.JASPER_LIGHT).extracting(Theme::id, Theme::name, Theme::dark)
            .containsExactly("jasper-light", "Jasper Light", false);
        assertThat(Theme.JASPER_LIGHT.palette()).isEqualTo(Palette.jasperLight());
        assertThat(ThemeManager.builtIns()).contains(Theme.JASPER_LIGHT, Theme.DARK, Theme.INTELLIJ_LIGHT, Theme.DARCULA);
    }

    @Test void itsTextAndStatusColoursMeetJaspersFloors() {
        for (Token background : List.of(Token.SURFACE, Token.TITLE_BAR, Token.SIDEBAR, Token.INPUT_BACKGROUND)) {
            assertThat(ChromeKeys.contrast(token(Token.TEXT), token(background))).as("text on " + background.key()).isGreaterThanOrEqualTo(4.5);
            assertThat(ChromeKeys.contrast(token(Token.TEXT_MUTED), token(background))).as("muted on " + background.key()).isGreaterThanOrEqualTo(3);
        }
        assertThat(ChromeKeys.contrast(token(Token.LINK), token(Token.SURFACE))).isGreaterThanOrEqualTo(4.5);
        for (Token status : List.of(Token.SUCCESS, Token.WARNING, Token.ERROR)) {
            assertThat(ChromeKeys.contrast(token(status), token(Token.STATUS_BAR))).as(status.key()).isGreaterThanOrEqualTo(4.5);
            assertThat(ChromeKeys.contrast(token(status), token(Token.SURFACE))).as(status.key()).isGreaterThanOrEqualTo(4.5);
        }
        assertThat(ChromeKeys.contrast(token(Token.SELECTION_TEXT), token(Token.SELECTION))).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(token(Token.TEXT_ON_ACCENT), token(Token.BUTTON_DEFAULT))).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(token(Token.DIVIDER), Palette.jasperLight().background())).isGreaterThanOrEqualTo(3);
    }

    @Test void itInstallsAsALightLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            assertThat(ThemeManager.install(Theme.JASPER_LIGHT)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Jasper Light");
            assertThat(FlatLaf.isLafDark()).isFalse();
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0xf3f4f6));
            assertThat(UIManager.getColor("List.background")).isEqualTo(Color.WHITE);
            assertThat(UIManager.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xe9ebef));
            assertThat(UIManager.getColor("Jasper.tabSelectedBackground")).isEqualTo(Color.WHITE);
        } finally { UIManager.setLookAndFeel(original); }
    }
}
