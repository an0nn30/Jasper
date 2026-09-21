package dev.jasper.app.workspace;

import dev.jasper.app.workspace.TerminalTitle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void windowTitleFallsBackToJasperWhenTheShellHasNoTitle() {
        assertThat(TerminalTitle.windowTitle(null)).isEqualTo("Jasper");
        assertThat(TerminalTitle.windowTitle(" ")).isEqualTo("Jasper");
    }

    @Test
    void windowTitleUsesTheShellTitle() {
        assertThat(TerminalTitle.windowTitle("vim README.md")).isEqualTo("vim README.md");
    }
}
