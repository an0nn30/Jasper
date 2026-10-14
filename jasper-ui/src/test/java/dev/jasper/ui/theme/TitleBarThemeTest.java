package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** A variant may give the window's title bar its own colours; the header then paints with a theme derived from them. */
class TitleBarThemeTest {
    /** Slate Steel Light with IntelliJ New UI Light's dark header: Gray2 under Gray12. */
    static JasperTheme darkHeader() throws Exception {
        return JasperTheme.parse(JasperThemeTest.bundledJson().replace("\"accent\": \"#3F6FA8\"",
            "\"surface.titlebar\": \"#27282E\", \"text.titlebar\": \"#DFE1E5\", \"accent\": \"#3F6FA8\""), "header");
    }

    @Test void leftOutTheTitleBarIsTheChromeAndTheHeaderPaintsWithTheThemeItself() {
        for (boolean dark : new boolean[] {true, false}) {
            var theme = JasperTheme.bundled();
            var palette = theme.palette(dark);
            assertThat(palette.color(SemanticColor.SURFACE_TITLEBAR)).isEqualTo(palette.color(SemanticColor.SURFACE_APP));
            assertThat(palette.color(SemanticColor.TEXT_TITLEBAR)).isEqualTo(palette.color(SemanticColor.TEXT_PRIMARY));
            assertThat(TitleBarTheme.own(palette)).isFalse();
            assertThat(TitleBarTheme.of(theme.generated(dark))).as("the same theme, nothing derived").isSameAs(theme.generated(dark));
        }
    }

    @Test void aTitleBarOfItsOwnRecoloursOnlyWhatTheHeaderPaints() throws Exception {
        var theme = darkHeader();
        assertThat(theme.palette(true).color(SemanticColor.SURFACE_TITLEBAR)).as("the Dark variant leaves it out")
            .isEqualTo(theme.palette(true).color(SemanticColor.SURFACE_APP));
        var light = theme.generated(false);
        var header = TitleBarTheme.of(light);
        var palette = header.semantic();
        assertThat(palette.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0x27282E));
        assertThat(palette.color(SemanticColor.TEXT_PRIMARY)).isEqualTo(new Color(0xDFE1E5));
        assertThat(palette.color(SemanticColor.HOVER_OVERLAY)).as("hovers lighten the dark header").isEqualTo(new Color(0xDFE1E5));
        assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_SECONDARY), palette.color(SemanticColor.SURFACE_APP))).isGreaterThan(4.5);
        assertThat(ColorMath.lightness(palette.color(SemanticColor.SURFACE_FIELD_RAISED))).as("the search field stays dark")
            .isLessThan(30);
        assertThat(header.dark()).as("a dark header in a light theme").isTrue();
        assertThat(light.dark()).isFalse();
        assertThat(palette.color(SemanticColor.ACCENT)).isEqualTo(light.semantic().color(SemanticColor.ACCENT));
        assertThat(palette.color(SemanticColor.SURFACE_PANEL)).isEqualTo(light.semantic().color(SemanticColor.SURFACE_PANEL));
        assertThat(TitleBarTheme.of(light)).as("derived once per theme").isSameAs(header);
    }
}
