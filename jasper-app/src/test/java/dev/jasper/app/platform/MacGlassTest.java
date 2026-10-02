package dev.jasper.app.platform;

import java.nio.file.Path;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MacGlassTest {
    @Test void anExplicitLibraryWins() {
        assertThat(MacGlass.library("/tmp/libjasper-glass.dylib", "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/tmp/libjasper-glass.dylib"));
    }

    @Test void aPackagedAppLoadsItFromContentsApp() {
        assertThat(MacGlass.library(null, "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper.app/Contents/app/libjasper-glass.dylib"));
    }

    @Test void anUnpackagedJvmHasNoLibrary() {
        assertThat(MacGlass.library(null, null)).isNull();
        assertThat(MacGlass.library(" ", "/usr/bin/java")).isNull();
    }

    @Test void headlessRuntimesAreUnsupported() {
        assertThat(WindowGlass.Platform.current().supported()).isFalse();
    }

    @Test void aRootPaneWithoutAWindowIsNeverMadeTranslucent() {
        assertThat(MacGlass.translucent(new JRootPane())).isFalse();
        assertThat(MacGlass.blur(new JRootPane(), 20)).isFalse();
    }
}
