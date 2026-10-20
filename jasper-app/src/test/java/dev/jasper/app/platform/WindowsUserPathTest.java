package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class WindowsUserPathTest {
    @Test @EnabledOnOs(OS.WINDOWS) void aValueIsWrittenUnexpandedAndReadBack() throws IOException {
        String key = "Software\\Jasper-test-" + UUID.randomUUID();
        try {
            assertThat(WindowsUserPath.read(key, "Path")).isEmpty();
            WindowsUserPath.write(key, "Path", "%USERPROFILE%\\bin;C:\\J\u00fcrgen\\bin");
            assertThat(WindowsUserPath.read(key, "Path")).contains("%USERPROFILE%\\bin;C:\\J\u00fcrgen\\bin");
        } finally {
            WindowsUserPath.delete(key);
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void theUsersOwnPathCanBeRead() {
        assertThatCode(WindowsUserPath::read).doesNotThrowAnyException();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void elsewhereThereIsNoUserPath() {
        assertThatThrownBy(WindowsUserPath::read).isInstanceOf(IOException.class);
    }
}
