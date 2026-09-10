package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CursorStyleTest {

    @Test
    void noRequestUsesTheConfiguredStyle() {
        assertThat(CursorStyle.effective(null, CursorStyle.UNDERLINE)).isEqualTo(CursorStyle.UNDERLINE);
        assertThat(CursorStyle.effectiveBlink(null, true)).isTrue();
    }

    @Test
    void applicationRequestsMapToStyles() {
        assertThat(CursorStyle.effective(CursorShape.STEADY_VERTICAL_BAR, CursorStyle.BLOCK)).isEqualTo(CursorStyle.BEAM);
        assertThat(CursorStyle.effective(CursorShape.BLINK_BLOCK, CursorStyle.BEAM)).isEqualTo(CursorStyle.BLOCK);
        assertThat(CursorStyle.effective(CursorShape.STEADY_UNDERLINE, CursorStyle.BLOCK)).isEqualTo(CursorStyle.UNDERLINE);
    }

    @Test
    void applicationRequestsDecideBlinking() {
        assertThat(CursorStyle.effectiveBlink(CursorShape.BLINK_VERTICAL_BAR, false)).isTrue();
        assertThat(CursorStyle.effectiveBlink(CursorShape.STEADY_BLOCK, true)).isFalse();
    }
}
