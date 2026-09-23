package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Arrays;

/** App-only retro defaults; does not alter terminal emulation or explicit application colors. */
final class RetroPalette {
    private RetroPalette() {}
    static Palette create() {
        int[] ansi = {0x000000, 0xff5555, 0x55ff55, 0xffff55, 0x6688ff, 0xff55ff, 0x55ffff, 0xdddddd,
            0x888888, 0xff8888, 0x88ff88, 0xffff88, 0x99bbff, 0xff88ff, 0x88ffff, 0xffffff};
        return new Palette(Color.WHITE, Color.BLACK, Color.WHITE, new Color(0x264f78),
            Arrays.stream(ansi).mapToObj(Color::new).toList());
    }
}
