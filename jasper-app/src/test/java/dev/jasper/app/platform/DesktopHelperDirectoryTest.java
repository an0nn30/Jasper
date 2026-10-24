package dev.jasper.app.platform;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class DesktopHelperDirectoryTest {
    @TempDir Path home;
    @Test void explicitAbsoluteDirectoryOverridesInstalledSibling() throws Exception {
        Path explicit = Files.createDirectory(home.resolve("explicit"));
        Path installed = Files.createDirectory(home.resolve("desktop-colors"));
        assertThat(DesktopColors.helperDirectory("Linux", explicit.toString(), home.resolve("jasper-app.jar"))).contains(explicit);
        assertThat(DesktopColors.helperDirectory("Linux", null, home.resolve("jasper-app.jar"))).contains(installed);
    }
    @Test void invalidExplicitPathDoesNotSearchOtherLocationsOrWorkingDirectory() throws Exception {
        Files.createDirectory(home.resolve("desktop-colors"));
        for (String explicit : new String[] { "relative/helpers", home.resolve("missing").toString(), "bad\0path" })
            assertThat(DesktopColors.helperDirectory("Linux", explicit, home.resolve("jasper-app.jar"))).isEmpty();
        assertThat(DesktopColors.helperDirectory("Linux", null, home.resolve("classes"))).isEmpty();
        assertThat(DesktopColors.helperDirectory("Linux", null, null)).isEmpty();
    }
    @Test void otherPlatformsNeverUseLinuxHelpers() throws Exception {
        Path explicit = Files.createDirectory(home.resolve("explicit"));
        assertThat(DesktopColors.helperDirectory("Mac OS X", explicit.toString(), null)).isEmpty();
        assertThat(DesktopColors.helperDirectory("Windows 11", explicit.toString(), null)).isEmpty();
    }
}
