package dev.jasper.app;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalTitleTest {
    @Test void idleTitleUsesDirectoryAndLoginShellLikeITerm() {
        assertThat(TerminalTitle.tab("", Path.of(System.getProperty("user.home")), "-zsh")).isEqualTo("~ (-zsh)");
        assertThat(TerminalTitle.tab(null, Path.of("/projects/jasper"), "zsh")).isEqualTo("jasper (zsh)");
        assertThat(TerminalTitle.tab("", Path.of("/"), "bash")).isEqualTo("/ (bash)");
    }

    @Test void jobAndProgramTitleRemainIndependentAndOpaque() {
        assertThat(TerminalTitle.tab("Editing README.md", Path.of("/projects/jasper"), "vim"))
            .isEqualTo("Editing README.md (vim)");
        assertThat(TerminalTitle.tab("~", Path.of("/projects/jasper"), "tmux")).isEqualTo("~ (tmux)");
        assertThat(TerminalTitle.tab("<html>literal\nsecond line", null, ""))
            .isEqualTo("<html>literal ↵ second line");
    }
}
