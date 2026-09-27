package dev.jasper.terminal.session;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.*;

class SessionLaunchOptionsEnvironmentTest {
    @Test @EnabledOnOs(OS.WINDOWS)
    void acceptsWindowsHiddenEnvironmentVariables() {
        var options = options(Map.of("=C:", "C:\\Users", "=ExitCode", "0", "Path", "C:\\Windows"));
        assertThat(options.environment()).containsEntry("=C:", "C:\\Users")
            .containsEntry("=ExitCode", "0").containsEntry("Path", "C:\\Windows");
    }

    @Test void rejectsMalformedEnvironmentEntries() {
        for (String key : List.of("", "A=B", "=C:=extra", "A\0B")) {
            assertThatIllegalArgumentException().isThrownBy(() -> options(Map.of(key, "value")));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> options(Map.of("NAME", "a\0b")));
    }

    private static SessionLaunchOptions options(Map<String, String> environment) {
        return SessionLaunchOptions.builder().command(List.of("powershell.exe"))
            .environment(environment).workingDirectory(Path.of(System.getProperty("user.home"))).build();
    }
}
