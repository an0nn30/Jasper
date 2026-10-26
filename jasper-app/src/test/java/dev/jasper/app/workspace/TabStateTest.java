package dev.jasper.app.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TabStateTest {
    @Test
    void theNameTheUserGaveIsKeptApartFromTheAutomaticTitle() {
        TabState tab = new TabState();
        assertThat(tab.renamed()).isEmpty();
        tab.rename("build");
        assertThat(tab.renamed()).contains("build");
        tab.rename(" ");
        assertThat(tab.renamed()).isEmpty();
    }
}
