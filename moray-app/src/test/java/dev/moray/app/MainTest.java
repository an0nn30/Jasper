package dev.moray.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void windowTitleFallsBackToMorayWhenTheShellHasNoTitle() {
        assertThat(Main.windowTitle(null)).isEqualTo("Moray");
        assertThat(Main.windowTitle(" ")).isEqualTo("Moray");
    }

    @Test
    void windowTitleUsesTheShellTitle() {
        assertThat(Main.windowTitle("vim README.md")).isEqualTo("vim README.md");
    }
}
