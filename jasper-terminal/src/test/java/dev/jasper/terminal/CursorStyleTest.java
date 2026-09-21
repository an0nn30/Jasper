package dev.jasper.terminal;

import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CursorStyleTest {

    @Test
    void noRequestUsesTheConfiguredStyle() {
        assertThat(CursorRequest.effective(null, CursorStyle.UNDERLINE)).isEqualTo(CursorStyle.UNDERLINE);
        assertThat(CursorRequest.effectiveBlink(null, true)).isTrue();
    }

    @Test
    void applicationRequestsMapToStyles() {
        assertThat(CursorRequest.effective(JediCellReader.cursor(CursorShape.STEADY_VERTICAL_BAR), CursorStyle.BLOCK)).isEqualTo(CursorStyle.BEAM);
        assertThat(CursorRequest.effective(JediCellReader.cursor(CursorShape.BLINK_BLOCK), CursorStyle.BEAM)).isEqualTo(CursorStyle.BLOCK);
        assertThat(CursorRequest.effective(JediCellReader.cursor(CursorShape.STEADY_UNDERLINE), CursorStyle.BLOCK)).isEqualTo(CursorStyle.UNDERLINE);
    }

    @Test
    void applicationRequestsDecideBlinking() {
        assertThat(CursorRequest.effectiveBlink(JediCellReader.cursor(CursorShape.BLINK_VERTICAL_BAR), false)).isTrue();
        assertThat(CursorRequest.effectiveBlink(JediCellReader.cursor(CursorShape.STEADY_BLOCK), true)).isFalse();
    }
}
