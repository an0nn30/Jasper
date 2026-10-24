package dev.jasper.app.platform;

import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WindowsGlassTest {
    @Test void onlyExplicitExperimentModesOptIn() {
        assertThat(WindowGlass.prototypeMode("dwm")).isTrue();
        assertThat(WindowGlass.prototypeMode("layered")).isTrue();
        for (String mode : new String[] { "", "true", "off", "DWM", "typo" })
            assertThat(WindowGlass.prototypeMode(mode)).as(mode).isFalse();
    }

    @Test void noWindowNeverLoadsWindowsLibraries() {
        var root = new JRootPane();
        assertThat(WindowsGlass.translucent(root)).isFalse();
        for (var blur : WindowGlass.Blur.values()) assertThat(WindowsGlass.blur(root, blur)).isFalse();
        WindowsGlass.opaque(root);
        WindowsGlass.watch(root, () -> { throw new AssertionError("No window to watch"); }).run();
    }
}
