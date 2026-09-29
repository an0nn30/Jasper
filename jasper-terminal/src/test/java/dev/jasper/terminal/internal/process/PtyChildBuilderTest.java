package dev.jasper.terminal.internal.process;

import com.pty4j.PtyProcessBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PtyChildBuilderTest {
    private static final Path HOME = Path.of(System.getProperty("user.home"));

    /** pty4j offers no getter; the field name was read from the 0.13.10 jar with javap -p. */
    private static boolean conPty(PtyProcessBuilder builder) throws ReflectiveOperationException {
        var field = PtyProcessBuilder.class.getDeclaredField("myUseWinConPty");
        field.setAccessible(true);
        return field.getBoolean(builder);
    }

    @Test void windowsSessionsRunOnConPtyAndOtherPlatformsAreUnchanged() throws Exception {
        assertThat(conPty(PtyChild.builder(List.of("powershell.exe", "-NoLogo"), Map.of(), HOME, 80, 24, "Windows 11"))).isTrue();
        assertThat(conPty(PtyChild.builder(List.of("/bin/zsh", "-l"), Map.of(), HOME, 80, 24, "Mac OS X"))).isFalse();
        assertThat(conPty(PtyChild.builder(List.of("/bin/bash", "-l"), Map.of(), HOME, 80, 24, "Linux"))).isFalse();
    }
}
