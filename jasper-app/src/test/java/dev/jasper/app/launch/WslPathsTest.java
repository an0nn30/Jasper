package dev.jasper.app.launch;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WslPathsTest {
    @Test void driveMountsBecomeDriveLettersAndEverythingElseTheDistributionsShare() {
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/c/Users/me")).map(Path::toString).contains("C:\\Users\\me");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/d")).map(Path::toString).contains("D:\\");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/C/Users/")).map(Path::toString).contains("C:\\Users");
        assertThat(WslPaths.toWindows("Ubuntu", "/home/me/My Dir")).map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\home\\me\\My Dir");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/wsl/shared")).as("not a drive letter")
            .map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\mnt\\wsl\\shared");
        assertThat(WslPaths.toWindows("Ubuntu", "/mnt/cdrom")).map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\mnt\\cdrom");
        assertThat(WslPaths.toWindows("Ubuntu", "/")).map(Path::toString).contains("\\\\wsl.localhost\\Ubuntu\\");
        assertThat(WslPaths.toWindows("Ubuntu", "relative/path")).isEmpty();
        assertThat(WslPaths.toWindows("Ubuntu", "")).isEmpty();
    }

    @Test void onlyThisDistributionsShareMapsBackToALinuxPath() {
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me"))).contains("/home/me");
        assertThat(WslPaths.toLinux("ubuntu", Path.of("\\\\WSL.LOCALHOST\\Ubuntu\\home\\me"))).as("names compare without regard to case")
            .contains("/home/me");
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl$\\Ubuntu\\srv\\app"))).contains("/srv/app");
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl.localhost\\Ubuntu\\"))).contains("/");
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("\\\\wsl.localhost\\Debian\\home\\me"))).isEmpty();
        assertThat(WslPaths.toLinux("Ubuntu", Path.of("C:\\Users\\me"))).as("a Windows path goes to --cd as it is").isEmpty();
        assertThat(WslPaths.toLinux("Ubuntu", WslPaths.toWindows("Ubuntu", "/home/me").orElseThrow())).contains("/home/me");
    }

    @Test void anyDistributionsShareIsRecognised() {
        assertThat(WslPaths.distroOf(Path.of("\\\\wsl.localhost\\Debian\\home"))).contains("Debian");
        assertThat(WslPaths.distroOf(Path.of("\\\\wsl$\\Ubuntu\\"))).contains("Ubuntu");
        assertThat(WslPaths.distroOf(Path.of("\\\\server\\share\\x"))).isEmpty();
        assertThat(WslPaths.distroOf(Path.of("C:\\Users"))).isEmpty();
    }
}
