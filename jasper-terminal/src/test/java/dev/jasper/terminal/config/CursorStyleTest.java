package dev.jasper.terminal.config;

import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.text.CursorRequest;

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
        assertThat(CursorRequest.effective(EmulationFixture.cursor(CursorShape.STEADY_VERTICAL_BAR), CursorStyle.BLOCK)).isEqualTo(CursorStyle.BEAM);
        assertThat(CursorRequest.effective(EmulationFixture.cursor(CursorShape.BLINK_BLOCK), CursorStyle.BEAM)).isEqualTo(CursorStyle.BLOCK);
        assertThat(CursorRequest.effective(EmulationFixture.cursor(CursorShape.STEADY_UNDERLINE), CursorStyle.BLOCK)).isEqualTo(CursorStyle.UNDERLINE);
    }

    @Test
    void applicationRequestsDecideBlinking() {
        assertThat(CursorRequest.effectiveBlink(EmulationFixture.cursor(CursorShape.BLINK_VERTICAL_BAR), false)).isTrue();
        assertThat(CursorRequest.effectiveBlink(EmulationFixture.cursor(CursorShape.STEADY_BLOCK), true)).isFalse();
    }
}
