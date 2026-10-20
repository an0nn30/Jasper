package dev.jasper.terminal.internal.rendering;

import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.internal.emulation.EmulationFixture;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TextStyle.Option;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class CellStyleTest {
    private final Palette palette = Palette.jasperDark();

    @Test
    void emptyStyleUsesThemeDefaults() {
        CellStyle s = CellStyle.resolve(EmulationFixture.attributes(TextStyle.EMPTY), palette);
        assertThat(s.foreground()).isEqualTo(palette.foreground());
        assertThat(s.background()).isEqualTo(palette.background());
        assertThat(s.bold()).isFalse();
        assertThat(s.italic()).isFalse();
        assertThat(s.underline()).isFalse();
    }

    @Test
    void inverseSwapsForegroundAndBackground() {
        TextStyle style = new TextStyle(TerminalColor.index(1), null, EnumSet.of(Option.INVERSE));
        CellStyle s = CellStyle.resolve(EmulationFixture.attributes(style), palette);
        assertThat(s.foreground()).isEqualTo(palette.background());
        assertThat(s.background()).isEqualTo(palette.ansi().get(1));
    }

    @Test
    void hiddenTextUsesBackgroundAsForeground() {
        TextStyle style = new TextStyle(TerminalColor.index(2), null, EnumSet.of(Option.HIDDEN));
        assertThat(CellStyle.resolve(EmulationFixture.attributes(style), palette).foreground()).isEqualTo(palette.background());
    }

    @Test
    void dimBlendsForegroundHalfwayToBackground() {
        TextStyle style = new TextStyle(TerminalColor.rgb(200, 200, 200), TerminalColor.rgb(0, 0, 0), EnumSet.of(Option.DIM));
        assertThat(CellStyle.resolve(EmulationFixture.attributes(style), palette).foreground()).isEqualTo(new Color(100, 100, 100));
    }

    @Test
    void boldItalicUnderlineFlags() {
        TextStyle style = new TextStyle(null, null, EnumSet.of(Option.BOLD, Option.ITALIC, Option.UNDERLINED));
        CellStyle s = CellStyle.resolve(EmulationFixture.attributes(style), palette);
        assertThat(s.bold()).isTrue();
        assertThat(s.italic()).isTrue();
        assertThat(s.underline()).isTrue();
    }

    @Test
    void hyperlinksAreUnderlined() {
        TextStyle link = new HyperlinkStyle(TextStyle.EMPTY, new LinkInfo(() -> { }));

        assertThat(CellStyle.resolve(EmulationFixture.attributes(link), palette).underline()).isTrue();
    }

    @Test
    void boldTextInTheDefaultColorTakesTheBoldForeground() {
        Palette bold = new Palette(palette.foreground(), palette.background(), palette.cursor(), palette.selection(),
            palette.ansi(), new Color(0x12ab34));
        CellStyle plain = CellStyle.resolve(EmulationFixture.attributes(TextStyle.EMPTY), bold);
        CellStyle strong = CellStyle.resolve(EmulationFixture.attributes(new TextStyle(null, null, EnumSet.of(Option.BOLD))), bold);
        CellStyle red = CellStyle.resolve(EmulationFixture.attributes(new TextStyle(TerminalColor.index(1), null, EnumSet.of(Option.BOLD))), bold);
        CellStyle inverse = CellStyle.resolve(EmulationFixture.attributes(new TextStyle(null, null, EnumSet.of(Option.BOLD, Option.INVERSE))), bold);
        assertThat(plain.foreground()).isEqualTo(palette.foreground());
        assertThat(strong.foreground()).isEqualTo(new Color(0x12ab34));
        assertThat(red.foreground()).as("an explicit color stays").isEqualTo(palette.ansi().get(1));
        assertThat(inverse.background()).as("inverse bold text has the bold color behind it").isEqualTo(new Color(0x12ab34));
    }
}
