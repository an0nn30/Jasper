package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.share.PathRefusedException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientPathsTest {
    @Test void resolvesAgainstTheWorkingDirectory() throws Exception {
        assertThat(ClientPaths.absolute("/", "docs")).isEqualTo("/docs");
        assertThat(ClientPaths.absolute("/docs", "a.txt")).isEqualTo("/docs/a.txt");
        assertThat(ClientPaths.absolute("/docs", "/x")).isEqualTo("/x");
        assertThat(ClientPaths.absolute("/docs", "")).isEqualTo("/docs");
        assertThat(ClientPaths.absolute("/docs", null)).isEqualTo("/docs");
        assertThat(ClientPaths.absolute("/docs", "./a//b/")).isEqualTo("/docs/a/b");
        assertThat(ClientPaths.absolute("/", "a\\b")).isEqualTo("/a/b");
        assertThat(ClientPaths.absolute("/docs", "~")).isEqualTo("/");
        assertThat(ClientPaths.absolute("/docs", "~/x")).isEqualTo("/x");
        assertThat(ClientPaths.absolute("/docs", "~x")).isEqualTo("/docs/~x");
    }

    @Test void dotDotStopsAtTheRoot() throws Exception {
        assertThat(ClientPaths.absolute("/docs", "..")).isEqualTo("/");
        assertThat(ClientPaths.absolute("/", "../../etc/passwd")).isEqualTo("/etc/passwd");
        assertThat(ClientPaths.absolute("/docs/a", "../../..")).isEqualTo("/");
    }

    @Test void refusesNamesNoPlatformCanShare() {
        assertThatThrownBy(() -> ClientPaths.absolute("/", "CON")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> ClientPaths.absolute("/", "a:b")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> ClientPaths.absolute("/", "bad" + (char) 1 + "name")).isInstanceOf(PathRefusedException.class);
    }

    @Test void namesAndRelativePaths() {
        assertThat(ClientPaths.name("/")).isEqualTo("/");
        assertThat(ClientPaths.name("/docs/a.txt")).isEqualTo("a.txt");
        assertThat(ClientPaths.relative("/")).isEmpty();
        assertThat(ClientPaths.relative("/docs/a.txt")).isEqualTo("docs/a.txt");
    }
}
