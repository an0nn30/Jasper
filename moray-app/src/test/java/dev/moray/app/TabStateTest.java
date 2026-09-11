package dev.moray.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TabStateTest {
    @Test
    void automaticTitlePrefersShellTitleThenDirectoryThenFallback() {
        TabState tab = new TabState();

        assertThat(tab.title("editor", Path.of("/work/moray"))).isEqualTo("editor");
        assertThat(tab.title("  ", Path.of("/work/moray"))).isEqualTo("moray");
        assertThat(tab.title(null, Path.of("/"))).isEqualTo("Terminal");
        assertThat(tab.title(null, null)).isEqualTo("Terminal");
    }

    @Test
    void userRenameSurvivesFocusedPaneChangesUntilCleared() {
        TabState tab = new TabState();
        tab.rename("Build logs");

        assertThat(tab.title("shell title", Path.of("/different/path"))).isEqualTo("Build logs");

        tab.rename(" \t ");
        assertThat(tab.title("shell title", Path.of("/different/path"))).isEqualTo("shell title");
        tab.rename("Pinned");
        tab.rename(null);
        assertThat(tab.title(null, Path.of("/different/path"))).isEqualTo("path");
    }
}
