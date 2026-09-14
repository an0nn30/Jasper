package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void windowTitleFallsBackToJasperWhenTheShellHasNoTitle() {
        assertThat(Main.windowTitle(null)).isEqualTo("Jasper");
        assertThat(Main.windowTitle(" ")).isEqualTo("Jasper");
    }

    @Test
    void windowTitleUsesTheShellTitle() {
        assertThat(Main.windowTitle("vim README.md")).isEqualTo("vim README.md");
    }
}
