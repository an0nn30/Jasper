package dev.jasper.remote.projects;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellTextTest {
    @Test void quotesEverythingAndKeepsOnlyStarsLiveInGlobs() {
        assertThat(ShellText.quote("plain")).isEqualTo("'plain'");
        assertThat(ShellText.quote("it's $(x)")).isEqualTo("'it'\\''s $(x)'");
        assertThat(ShellText.glob("*.sln")).isEqualTo("''*'.sln'");
        assertThat(ShellText.glob("a b*c'd")).isEqualTo("'a b'*'c'\\''d'");
    }
}
