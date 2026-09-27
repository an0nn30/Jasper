package dev.jasper.app.workspace;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalTitleTest {
    @Test void automaticTabsShowOnlyTheCommandOrShell() {
        assertThat(TerminalTitle.tab("", "sleep 12")).isEqualTo("sleep 12");
        assertThat(TerminalTitle.tab(null, "zsh")).isEqualTo("zsh");
        assertThat(TerminalTitle.tab("", "")).isEqualTo("Terminal");
    }
    @Test void programTitleOverridesTheEntireAutomaticTitle() {
        assertThat(TerminalTitle.tab("Editing README.md", "vim")).isEqualTo("Editing README.md");
        assertThat(TerminalTitle.tab("<html>literal\nsecond line", "worker"))
            .isEqualTo("<html>literal ↵ second line");
    }
}
