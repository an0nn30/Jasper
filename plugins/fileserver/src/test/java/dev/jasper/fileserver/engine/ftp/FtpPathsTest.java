package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.share.PathRefusedException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FtpPathsTest {
    @Test void resolvesAgainstTheWorkingDirectory() throws Exception {
        assertThat(FtpPaths.absolute("/", "docs")).isEqualTo("/docs");
        assertThat(FtpPaths.absolute("/docs", "a.txt")).isEqualTo("/docs/a.txt");
        assertThat(FtpPaths.absolute("/docs", "/x")).isEqualTo("/x");
        assertThat(FtpPaths.absolute("/docs", "")).isEqualTo("/docs");
        assertThat(FtpPaths.absolute("/docs", null)).isEqualTo("/docs");
        assertThat(FtpPaths.absolute("/docs", "./a//b/")).isEqualTo("/docs/a/b");
        assertThat(FtpPaths.absolute("/", "a\\b")).isEqualTo("/a/b");
        assertThat(FtpPaths.absolute("/docs", "~")).isEqualTo("/");
        assertThat(FtpPaths.absolute("/docs", "~/x")).isEqualTo("/x");
        assertThat(FtpPaths.absolute("/docs", "~x")).isEqualTo("/docs/~x");
    }

    @Test void dotDotStopsAtTheRoot() throws Exception {
        assertThat(FtpPaths.absolute("/docs", "..")).isEqualTo("/");
        assertThat(FtpPaths.absolute("/", "../../etc/passwd")).isEqualTo("/etc/passwd");
        assertThat(FtpPaths.absolute("/docs/a", "../../..")).isEqualTo("/");
    }

    @Test void refusesNamesNoPlatformCanShare() {
        assertThatThrownBy(() -> FtpPaths.absolute("/", "CON")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> FtpPaths.absolute("/", "a:b")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> FtpPaths.absolute("/", "bad" + (char) 1 + "name")).isInstanceOf(PathRefusedException.class);
    }

    @Test void namesAndRelativePaths() {
        assertThat(FtpPaths.name("/")).isEqualTo("/");
        assertThat(FtpPaths.name("/docs/a.txt")).isEqualTo("a.txt");
        assertThat(FtpPaths.relative("/")).isEmpty();
        assertThat(FtpPaths.relative("/docs/a.txt")).isEqualTo("docs/a.txt");
    }
}
