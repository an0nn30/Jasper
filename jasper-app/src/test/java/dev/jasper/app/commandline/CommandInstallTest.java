package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class CommandInstallTest {
    @Test void theCommandIsFoundInsideEachAppImage() {
        assertThat(CommandInstall.clientIn("Mac OS X", "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .contains(Path.of("/Applications/Jasper.app/Contents/Resources/bin/jasper"));
        assertThat(CommandInstall.clientIn("Linux", "/opt/jasper/bin/Jasper")).contains(Path.of("/opt/jasper/lib/bin/jasper"));
        assertThat(CommandInstall.clientIn("Mac OS X", null)).isEmpty();
        assertThat(CommandInstall.clientIn("Linux", "  ")).isEmpty();
    }

    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsTheCommandIsInTheImagesBinFolder() {
        assertThat(CommandInstall.clientIn("Windows 11", "C:\\Program Files\\Jasper\\Jasper.exe"))
            .contains(Path.of("C:\\Program Files\\Jasper\\bin\\jasper.exe"));
    }

    @Test void onlyLinuxHintsAtPathBecauseAFinderLaunchedJasperHasNoUsefulPath(@TempDir Path home) {
        String launchdPath = String.join(java.io.File.pathSeparator, "/usr/bin", "/bin", "/usr/sbin", "/sbin");
        assertThat(CommandInstall.forThisPlatform("Mac OS X", "/Applications/Jasper.app/Contents/MacOS/Jasper", home).hint(launchdPath))
            .isEmpty();
        assertThat(CommandInstall.forThisPlatform("Linux", "/opt/jasper/bin/Jasper", home).hint(launchdPath))
            .contains(home.resolve(".local").resolve("bin") + " is not on your PATH; add it to use jasper in other terminals");
    }

    @Test void aDevelopmentRunCannotInstall(@TempDir Path home) {
        CommandInstall install = CommandInstall.forThisPlatform(System.getProperty("os.name"), null, home);
        assertThat(install.available()).isFalse();
        assertThat(install.state().kind()).isEqualTo(CommandInstall.Kind.UNAVAILABLE);
    }

    @Test void theClientIsTheImagesCommandElseTheBuildsElseNone() {
        String built = Path.of("/src/jasper-app/build/cli/bin/jasper").toString();
        assertThat(CommandInstall.client("Mac OS X", "/Applications/Jasper Beta.app/Contents/MacOS/Jasper", built))
            .as("an installed image's own command wins").contains(Path.of("/Applications/Jasper Beta.app/Contents/Resources/bin/jasper"));
        assertThat(CommandInstall.client("Linux", "/opt/jasper/bin/Jasper", null)).contains(Path.of("/opt/jasper/lib/bin/jasper"));
        assertThat(CommandInstall.client("Mac OS X", null, built)).as("a development run's").contains(Path.of(built));
        assertThat(CommandInstall.client("Linux", "", " ")).isEmpty();
        assertThat(CommandInstall.client("Linux", null, null)).isEmpty();
    }
}
